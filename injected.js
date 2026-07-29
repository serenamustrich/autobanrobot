(() => {
  let SPAM = [];

  window.addEventListener('__twblocker_keywords__', e => {
    SPAM = e.detail?.kws ?? SPAM;
    processedSignatures = new WeakMap();
    scanAll();
  });
  let bearer = null;
  const pendingBlocks = new Map();
  const queuedBlocks = new Map();
  const blocked = new Set();
  const blocking = new Set();
  const matchedElements = new Map();
  let processedSignatures = new WeakMap();
  let scanScheduled = false;
  let blockQueueRunning = false;
  let pageStats = createPageStats();
  const _f = window.fetch.bind(window);
  const MAX_BLOCK_ATTEMPTS = 3;
  const BLOCK_INTERVAL_MS = 500;

  function currentPageKey() {
    return `${location.pathname}${location.search}`;
  }

  function createPageStats() {
    return {
      key: currentPageKey(),
      matchedAccounts: new Set(),
      successfulBlockCount: 0
    };
  }

  function syncPageStats() {
    if (pageStats.key === currentPageKey()) return pageStats;
    pageStats = createPageStats();
    matchedElements.clear();
    processedSignatures = new WeakMap();
    return pageStats;
  }

  function toast(msg, ok = true, stats = null) {
    const show = () => {
      const el = document.createElement('div');
      el.style.cssText = `
        position:fixed;bottom:24px;right:24px;z-index:2147483647;
        background:${ok ? '#1d9bf0' : '#e0245e'};color:#fff;
        padding:10px 16px;border-radius:10px;font-size:13px;
        font-family:-apple-system,sans-serif;
        box-shadow:0 2px 12px rgba(0,0,0,.5);max-width:300px;word-break:break-all;`;
      const message = document.createElement('div');
      message.textContent = msg;
      el.appendChild(message);
      if (stats && stats === syncPageStats()) {
        const statsEl = document.createElement('div');
        statsEl.style.cssText = `
          margin-top:6px;padding-top:6px;border-top:1px solid rgba(255,255,255,.28);
          font-size:11px;opacity:.92;`;
        statsEl.textContent =
          `当前页面：已匹配 ${pageStats.matchedAccounts.size} · 已屏蔽 ${pageStats.successfulBlockCount}`;
        el.appendChild(statsEl);
      }
      document.body.appendChild(el);
      setTimeout(() => el.remove(), 4000);
    };
    document.body ? show() : document.addEventListener('DOMContentLoaded', show);
  }

  // 接收来自 content.js 的 bearer token（background 层抓到的）
  window.addEventListener('__twblocker_bearer__', e => {
    const token = e.detail?.token;
    if (!token) return;
    const isNew = !bearer;
    bearer = token;
    if (isNew) {
      toast('🔑 Token 已就绪，开始封号');
      const pending = [...pendingBlocks.entries()];
      pendingBlocks.clear();
      pending.forEach(([username, job]) => enqueueBlock(username, job));
    }
  });

  function wait(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  function updateMatchedElements(username, opacity, title = '') {
    matchedElements.get(username)?.forEach(el => {
      if (!el?.isConnected) return;
      el.style.opacity = opacity;
      el.title = title;
    });
  }

  function shouldRetry(status) {
    return status === 408 || status === 425 || status === 429 || status >= 500;
  }

  async function doBlock(username, job) {
    if (blocked.has(username) || blocking.has(username)) return;
    blocking.add(username);

    let lastError = '';
    try {
      for (let attempt = 1; attempt <= MAX_BLOCK_ATTEMPTS; attempt++) {
        const csrfRaw = document.cookie.match(/ct0=([^;]+)/)?.[1];
        if (!csrfRaw) {
          lastError = '无 CSRF token';
        } else {
          const csrf = decodeURIComponent(csrfRaw);
          try {
            const res = await _f(`https://${location.hostname}/i/api/1.1/blocks/create.json`, {
              method: 'POST',
              headers: {
                'authorization': bearer,
                'x-csrf-token': csrf,
                'content-type': 'application/x-www-form-urlencoded',
                'x-twitter-active-user': 'yes',
                'x-twitter-auth-type': 'OAuth2Session',
              },
              body: `screen_name=${encodeURIComponent(username)}`,
              credentials: 'include',
            });

            if (res.ok) {
              blocked.add(username);
              job.stats.successfulBlockCount++;
              toast(`✅ 已屏蔽 @${username}`, true, job.stats);
              updateMatchedElements(username, '0.12', `[已屏蔽] @${username}`);
              window.dispatchEvent(new CustomEvent('__twblocker_blocked__'));
              return;
            }

            const body = await res.text().catch(() => '');
            lastError = `HTTP ${res.status}: ${body.slice(0, 60)}`;
            if (!shouldRetry(res.status)) break;
          } catch (e) {
            lastError = `异常: ${e.message}`;
          }
        }

        if (attempt < MAX_BLOCK_ATTEMPTS) {
          await wait(750 * (2 ** (attempt - 1)));
        }
      }
    } finally {
      blocking.delete(username);
    }

    toast(`❌ @${username} ${lastError}`, false);
    updateMatchedElements(username, '1');
  }

  async function drainBlockQueue() {
    if (blockQueueRunning || !bearer) return;
    blockQueueRunning = true;

    try {
      while (queuedBlocks.size && bearer) {
        const [username, job] = queuedBlocks.entries().next().value;
        queuedBlocks.delete(username);
        await doBlock(username, job);
        if (queuedBlocks.size) await wait(BLOCK_INTERVAL_MS);
      }
    } finally {
      blockQueueRunning = false;
      if (queuedBlocks.size && bearer) drainBlockQueue();
    }
  }

  function enqueueBlock(username, job) {
    if (blocked.has(username) || blocking.has(username) || queuedBlocks.has(username)) return;
    queuedBlocks.set(username, job);
    drainBlockQueue();
  }

  function blockUser(username, el) {
    const stats = syncPageStats();
    stats.matchedAccounts.add(username);
    if (!matchedElements.has(username)) matchedElements.set(username, new Set());
    matchedElements.get(username).add(el);
    if (blocked.has(username) || blocking.has(username)) return;
    const job = { el, stats };
    if (!bearer) {
      pendingBlocks.set(username, job);
    } else {
      enqueueBlock(username, job);
    }
  }

  function isSingleEmoji(text) {
    const s = text.replace(/\s/g, '');
    if (!s) return false;
    return /^(?:\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?(?:\u{200D}\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?)*|[\u{1F1E0}-\u{1F1FF}]{2}|[0-9#*]\u{FE0F}?\u{20E3})$/u.test(s);
  }

  function normalizeForMatch(text) {
    return text
      .normalize('NFKC')
      .toLocaleLowerCase()
      .replace(/[\s\u200B-\u200D\u2060\uFEFF]/gu, '');
  }

  function hasKeyword(text) {
    const normalizedText = normalizeForMatch(text);
    return SPAM.some(keyword => {
      const normalizedKeyword = normalizeForMatch(keyword);
      return normalizedKeyword && normalizedText.includes(normalizedKeyword);
    });
  }

  function extractTweetText(root) {
    if (!root) return '';

    let text = '';
    const walker = document.createTreeWalker(
      root,
      NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT
    );

    while (walker.nextNode()) {
      const node = walker.currentNode;
      if (node.nodeType === Node.TEXT_NODE) {
        text += node.nodeValue ?? '';
        continue;
      }

      if (node instanceof HTMLImageElement) {
        text += node.getAttribute('alt') ?? '';
        continue;
      }

      if (
        node instanceof Element &&
        node.matches('[data-emoji][aria-label], [role="img"][aria-label]')
      ) {
        text += node.getAttribute('aria-label') ?? '';
      }
    }

    return text;
  }

  function processTweet(el) {
    const tweetText = el.querySelector('[data-testid="tweetText"]');
    const text = extractTweetText(tweetText);
    const nameBlock = el.querySelector('[data-testid="User-Name"]');
    if (!nameBlock) return;

    const link = nameBlock.querySelector('a[href^="/"]');
    const username = link?.getAttribute('href')?.replace(/^\//, '') ?? '';
    if (!username) return;

    const nameText = nameBlock.textContent.replace(`@${username}`, '').trim();
    const signature = `${username}\u0000${nameText}\u0000${text}`;
    if (processedSignatures.get(el) === signature) return;
    processedSignatures.set(el, signature);

    if (blocked.has(username)) {
      el.style.opacity = '0.12';
      el.title = `[已屏蔽] @${username}`;
      return;
    }

    const nameSpam = hasKeyword(nameText) || hasKeyword(username);
    const contentSpam = hasKeyword(text);
    const singleEmoji = isSingleEmoji(text);
    if (!nameSpam && !contentSpam && !singleEmoji) return;

    el.style.opacity = '0.35';
    blockUser(username, el);
  }

  function scanAll() {
    syncPageStats();
    ['[data-testid="tweet"]', 'article[role="article"]']
      .forEach(sel => document.querySelectorAll(sel).forEach(processTweet));
  }

  function scheduleScan() {
    if (scanScheduled) return;
    scanScheduled = true;
    requestAnimationFrame(() => {
      scanScheduled = false;
      scanAll();
    });
  }

  new MutationObserver(scheduleScan).observe(document.documentElement, {
    childList: true,
    subtree: true,
    characterData: true
  });
  setTimeout(scanAll, 2000);
})();
