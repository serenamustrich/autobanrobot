const QUEUE_KEY = 'pendingBlockQueue';
const QUEUE_ALARM = 'autobanrobot-process-queue';
const MAX_ATTEMPTS = 3;
const MAX_BLOCK_HISTORY = 500;
const BLOCK_INTERVAL_MS = 500;
const UPLOAD_QUEUE_KEY = 'pendingBanUploadQueue';
const UPLOAD_ALARM = 'autobanrobot-upload-ban-events';
const UPLOAD_ENDPOINT = 'https://ban.richccy.com/api/bans';
const UPLOAD_RETRY_BASE_MS = 15_000;
const UPLOAD_RETRY_MAX_MS = 5 * 60_000;
const UPDATE_ALARM = 'autobanrobot-check-github-release';
const HEARTBEAT_ALARM = 'autobanrobot-plugin-heartbeat';
const HEARTBEAT_ENDPOINT = 'https://ban.richccy.com/api/clients/heartbeat';
const RULES_ENDPOINT = 'https://ban.richccy.com/api/rules';
const RULES_ALARM = 'autobanrobot-refresh-rules';
const INSTALLATION_ID_KEY = 'anonymousInstallationId';
const LATEST_RELEASE_API =
  'https://api.github.com/repos/serenamustrich/autobanrobot/releases/latest';

let bearer = null;
let csrf = null;
let queueProcessing = false;
let queueOperation = Promise.resolve();
let queueScheduleId = 0;
let uploadProcessing = false;
let uploadScheduleId = 0;

const authReady = extensionAPI.storage.session.get(['bearer', 'csrf']).then(result => {
  bearer = result.bearer ?? null;
  csrf = result.csrf ?? null;
});

function withQueueLock(operation) {
  const result = queueOperation.then(operation, operation);
  queueOperation = result.catch(() => {});
  return result;
}

async function initializeKeywords() {
  const stored = await extensionAPI.storage.local.get(['keywords']);
  if (Array.isArray(stored.keywords)) return;
  const response = await fetch(extensionAPI.runtime.getURL('default-keywords.json'));
  await extensionAPI.storage.local.set({ keywords: await response.json() });
}

async function initializeSettings() {
  const stored = await extensionAPI.storage.local.get([
    'emojiEnglishEmojiEnabled',
    'singleEmojiEnabled',
    'structuredEmojiTimeEnabled',
    'structuredThreeSegmentEnabled'
  ]);
  const defaults = {};
  if (typeof stored.emojiEnglishEmojiEnabled !== 'boolean') {
    defaults.emojiEnglishEmojiEnabled = true;
  }
  if (typeof stored.singleEmojiEnabled !== 'boolean') {
    defaults.singleEmojiEnabled = true;
  }
  if (typeof stored.structuredEmojiTimeEnabled !== 'boolean') {
    defaults.structuredEmojiTimeEnabled = true;
  }
  if (typeof stored.structuredThreeSegmentEnabled !== 'boolean') {
    defaults.structuredThreeSegmentEnabled = true;
  }
  if (Object.keys(defaults).length) {
    await extensionAPI.storage.local.set(defaults);
  }
}

async function initializeRules() {
  const stored = await extensionAPI.storage.local.get(['remoteRuleConfig']);
  if (stored.remoteRuleConfig?.rules) return;
  const response = await fetch(extensionAPI.runtime.getURL('default-rules.json'));
  await extensionAPI.storage.local.set({ remoteRuleConfig: await response.json() });
}

function isValidRuleConfig(config) {
  return Number.isSafeInteger(config?.version) &&
    Array.isArray(config.rules) && config.rules.length <= 100 &&
    config.rules.every(rule =>
      typeof rule?.id === 'string' && rule.id.length <= 64 &&
      typeof rule?.name === 'string' && rule.name.length <= 120 &&
      typeof rule?.pattern === 'string' && rule.pattern.length <= 2000 &&
      ['raw', 'compact', 'noSymbols'].includes(rule.normalization ?? 'raw') &&
      typeof rule?.flags === 'string' && /^[gimsuy]*$/.test(rule.flags)
    );
}

