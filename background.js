const QUEUE_KEY = 'pendingBlockQueue';
const QUEUE_ALARM = 'autobanrobot-process-queue';
const MAX_ATTEMPTS = 3;
const MAX_BLOCK_HISTORY = 500;
const BLOCK_INTERVAL_MS = 500;

let bearer = null;
let csrf = null;
let queueProcessing = false;
let queueOperation = Promise.resolve();
let queueScheduleId = 0;

const authReady = chrome.storage.session.get(['bearer', 'csrf']).then(result => {
  bearer = result.bearer ?? null;
  csrf = result.csrf ?? null;
});

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

async function initializeSettings() {
  const stored = await chrome.storage.local.get([
    'emojiEnglishEmojiEnabled',
    'singleEmojiEnabled'
  ]);
  const defaults = {};
  if (typeof stored.emojiEnglishEmojiEnabled !== 'boolean') {
    defaults.emojiEnglishEmojiEnabled = true;
  }
  if (typeof stored.singleEmojiEnabled !== 'boolean') {
    defaults.singleEmojiEnabled = true;
  }
  if (Object.keys(defaults).length) await chrome.storage.local.set(defaults);
}

function scheduleQueue(delayMs = 0) {
  const delay = Math.max(delayMs, 50);
  const scheduleId = ++queueScheduleId;
  chrome.alarms.create(QUEUE_ALARM, { when: Date.now() + delay });
  setTimeout(() => {
    if (scheduleId !== queueScheduleId) return;
    queueScheduleId++;
    chrome.alarms.clear(QUEUE_ALARM);
    withQueueLock(processQueue);
  }, delay);
}

chrome.runtime.onInstalled.addListener(() => {
  Promise.all([initializeKeywords(), initializeSettings()]).catch(error => {
    console.error('Failed to initialize extension settings:', error);
  });
  scheduleQueue();
});

chrome.runtime.onStartup.addListener(() => scheduleQueue());

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
      chrome.storage.session.set({ bearer, csrf });
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
  await chrome.tabs.sendMessage(job.sourceTabId, {
      type: 'BLOCK_RESULT',
      result: { ...job, state, message }
  }).catch(() => {});
}

async function recordSuccess(job) {
  const stored = await chrome.storage.local.get(['blockCount', 'blockHistory']);
  const history = Array.isArray(stored.blockHistory) ? stored.blockHistory : [];
  const record = {
    username: job.username,
    displayName: job.displayName,
    reason: job.reason,
    content: job.content,
    pageUrl: job.pageUrl,
    blockedAt: new Date().toISOString()
  };
  await chrome.storage.local.set({
    blockCount: (stored.blockCount ?? 0) + 1,
    blockHistory: [
      record,
      ...history.filter(item => item.username !== job.username)
    ].slice(0, MAX_BLOCK_HISTORY)
  });
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
    } else if (outcome.state === 'already-blocked') {
      await broadcastResult(job, 'already-blocked', outcome.message);
    } else if (outcome.state === 'retry' && (job.attempts ?? 0) + 1 < MAX_ATTEMPTS) {
      job.attempts = (job.attempts ?? 0) + 1;
      queue.push(job);
    } else {
      await broadcastResult(job, 'failed', outcome.message);
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
    await chrome.storage.local.set({ [QUEUE_KEY]: queue });
  } else {
    queue.push({
      username: job.username,
      displayName: job.displayName ?? '',
      reason: job.reason ?? '',
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
    withQueueLock(processQueue);
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
    withQueueLock(processQueue).then(() => sendResponse({ ok: true }));
    return true;
  }
});
