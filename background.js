const QUEUE_KEY = 'pendingBlockQueue';
const QUEUE_ALARM = 'autobanrobot-process-queue';
const MAX_ATTEMPTS = 3;
const MAX_BLOCK_HISTORY = 500;
const BLOCK_INTERVAL_MS = 500;
const HISTORY_HIDE_MIGRATION_KEY = 'historyHideMigrationV1';
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
const MIN_ONLINE_RULE_VERSION = 9;
const INSTALLATION_ID_KEY = 'anonymousInstallationId';
const LATEST_RELEASE_API =
  'https://api.github.com/repos/serenamustrich/autobanrobot/releases/latest';
const DEFAULT_ACCOUNT_WHITELIST = ['AAAGodofWealth'];

let bearer = null;
let csrf = null;
let queueProcessing = false;
let queueOperation = Promise.resolve();
let queueScheduleId = 0;
let uploadProcessing = false;
let uploadScheduleId = 0;
let historyHideMigrationRunning = false;

function isExtensionShutdownError(error) {
  return /(?:No SW|Extension context invalidated|message port closed)/i.test(
    error?.message ?? ''
  );
}

function settleExtensionCall(promise, operation) {
  return Promise.resolve(promise).catch(error => {
    if (!isExtensionShutdownError(error)) {
      console.error(`${operation}:`, error);
    }
    return undefined;
  });
}

const authReady = settleExtensionCall(
  chrome.storage.session.get(['bearer', 'csrf']).then(result => {
    bearer = result.bearer ?? null;
    csrf = result.csrf ?? null;
  }),
  'Failed to restore session authentication'
);

function withQueueLock(operation) {
  const result = queueOperation.then(operation, operation);
  queueOperation = result.catch(() => {});
  return result;
}

async function initializeKeywords() {
  const stored = await chrome.storage.local.get(['keywords']);
  if (Array.isArray(stored.keywords)) return;
  const response = await fetch(chrome.runtime.getURL('default-keywords.json'));
  await chrome.storage.local.set({ keywords: await response.json() });
}

async function initializeAccountWhitelist() {
  const stored = await chrome.storage.local.get(['accountWhitelist']);
  const current = Array.isArray(stored.accountWhitelist) ? stored.accountWhitelist : [];
  const merged = [...new Set([...DEFAULT_ACCOUNT_WHITELIST, ...current])];
  await chrome.storage.local.set({ accountWhitelist: merged });
}

async function migrateLegacyRuleStates() {
  const stored = await chrome.storage.local.get([
    'emojiEnglishEmojiEnabled',
    'singleEmojiEnabled',
    'structuredEmojiTimeEnabled',
    'structuredThreeSegmentEnabled',
    'remoteRuleStates',
    'onlineRuleStateMigrationV1'
  ]);
  if (stored.onlineRuleStateMigrationV1 === true) return;
  const states = { ...(stored.remoteRuleStates ?? {}) };
  const mappings = {
    emojiEnglishEmojiEnabled: 'emoji-content-emoji',
    singleEmojiEnabled: 'single-emoji-content',
    structuredEmojiTimeEnabled: 'five-segment-alternating',
    structuredThreeSegmentEnabled: 'three-segment-single-middle'
  };
  for (const [legacyKey, ruleId] of Object.entries(mappings)) {
    if (stored[legacyKey] === false && states[ruleId] === undefined) {
      states[ruleId] = false;
    }
  }
  await chrome.storage.local.set({
    remoteRuleStates: states,
    onlineRuleStateMigrationV1: true
  });
}

async function initializeRules() {
  const stored = await chrome.storage.local.get(['remoteRuleConfig']);
  if (
    stored.remoteRuleConfig?.rules &&
    stored.remoteRuleConfig.version >= MIN_ONLINE_RULE_VERSION
  ) return;
  const response = await fetch(chrome.runtime.getURL('default-rules.json'));
  await chrome.storage.local.set({ remoteRuleConfig: await response.json() });
}

