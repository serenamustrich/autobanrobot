(() => {
  let SPAM = [];
  let emojiEnglishEmojiEnabled = true;
  let singleEmojiEnabled = true;

  window.addEventListener('__twblocker_keywords__', e => {
    SPAM = e.detail?.kws ?? SPAM;
    processedSignatures = new WeakMap();
    scanAll();
  });
  window.addEventListener('__twblocker_settings__', e => {
    if (typeof e.detail?.emojiEnglishEmojiEnabled === 'boolean') {
      emojiEnglishEmojiEnabled = e.detail.emojiEnglishEmojiEnabled;
    }
    if (typeof e.detail?.singleEmojiEnabled === 'boolean') {
      singleEmojiEnabled = e.detail.singleEmojiEnabled;
    }
    processedSignatures = new WeakMap();
    scanAll();
  });
  const blocked = new Set();
  const exemptAccounts = new Set();
  const queuedAccounts = new Set();
  const matchedElements = new Map();
  let processedSignatures = new WeakMap();
  let scanScheduled = false;
  let pageStats = createPageStats();

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

  function updateMatchedElements(username, opacity, title = '') {
    matchedElements.get(username)?.forEach(el => {
      if (!el?.isConnected) return;
      el.style.opacity = opacity;
      el.title = title;
    });
  }

  function blockUser(username, el, match) {
    const stats = syncPageStats();
    stats.matchedAccounts.add(username);
    if (!matchedElements.has(username)) matchedElements.set(username, new Set());
    matchedElements.get(username).add(el);
    if (
      blocked.has(username) ||
      exemptAccounts.has(username) ||
      queuedAccounts.has(username)
    ) return;
    queuedAccounts.add(username);
    const csrfRaw = document.cookie.match(/ct0=([^;]+)/)?.[1];
    window.dispatchEvent(new CustomEvent('__twblocker_enqueue__', {
      detail: {
        username,
        csrf: csrfRaw ? decodeURIComponent(csrfRaw) : '',
        hostname: location.hostname,
        pageKey: stats.key,
        pageUrl: location.href,
        displayName: match.displayName,
        reason: match.reason,
        content: match.content
      }
    }));
  }

  window.addEventListener('__twblocker_block_result__', event => {
    const result = event.detail;
    if (!result?.username) return;
    queuedAccounts.delete(result.username);
    const samePage = result.pageKey === currentPageKey();

    if (result.state === 'success') {
      blocked.add(result.username);
      if (!samePage) return;
      const stats = syncPageStats();
      stats.successfulBlockCount++;
      updateMatchedElements(
        result.username,
        '0.12',
        `[已屏蔽] @${result.username}`
      );
      toast(`✅ 已屏蔽 @${result.username}`, true, stats);
      return;
    }

    if (result.state === 'skipped') {
      exemptAccounts.add(result.username);
      if (!samePage) return;
      updateMatchedElements(
        result.username,
        '1',
        `[已跳过：${result.message}] @${result.username}`
      );
      toast(`⏭️ 已跳过 @${result.username}：${result.message}`);
      return;
    }

    if (result.state === 'already-blocked') {
      blocked.add(result.username);
      if (!samePage) return;
      updateMatchedElements(
        result.username,
        '0.12',
        `[已屏蔽] @${result.username}`
      );
      toast(`ℹ️ @${result.username} 已经处于屏蔽状态`);
      return;
    }

    if (!samePage) return;
    updateMatchedElements(result.username, '1');
    toast(`❌ @${result.username} ${result.message || '后台屏蔽失败'}`, false);
  });

  function isSingleEmoji(text) {
    const s = text.replace(/\s/g, '');
    if (!s) return false;
    return /^(?:\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?(?:\u{200D}\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?)*|[\u{1F1E0}-\u{1F1FF}]{2}|[0-9#*]\u{FE0F}?\u{20E3})$/u.test(s);
  }

  const emojiSegmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

  function isEmojiGrapheme(value) {
    return isSingleEmoji(value);
  }

  function isEmojiEnglishEmoji(text) {
    const normalized = text.trim();
    if (!normalized) return false;

    const graphemes = [...emojiSegmenter.segment(normalized)].map(item => item.segment);
    if (graphemes.length < 3) return false;
    if (!isEmojiGrapheme(graphemes[0]) || !isEmojiGrapheme(graphemes.at(-1))) return false;

    const middle = graphemes.slice(1, -1).join('');
    return (
      /\p{Script=Latin}/u.test(middle) &&
      /^[\p{Script=Latin}\p{Mark}\p{Number}\s'’.,!?&+\-_/]+$/u.test(middle)
    );
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
    if (exemptAccounts.has(username)) {
      el.style.opacity = '1';
      el.title = `[已跳过：你正在关注该账号] @${username}`;
      return;
    }

    const nameSpam = hasKeyword(nameText) || hasKeyword(username);
    const contentSpam = hasKeyword(text);
    const singleEmoji = singleEmojiEnabled && isSingleEmoji(text);
    const emojiEnglishEmoji =
      emojiEnglishEmojiEnabled && isEmojiEnglishEmoji(text);
    if (!nameSpam && !contentSpam && !singleEmoji && !emojiEnglishEmoji) return;

    el.style.opacity = '0.35';
    const reasons = [];
    if (nameSpam) reasons.push('用户名或显示名称命中关键词');
    if (contentSpam) reasons.push('内容命中关键词');
    if (singleEmoji) reasons.push('单 Emoji 内容');
    if (emojiEnglishEmoji) reasons.push('Emoji + 英文 + Emoji');
    blockUser(username, el, {
      displayName: nameText,
      reason: reasons.join('；'),
      content: text.slice(0, 160)
    });
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