async function refreshRules() {
  const response = await fetch(RULES_ENDPOINT, {
    headers: { accept: 'application/json', 'x-autoban-client': 'browser-extension' },
    cache: 'no-store'
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const config = await response.json();
  if (!isValidRuleConfig(config)) throw new Error('Invalid rule configuration');
  config.checkedAt = new Date().toISOString();
  await extensionAPI.storage.local.set({ remoteRuleConfig: config });
  return config;
}

function scheduleQueue(delayMs = 0) {
  const delay = Math.max(delayMs, 50);
  const scheduleId = ++queueScheduleId;
  extensionAPI.alarms.create(QUEUE_ALARM, { when: Date.now() + delay });
  setTimeout(() => {
    if (scheduleId !== queueScheduleId) return;
    queueScheduleId++;
    extensionAPI.alarms.clear(QUEUE_ALARM);
    withQueueLock(processQueue);
  }, delay);
}

function scheduleUpload(delayMs = 0) {
  const delay = Math.max(delayMs, 100);
  const scheduleId = ++uploadScheduleId;
  extensionAPI.alarms.create(UPLOAD_ALARM, { when: Date.now() + delay });
  setTimeout(() => {
    if (scheduleId !== uploadScheduleId) return;
    uploadScheduleId++;
    extensionAPI.alarms.clear(UPLOAD_ALARM);
    processUploadQueue();
  }, delay);
}

function scheduleUpdateChecks() {
  extensionAPI.alarms.create(UPDATE_ALARM, {
    delayInMinutes: 1,
    periodInMinutes: 12 * 60
  });
}

function scheduleHeartbeat() {
  extensionAPI.alarms.create(HEARTBEAT_ALARM, {
    delayInMinutes: 1,
    periodInMinutes: 1
  });
}

function scheduleRuleRefresh() {
  extensionAPI.alarms.create(RULES_ALARM, {
    delayInMinutes: 1,
    periodInMinutes: 5
  });
}

async function getInstallationId() {
  const stored = await extensionAPI.storage.local.get([INSTALLATION_ID_KEY]);
  if (typeof stored[INSTALLATION_ID_KEY] === 'string' &&
      stored[INSTALLATION_ID_KEY]) {
    return stored[INSTALLATION_ID_KEY];
  }
  const installationId = crypto.randomUUID();
  await extensionAPI.storage.local.set({
    [INSTALLATION_ID_KEY]: installationId
  });
  return installationId;
}

async function sendHeartbeat() {
  const installationId = await getInstallationId();
  const response = await fetch(HEARTBEAT_ENDPOINT, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-autoban-client': 'browser-extension'
    },
    body: JSON.stringify({
      installationId,
      platform: 'safari',
      version: extensionAPI.runtime.getManifest().version
    })
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  processUploadQueue();
}

function compareVersions(left, right) {
  const a = left.split('.').map(value => Number.parseInt(value, 10) || 0);
  const b = right.split('.').map(value => Number.parseInt(value, 10) || 0);
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index++) {
    if ((a[index] ?? 0) !== (b[index] ?? 0)) {
      return (a[index] ?? 0) > (b[index] ?? 0) ? 1 : -1;
    }
  }
  return 0;
}

async function checkForUpdate() {
  const currentVersion = extensionAPI.runtime.getManifest().version;
  try {
    const response = await fetch(LATEST_RELEASE_API, {
      headers: { accept: 'application/vnd.github+json' }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const release = await response.json();
    const latestVersion = String(release.tag_name ?? '').replace(/^v/i, '');
    const updateInfo = {
      checkedAt: new Date().toISOString(),
      currentVersion,
      latestVersion,
      available:
        Boolean(latestVersion) &&
        compareVersions(latestVersion, currentVersion) > 0,
      releaseUrl: release.html_url ?? ''
    };
    await extensionAPI.storage.local.set({ updateInfo });
    return updateInfo;
  } catch (error) {
    const updateInfo = {
      checkedAt: new Date().toISOString(),
      currentVersion,
      error: error.message
    };
    await extensionAPI.storage.local.set({ updateInfo });
    return updateInfo;
  }
}

extensionAPI.runtime.onInstalled.addListener(() => {
  Promise.all([initializeKeywords(), initializeSettings(), initializeRules()]).catch(error => {
    console.error('Failed to initialize extension settings:', error);
  });
  scheduleQueue();
  scheduleUpload();
  scheduleUpdateChecks();
  checkForUpdate();
  scheduleHeartbeat();
  sendHeartbeat().catch(() => {});
  scheduleRuleRefresh();
  refreshRules().catch(() => {});
});

extensionAPI.runtime.onStartup.addListener(() => {
  scheduleQueue();
  scheduleUpload();
  scheduleUpdateChecks();
  checkForUpdate();
  scheduleHeartbeat();
  sendHeartbeat().catch(() => {});
  scheduleRuleRefresh();
  refreshRules().catch(() => {});
});

extensionAPI.webRequest.onBeforeSendHeaders.addListener(
  details => {
    const auth = details.requestHeaders?.find(
      header =>
        header.name.toLowerCase() === 'authorization' &&
        header.value?.startsWith('Bearer ')
    );
    const csrfHeader = details.requestHeaders?.find(
      header => header.name.toLowerCase() === 'x-csrf-token' && header.value
    );
    let authChanged = false;
    if (auth && auth.value !== bearer) {
      bearer = auth.value;
      authChanged = true;
    }
    if (csrfHeader && csrfHeader.value !== csrf) {
      csrf = csrfHeader.value;
      authChanged = true;
    }
    if (authChanged) {
      extensionAPI.storage.session.set({ bearer, csrf });
      scheduleQueue();
    }
  },
  { urls: ['https://twitter.com/i/api/*', 'https://x.com/i/api/*'] },
  ['requestHeaders']
);

function apiHeaders() {
  return {
    'authorization': bearer,
    'x-csrf-token': csrf,
    'content-type': 'application/x-www-form-urlencoded',
    'x-twitter-active-user': 'yes',
    'x-twitter-auth-type': 'OAuth2Session'
  };
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function confirmsBlock(payload, username) {
  if (!payload || payload.errors?.length) return false;
  const sameUser =
    typeof payload.screen_name !== 'string' ||
    payload.screen_name.toLocaleLowerCase() === username.toLocaleLowerCase();
  return sameUser && (
    payload.blocking === true ||
    payload.relationship?.source?.blocking === true
  );
}

async function fetchRelationship(job) {
  try {
    const query = new URLSearchParams({ target_screen_name: job.username });
    const response = await fetch(
      `https://${job.hostname}/i/api/1.1/friendships/show.json?${query}`,
      { method: 'GET', headers: apiHeaders(), credentials: 'include' }
    );
    const payload = parseJson(await response.text());
    return { ok: response.ok, status: response.status, payload };
  } catch (error) {
    return { ok: false, status: 0, payload: null, error: error.message };
  }
}

async function attemptBlock(job) {
  const relationship = await fetchRelationship(job);
  const source = relationship.payload?.relationship?.source;
  if (!relationship.ok || !source) {
    return {
      state: relationship.status === 408 || relationship.status === 425 ||
        relationship.status === 429 || relationship.status >= 500 ||
        relationship.status === 0 ? 'retry' : 'failed',
      message: relationship.status
        ? `无法确认关注关系（HTTP ${relationship.status}）`
        : '无法确认关注关系'
    };
  }

  if (source.following === true) {
    return { state: 'skipped', message: '你正在关注该账号' };
  }
  if (source.blocking === true) {
    return { state: 'already-blocked', message: '该账号已经处于屏蔽状态' };
  }

  try {
    const response = await fetch(
      `https://${job.hostname}/i/api/1.1/blocks/create.json`,
      {
        method: 'POST',
        headers: apiHeaders(),
        body: `screen_name=${encodeURIComponent(job.username)}`,
        credentials: 'include'
      }
    );
    const payload = parseJson(await response.text());
    if (!response.ok) {
      const retryable =
        response.status === 408 || response.status === 425 ||
        response.status === 429 || response.status >= 500;
      return {
        state: retryable ? 'retry' : 'failed',
        message: `HTTP ${response.status}`
      };
    }

    if (confirmsBlock(payload, job.username)) {
      return { state: 'success' };
    }

    const verification = await fetchRelationship(job);
    if (verification.ok && confirmsBlock(verification.payload, job.username)) {
      return { state: 'success' };
    }
    return { state: 'retry', message: 'API 未确认屏蔽成功' };
  } catch (error) {
    return { state: 'retry', message: `异常: ${error.message}` };
  }
}

async function broadcastResult(job, state, message = '') {
  if (!job.sourceTabId) return;
  await extensionAPI.tabs.sendMessage(job.sourceTabId, {
      type: 'BLOCK_RESULT',
      result: { ...job, state, message }
  }).catch(() => {});
}

async function recordSuccess(job) {
  const stored = await extensionAPI.storage.local.get([
    'blockCount',
    'blockHistory',
    'keywords'
  ]);
  const history = Array.isArray(stored.blockHistory) ? stored.blockHistory : [];
  const record = {
    clientEventId: crypto.randomUUID(),
    username: job.username,
    displayName: job.displayName,
    reason: job.reason,
    matchedKeywords: Array.isArray(job.matchedKeywords)
      ? job.matchedKeywords
      : [],
    configuredKeywords: Array.isArray(stored.keywords) ? stored.keywords : [],
    content: job.content,
    pageUrl: job.pageUrl,
    blockedAt: new Date().toISOString()
  };
  await extensionAPI.storage.local.set({
    blockCount: (stored.blockCount ?? 0) + 1,
    blockHistory: [
      record,
      ...history.filter(item => item.username !== job.username)
    ].slice(0, MAX_BLOCK_HISTORY)
  });
  await enqueueBanUpload(record);
}

async function enqueueBanUpload(record) {
  const stored = await extensionAPI.storage.local.get([UPLOAD_QUEUE_KEY]);
  const queue = Array.isArray(stored[UPLOAD_QUEUE_KEY])
    ? stored[UPLOAD_QUEUE_KEY]
    : [];
  if (!queue.some(item => item.clientEventId === record.clientEventId)) {
    queue.push({ ...record, uploadAttempts: 0 });
    await extensionAPI.storage.local.set({ [UPLOAD_QUEUE_KEY]: queue });
  }
  scheduleUpload();
}

async function processUploadQueue() {
  if (uploadProcessing) return;
  uploadProcessing = true;
  try {
    const stored = await extensionAPI.storage.local.get([UPLOAD_QUEUE_KEY]);
    const queue = Array.isArray(stored[UPLOAD_QUEUE_KEY])
      ? stored[UPLOAD_QUEUE_KEY]
      : [];
    if (!queue.length) return;

    const record = queue[0];
    try {
      const response = await fetch(UPLOAD_ENDPOINT, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-autoban-client': 'browser-extension'
        },
        body: JSON.stringify({
          clientEventId: record.clientEventId,
          username: record.username,
          displayName: record.displayName,
          reason: record.reason,
          matchedKeywords: record.matchedKeywords,
          configuredKeywords: record.configuredKeywords,
          content: record.content,
          pageUrl: record.pageUrl,
          blockedAt: record.blockedAt
        })
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      queue.shift();
      await extensionAPI.storage.local.set({ [UPLOAD_QUEUE_KEY]: queue });
      if (queue.length) scheduleUpload(250);
    } catch {
      record.uploadAttempts = (record.uploadAttempts ?? 0) + 1;
      await extensionAPI.storage.local.set({ [UPLOAD_QUEUE_KEY]: queue });
      const retryDelay = Math.min(
        UPLOAD_RETRY_BASE_MS * 2 ** Math.min(record.uploadAttempts - 1, 5),
        UPLOAD_RETRY_MAX_MS
      );
      scheduleUpload(retryDelay);
    }
  } finally {
    uploadProcessing = false;
  }
}

async function processQueue() {
  await authReady;
  if (queueProcessing) return;
  queueProcessing = true;

  try {
    const stored = await extensionAPI.storage.local.get([QUEUE_KEY]);
    const queue = Array.isArray(stored[QUEUE_KEY]) ? stored[QUEUE_KEY] : [];
    if (!queue.length) return;
    if (!bearer || !csrf) {
      scheduleQueue(30_000);
      return;
    }

    const job = queue.shift();
    const outcome = await attemptBlock(job);
    if (outcome.state === 'success') {
      await recordSuccess(job);
      await broadcastResult(job, 'success');
    } else if (outcome.state === 'skipped') {
      await broadcastResult(job, 'skipped', outcome.message);
    } else if (outcome.state === 'already-blocked') {
      await broadcastResult(job, 'already-blocked', outcome.message);
    } else if (outcome.state === 'retry' && (job.attempts ?? 0) + 1 < MAX_ATTEMPTS) {
      job.attempts = (job.attempts ?? 0) + 1;
      queue.push(job);
    } else {
      await broadcastResult(job, 'failed', outcome.message);
    }

    await extensionAPI.storage.local.set({ [QUEUE_KEY]: queue });
    if (queue.length) scheduleQueue(BLOCK_INTERVAL_MS);
  } finally {
    queueProcessing = false;
  }
}

async function enqueueBlock(job, sender) {
  if (job.csrf) {
    csrf = job.csrf;
    await extensionAPI.storage.session.set({ csrf });
  }

  const stored = await extensionAPI.storage.local.get([QUEUE_KEY]);
  const queue = Array.isArray(stored[QUEUE_KEY]) ? stored[QUEUE_KEY] : [];
  const existing = queue.find(item => item.username === job.username);
  if (existing) {
    existing.sourceTabId = sender.tab?.id ?? existing.sourceTabId;
    existing.pageKey = job.pageKey ?? existing.pageKey;
    existing.pageUrl = job.pageUrl ?? existing.pageUrl;
    existing.matchedKeywords = Array.isArray(job.matchedKeywords)
      ? job.matchedKeywords
      : existing.matchedKeywords;
    await extensionAPI.storage.local.set({ [QUEUE_KEY]: queue });
  } else {
    queue.push({
      username: job.username,
      displayName: job.displayName ?? '',
      reason: job.reason ?? '',
      matchedKeywords: Array.isArray(job.matchedKeywords)
        ? job.matchedKeywords
        : [],
      content: job.content ?? '',
      pageUrl: job.pageUrl ?? '',
      pageKey: job.pageKey ?? '',
      hostname: job.hostname === 'twitter.com' ? 'twitter.com' : 'x.com',
      sourceTabId: sender.tab?.id ?? null,
      createdAt: new Date().toISOString(),
      attempts: 0
    });
    await extensionAPI.storage.local.set({ [QUEUE_KEY]: queue });
  }
  scheduleQueue();
  return { queued: true, queueSize: queue.length };
}

extensionAPI.alarms.onAlarm.addListener(alarm => {
  if (alarm.name === QUEUE_ALARM) {
    queueScheduleId++;
    withQueueLock(processQueue);
  }
  if (alarm.name === UPLOAD_ALARM) {
    uploadScheduleId++;
    processUploadQueue();
  }
  if (alarm.name === UPDATE_ALARM) {
    checkForUpdate();
  }
  if (alarm.name === HEARTBEAT_ALARM) {
    sendHeartbeat().catch(() => {});
  }
  if (alarm.name === RULES_ALARM) {
    refreshRules().catch(() => {});
  }
});

extensionAPI.runtime.onMessage.addListener((message, sender) => {
  if (message.type === 'ENQUEUE_BLOCK') {
    return withQueueLock(() => enqueueBlock(message.job, sender))
      .catch(error => ({ queued: false, error: error.message }));
  }
  if (message.type === 'PROCESS_BLOCK_QUEUE') {
    return withQueueLock(processQueue).then(() => ({ ok: true }));
  }
  if (message.type === 'CHECK_FOR_UPDATE') {
    return checkForUpdate()
      .then(updateInfo => ({ ok: true, updateInfo }))
      .catch(error => ({ ok: false, error: error.message }));
  }
  if (message.type === 'REFRESH_RULES') {
    return refreshRules()
      .then(config => ({ ok: true, config }))
      .catch(error => ({ ok: false, error: error.message }));
  }
});