function isValidRuleConfig(config) {
  return Number.isSafeInteger(config?.version) &&
    config.version >= MIN_ONLINE_RULE_VERSION &&
    Array.isArray(config.rules) &&
    config.rules.length <= 100 &&
    config.rules.every(rule =>
      typeof rule?.id === 'string' && rule.id.length <= 64 &&
      typeof rule?.name === 'string' && rule.name.length <= 120 &&
      ((rule.matcher === undefined &&
        typeof rule?.pattern === 'string' && rule.pattern.length <= 2000 &&
        typeof rule?.flags === 'string' && /^[gimsuy]*$/.test(rule.flags)) ||
        (rule.pattern === undefined &&
        ['singleEmoji', 'structuredEmojiTime',
            'structuredThreeSegment'].includes(rule?.matcher))) &&
      ['content', 'username', 'displayName'].includes(rule.scope ?? 'content') &&
      (rule.requiresDefaultAvatar === undefined ||
        typeof rule.requiresDefaultAvatar === 'boolean') &&
      ['raw', 'compact', 'noSymbols'].includes(rule.normalization ?? 'raw')
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
  await chrome.storage.local.set({ remoteRuleConfig: config });
  return config;
}

function scheduleQueue(delayMs = 0) {
  const delay = Math.max(delayMs, 50);
  const scheduleId = ++queueScheduleId;
  settleExtensionCall(
    chrome.alarms.create(QUEUE_ALARM, { when: Date.now() + delay }),
    'Failed to schedule block queue'
  );
  setTimeout(() => {
    if (scheduleId !== queueScheduleId) return;
    queueScheduleId++;
    settleExtensionCall(
      chrome.alarms.clear(QUEUE_ALARM),
      'Failed to clear block queue alarm'
    );
    withQueueLock(processQueue).catch(() => {});
  }, delay);
}

function scheduleUpload(delayMs = 0) {
  const delay = Math.max(delayMs, 100);
  const scheduleId = ++uploadScheduleId;
  settleExtensionCall(
    chrome.alarms.create(UPLOAD_ALARM, { when: Date.now() + delay }),
    'Failed to schedule upload queue'
  );
  setTimeout(() => {
    if (scheduleId !== uploadScheduleId) return;
    uploadScheduleId++;
    settleExtensionCall(
      chrome.alarms.clear(UPLOAD_ALARM),
      'Failed to clear upload queue alarm'
    );
    processUploadQueue().catch(() => {});
  }, delay);
}

function scheduleUpdateChecks() {
  settleExtensionCall(
    chrome.alarms.create(UPDATE_ALARM, {
      delayInMinutes: 1,
      periodInMinutes: 12 * 60
    }),
    'Failed to schedule update checks'
  );
}

function scheduleHeartbeat() {
  settleExtensionCall(
    chrome.alarms.create(HEARTBEAT_ALARM, {
      delayInMinutes: 1,
      periodInMinutes: 1
    }),
    'Failed to schedule heartbeat'
  );
}

function scheduleRuleRefresh() {
  settleExtensionCall(
    chrome.alarms.create(RULES_ALARM, {
      delayInMinutes: 1,
      periodInMinutes: 5
    }),
    'Failed to schedule rule refresh'
  );
}

async function getInstallationId() {
  const stored = await chrome.storage.local.get([INSTALLATION_ID_KEY]);
  if (typeof stored[INSTALLATION_ID_KEY] === 'string' &&
      stored[INSTALLATION_ID_KEY]) {
    return stored[INSTALLATION_ID_KEY];
  }
  const installationId = crypto.randomUUID();
  await chrome.storage.local.set({ [INSTALLATION_ID_KEY]: installationId });
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
      platform: 'chrome-edge',
      version: chrome.runtime.getManifest().version,
      clientType: 'plugin'
    })
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  processUploadQueue().catch(() => {});
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
  const currentVersion = chrome.runtime.getManifest().version;
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
    await chrome.storage.local.set({ updateInfo });
    return updateInfo;
  } catch (error) {
    const updateInfo = {
      checkedAt: new Date().toISOString(),
      currentVersion,
      error: error.message
    };
    await chrome.storage.local.set({ updateInfo });
    return updateInfo;
  }
}

chrome.runtime.onInstalled.addListener(() => {
  Promise.all([initializeKeywords(), initializeAccountWhitelist(), migrateLegacyRuleStates(), initializeRules()]).catch(error => {
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
  withQueueLock(migrateHistoryHides).catch(() => {});
});

chrome.runtime.onStartup.addListener(() => {
  initializeAccountWhitelist().catch(() => {});
  scheduleQueue();
  scheduleUpload();
  scheduleUpdateChecks();
  checkForUpdate();
  scheduleHeartbeat();
  sendHeartbeat().catch(() => {});
  scheduleRuleRefresh();
  refreshRules().catch(() => {});
  withQueueLock(migrateHistoryHides).catch(() => {});
});

chrome.webRequest.onBeforeSendHeaders.addListener(
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
      settleExtensionCall(
        chrome.storage.session.set({ bearer, csrf }),
        'Failed to persist session authentication'
    );
    scheduleQueue();
    withQueueLock(migrateHistoryHides).catch(() => {});
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

function confirmsUnblock(payload, username) {
  if (!payload || payload.errors?.length) return false;
  const sameUser =
    typeof payload.screen_name !== 'string' ||
    payload.screen_name.toLocaleLowerCase() === username.toLocaleLowerCase();
  return sameUser && (
    payload.blocking === false ||
    payload.relationship?.source?.blocking === false
  );
}

function confirmsMute(payload, username) {
  if (!payload || payload.errors?.length) return false;
  const sameUser =
    typeof payload.screen_name !== 'string' ||
    payload.screen_name.toLocaleLowerCase() === username.toLocaleLowerCase();
  return sameUser && (
    payload.muting === true ||
    payload.relationship?.source?.muting === true
  );
}

function confirmsUnmute(payload, username) {
  if (!payload || payload.errors?.length) return false;
  const sameUser =
    typeof payload.screen_name !== 'string' ||
    payload.screen_name.toLocaleLowerCase() === username.toLocaleLowerCase();
  return sameUser && (
    payload.muting === false ||
    payload.relationship?.source?.muting === false
  );
}

function isRetryableStatus(status) {
  return status === 0 || status === 408 || status === 425 ||
    status === 429 || status >= 500;
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

async function attemptMuteOnly(record) {
  await authReady;
  if (!bearer || !csrf) {
    return { state: 'retry', message: '尚未获取 X 登录凭据' };
  }
  const username = String(record?.username ?? '').trim();
  if (!/^[A-Za-z0-9_]{1,15}$/.test(username)) {
    return { state: 'failed', message: '账号用户名无效' };
  }
  const target = {
    ...record,
    username,
    hostname: record?.hostname === 'twitter.com' ||
      String(record?.pageUrl ?? '').includes('twitter.com')
      ? 'twitter.com'
      : 'x.com'
  };
  const relationship = await fetchRelationship(target);
  const source = relationship.payload?.relationship?.source;
  if (!relationship.ok || !source) {
    return {
      state: isRetryableStatus(relationship.status) ? 'retry' : 'failed',
      message: relationship.status
        ? `无法确认当前隐藏状态（HTTP ${relationship.status}）`
        : '无法确认当前隐藏状态'
    };
  }
  if (source.muting === true) return { state: 'already-muted', message: '该账号已经处于隐藏状态' };

  try {
    const response = await fetch(
      `https://${target.hostname}/i/api/1.1/mutes/users/create.json`,
      {
        method: 'POST',
        headers: apiHeaders(),
        body: `screen_name=${encodeURIComponent(username)}`,
        credentials: 'include'
      }
    );
    const payload = parseJson(await response.text());
    if (!response.ok || payload?.errors?.length) {
      return {
        state: isRetryableStatus(response.status) ? 'retry' : 'failed',
        message: `隐藏请求失败（HTTP ${response.status}）`
      };
    }
    const verification = await fetchRelationship(target);
    const verifiedSource = verification.payload?.relationship?.source;
    if (verification.ok && verifiedSource && verifiedSource.muting === false) {
      return { state: 'retry', message: 'X 未确认隐藏成功' };
    }
    return { state: 'success', message: '已确认隐藏' };
  } catch (error) {
    return { state: 'retry', message: `隐藏异常：${error.message}` };
  }
}

async function migrateHistoryHides() {
  await authReady;
  if (historyHideMigrationRunning || !bearer || !csrf) return;
  const stored = await chrome.storage.local.get([
    'blockHistory',
    HISTORY_HIDE_MIGRATION_KEY
  ]);
  if (stored[HISTORY_HIDE_MIGRATION_KEY] === true) return;
  historyHideMigrationRunning = true;
  try {
    const history = Array.isArray(stored.blockHistory) ? stored.blockHistory : [];
    const updated = history.map(item => ({ ...item }));
    let complete = true;
    for (let index = 0; index < updated.length; index += 1) {
      const record = updated[index];
      if (record.unblockedAt || record.hidden) continue;
      const outcome = await attemptMuteOnly(record);
      if (outcome.state === 'success' || outcome.state === 'already-muted') {
        record.hidden = true;
        record.hiddenAt = new Date().toISOString();
        await broadcastResult(
          record,
          'success',
          '历史记录同步：已确认隐藏',
          true
        );
      } else {
        complete = false;
      }
      await new Promise(resolve => setTimeout(resolve, BLOCK_INTERVAL_MS));
    }
    await chrome.storage.local.set({ blockHistory: updated });
    if (complete) {
      await chrome.storage.local.set({ [HISTORY_HIDE_MIGRATION_KEY]: true });
    }
  } finally {
    historyHideMigrationRunning = false;
  }
}

async function restoreHistoryVisuals(tabId) {
  const stored = await chrome.storage.local.get(['blockHistory']);
  const history = Array.isArray(stored.blockHistory) ? stored.blockHistory : [];
  let restored = 0;
  for (const record of history) {
    if (record.unblockedAt || !record.hidden) continue;
    restored += 1;
    await broadcastResult(
      { ...record, sourceTabId: tabId },
      'success',
      '历史记录同步：已恢复',
      true
    );
  }
  return { ok: true, restored };
}

async function attemptBlock(job) {
  const target = {
    ...job,
    hostname: job.hostname === 'twitter.com' ? 'twitter.com' : 'x.com'
  };
  const relationship = await fetchRelationship(target);
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
  try {
    let muted = source.muting === true;
    let blocking = source.blocking === true;

    if (!muted) {
      const response = await fetch(
        `https://${target.hostname}/i/api/1.1/mutes/users/create.json`,
        {
          method: 'POST',
          headers: apiHeaders(),
          body: `screen_name=${encodeURIComponent(target.username)}`,
          credentials: 'include'
        }
      );
      const payload = parseJson(await response.text());
      if (!response.ok || payload?.errors?.length) {
        return {
          state: isRetryableStatus(response.status) ? 'retry' : 'failed',
          message: `隐藏请求失败（HTTP ${response.status}）`
        };
      }
      muted = true;
    }

    if (!blocking) {
      const response = await fetch(
        `https://${target.hostname}/i/api/1.1/blocks/create.json`,
        {
          method: 'POST',
          headers: apiHeaders(),
          body: `screen_name=${encodeURIComponent(target.username)}`,
          credentials: 'include'
        }
      );
      const payload = parseJson(await response.text());
      if (!response.ok || payload?.errors?.length) {
        return {
          state: isRetryableStatus(response.status) ? 'retry' : 'failed',
          message: `屏蔽请求失败（HTTP ${response.status}）`
        };
      }
      blocking = confirmsBlock(payload, target.username) || response.ok;
    }

    const verification = await fetchRelationship(target);
    const verifiedSource = verification.payload?.relationship?.source;
    const confirmedBlocking = verification.ok && verifiedSource
      ? verifiedSource.blocking === true
      : blocking;
    const confirmedMuted = verification.ok && verifiedSource &&
      typeof verifiedSource.muting === 'boolean'
      ? verifiedSource.muting === true
      : muted;
    if (confirmedBlocking && confirmedMuted) {
      return { state: 'success', message: '已确认屏蔽和隐藏' };
    }
    return { state: 'retry', message: 'X 未同时确认屏蔽和隐藏成功' };
  } catch (error) {
    return { state: 'retry', message: `异常: ${error.message}` };
  }
}

async function attemptUnblock(record) {
  await authReady;
  if (!bearer || !csrf) {
    return { state: 'failed', message: '尚未获取 X 登录凭据，请打开 X 后重试' };
  }
  const username = String(record?.username ?? '').trim();
  if (!/^[A-Za-z0-9_]{1,15}$/.test(username)) {
    return { state: 'failed', message: '账号用户名无效' };
  }
  let hostname = 'x.com';
  try {
    const candidate = new URL(record?.pageUrl ?? '').hostname;
    if (candidate === 'twitter.com' || candidate === 'x.com') hostname = candidate;
  } catch {}
  const target = { username, hostname };
  const relationship = await fetchRelationship(target);
  const source = relationship.payload?.relationship?.source;
  if (!relationship.ok || !source) {
    return {
      state: 'failed',
      message: relationship.status
        ? `无法确认当前屏蔽状态（HTTP ${relationship.status}）`
        : '无法确认当前屏蔽状态'
    };
  }
  try {
    let muted = source.muting === true;
    let blocking = source.blocking === true;

    if (source.muting !== false) {
      const response = await fetch(
        `https://${hostname}/i/api/1.1/mutes/users/destroy.json`,
        {
          method: 'POST',
          headers: apiHeaders(),
          body: `screen_name=${encodeURIComponent(username)}`,
          credentials: 'include'
        }
      );
      const payload = parseJson(await response.text());
      if (!response.ok || payload?.errors?.length) {
        return { state: isRetryableStatus(response.status) ? 'retry' : 'failed', message: `取消隐藏失败（HTTP ${response.status}）` };
      }
      muted = false;
    }

    if (source.blocking !== false) {
      const response = await fetch(
        `https://${hostname}/i/api/1.1/blocks/destroy.json`,
        {
          method: 'POST',
          headers: apiHeaders(),
          body: `screen_name=${encodeURIComponent(username)}`,
          credentials: 'include'
        }
      );
      const payload = parseJson(await response.text());
      if (!response.ok || payload?.errors?.length) {
        return { state: isRetryableStatus(response.status) ? 'retry' : 'failed', message: `取消屏蔽失败（HTTP ${response.status}）` };
      }
      blocking = false;
    }

    const verification = await fetchRelationship(target);
    const verifiedSource = verification.payload?.relationship?.source;
    const confirmedBlocking = verification.ok && verifiedSource
      ? verifiedSource.blocking === false
      : !blocking;
    const confirmedMuted = verification.ok && verifiedSource &&
      typeof verifiedSource.muting === 'boolean'
      ? verifiedSource.muting === false
      : !muted;
    if (confirmedBlocking && confirmedMuted) {
      return { state: 'success', message: '已确认取消屏蔽和隐藏' };
    }
    return { state: 'retry', message: 'X 未同时确认取消屏蔽和隐藏成功' };
  } catch (error) {
    return { state: 'retry', message: `取消屏蔽和隐藏异常：${error.message}` };
  }
}

async function unblockHistoryRecord(record) {
  const stored = await chrome.storage.local.get([QUEUE_KEY, 'blockHistory']);
  const username = String(record?.username ?? '').toLocaleLowerCase();
  const queue = Array.isArray(stored[QUEUE_KEY]) ? stored[QUEUE_KEY] : [];
  await chrome.storage.local.set({
    [QUEUE_KEY]: queue.filter(job =>
      String(job.username ?? '').toLocaleLowerCase() !== username
    )
  });

  const outcome = await attemptUnblock(record);
  if (outcome.state !== 'success' && outcome.state !== 'already-unblocked') {
    return { ok: false, error: outcome.message };
  }
  const latest = await chrome.storage.local.get(['blockHistory']);
  const history = Array.isArray(latest.blockHistory) ? latest.blockHistory : [];
  const unblockedAt = new Date().toISOString();
  await chrome.storage.local.set({
    blockHistory: history.map(item => {
      const sameRecord = record.clientEventId
        ? item.clientEventId === record.clientEventId
        : String(item.username ?? '').toLocaleLowerCase() === username;
      return sameRecord ? { ...item, unblockedAt } : item;
    })
  });
  return { ok: true, state: outcome.state, unblockedAt };
}

async function reblockHistoryRecord(record) {
  const username = String(record?.username ?? '').trim();
  if (!/^[A-Za-z0-9_]{1,15}$/.test(username)) {
    return { ok: false, error: '账号用户名无效' };
  }
  const stored = await chrome.storage.local.get([QUEUE_KEY]);
  const queue = Array.isArray(stored[QUEUE_KEY]) ? stored[QUEUE_KEY] : [];
  await chrome.storage.local.set({
    [QUEUE_KEY]: queue.filter(job =>
      String(job.username ?? '').toLocaleLowerCase() !== username.toLocaleLowerCase()
    )
  });
  const outcome = await attemptBlock(record);
  if (outcome.state !== 'success') return { ok: false, error: outcome.message };
  await recordSuccess(record);
  return { ok: true, state: outcome.state, message: outcome.message };
}

async function broadcastResult(job, state, message = '', historical = false) {
  const tabIds = job.sourceTabId
    ? [job.sourceTabId]
    : historical
      ? (await chrome.tabs.query({
          url: ['https://x.com/*', 'https://twitter.com/*']
        })).map(tab => tab.id).filter(Number.isInteger)
      : [];
  await Promise.all(tabIds.map(tabId => chrome.tabs.sendMessage(tabId, {
      type: 'BLOCK_RESULT',
      result: { ...job, state, message, historical }
    }).catch(() => {})));
}

async function recordSuccess(job) {
  const stored = await chrome.storage.local.get([
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
    hostname: job.hostname === 'twitter.com' ? 'twitter.com' : 'x.com',
    csrf: job.csrf ?? '',
    action: 'block+mute',
    hidden: true,
    blockedAt: new Date().toISOString()
  };
  await chrome.storage.local.set({
    blockCount: (stored.blockCount ?? 0) + 1,
    blockHistory: [
      record,
      ...history.filter(item => item.username !== job.username)
    ].slice(0, MAX_BLOCK_HISTORY)
  });
  await enqueueBanUpload(record);
}

async function enqueueBanUpload(record) {
  const stored = await chrome.storage.local.get([UPLOAD_QUEUE_KEY]);
  const queue = Array.isArray(stored[UPLOAD_QUEUE_KEY])
    ? stored[UPLOAD_QUEUE_KEY]
    : [];
  if (!queue.some(item => item.clientEventId === record.clientEventId)) {
    queue.push({ ...record, uploadAttempts: 0 });
    await chrome.storage.local.set({ [UPLOAD_QUEUE_KEY]: queue });
  }
  scheduleUpload();
}

async function processUploadQueue() {
  if (uploadProcessing) return;
  uploadProcessing = true;
  try {
    const stored = await chrome.storage.local.get([UPLOAD_QUEUE_KEY]);
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
          blockedAt: record.blockedAt,
          clientType: 'plugin'
        })
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      queue.shift();
      await chrome.storage.local.set({ [UPLOAD_QUEUE_KEY]: queue });
      if (queue.length) scheduleUpload(250);
    } catch {
      record.uploadAttempts = (record.uploadAttempts ?? 0) + 1;
      await chrome.storage.local.set({ [UPLOAD_QUEUE_KEY]: queue });
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
    const stored = await chrome.storage.local.get([QUEUE_KEY]);
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
    } else {
      job.attempts = (job.attempts ?? 0) + 1;
      queue.push(job);
    }

    await chrome.storage.local.set({ [QUEUE_KEY]: queue });
    if (queue.length) scheduleQueue(BLOCK_INTERVAL_MS);
  } finally {
    queueProcessing = false;
  }
}

async function enqueueBlock(job, sender) {
  if (job.csrf) {
    csrf = job.csrf;
    await chrome.storage.session.set({ csrf });
  }

  const stored = await chrome.storage.local.get([QUEUE_KEY]);
  const queue = Array.isArray(stored[QUEUE_KEY]) ? stored[QUEUE_KEY] : [];
  const existing = queue.find(item => item.username === job.username);
  if (existing) {
    existing.sourceTabId = sender.tab?.id ?? existing.sourceTabId;
    existing.pageKey = job.pageKey ?? existing.pageKey;
    existing.pageUrl = job.pageUrl ?? existing.pageUrl;
    existing.matchedKeywords = Array.isArray(job.matchedKeywords)
      ? job.matchedKeywords
      : existing.matchedKeywords;
    await chrome.storage.local.set({ [QUEUE_KEY]: queue });
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
    await chrome.storage.local.set({ [QUEUE_KEY]: queue });
  }
  scheduleQueue();
  return { queued: true, queueSize: queue.length };
}

chrome.alarms.onAlarm.addListener(alarm => {
  if (alarm.name === QUEUE_ALARM) {
    queueScheduleId++;
    withQueueLock(processQueue).catch(() => {});
  }
  if (alarm.name === UPLOAD_ALARM) {
    uploadScheduleId++;
    processUploadQueue().catch(() => {});
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

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'ENQUEUE_BLOCK') {
    withQueueLock(() => enqueueBlock(message.job, sender))
      .then(sendResponse)
      .catch(error => sendResponse({ queued: false, error: error.message }));
    return true;
  }
  if (message.type === 'PROCESS_BLOCK_QUEUE') {
    withQueueLock(processQueue)
      .then(() => sendResponse({ ok: true }))
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  if (message.type === 'RESTORE_HISTORY_VISUALS') {
    withQueueLock(() => restoreHistoryVisuals(sender.tab?.id))
      .then(sendResponse)
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  if (message.type === 'UNBLOCK_USER') {
    withQueueLock(() => unblockHistoryRecord(message.record))
      .then(sendResponse)
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  if (message.type === 'REBLOCK_USER') {
    withQueueLock(() => reblockHistoryRecord(message.record))
      .then(sendResponse)
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  if (message.type === 'CHECK_FOR_UPDATE') {
    checkForUpdate()
      .then(updateInfo => sendResponse({ ok: true, updateInfo }))
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  if (message.type === 'REFRESH_RULES') {
    refreshRules()
      .then(config => sendResponse({ ok: true, config }))
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
});
