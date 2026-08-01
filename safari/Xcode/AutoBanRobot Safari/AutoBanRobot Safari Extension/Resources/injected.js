(() => {
  let SPAM = [];
  let emojiEnglishEmojiEnabled = true;
  let singleEmojiEnabled = true;
  let structuredEmojiTimeEnabled = true;
  let structuredThreeSegmentEnabled = true;

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
    if (typeof e.detail?.structuredEmojiTimeEnabled === 'boolean') {
      structuredEmojiTimeEnabled = e.detail.structuredEmojiTimeEnabled;
    }
    if (typeof e.detail?.structuredThreeSegmentEnabled === 'boolean') {
      structuredThreeSegmentEnabled = e.detail.structuredThreeSegmentEnabled;
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
        matchedKeywords: match.matchedKeywords,
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

  function isEmojiContentEmoji(text) {
    const normalized = text.trim();
    if (!normalized) return false;

    const graphemes = [...emojiSegmenter.segment(normalized)].map(item => item.segment);
    if (graphemes.length < 3) return false;
    if (!isEmojiGrapheme(graphemes[0]) || !isEmojiGrapheme(graphemes.at(-1))) return false;

    const middle = graphemes.slice(1, -1).join('');
    return middle.trim().length > 0;
  }

  function isStructuredEmojiTime(text) {
    const lines = text
      .split(/\r?\n/u)
      .map(line => line.trim())
      .filter(Boolean);
    if (lines.length !== 5) return false;

    const nonEmojiSegment = value => value.length > 0 && !isSingleEmoji(value);

    return (
      nonEmojiSegment(lines[0]) &&
      isSingleEmoji(lines[1]) &&
      nonEmojiSegment(lines[2]) &&
      isSingleEmoji(lines[3]) &&
      nonEmojiSegment(lines[4])
    );
  }

  function isStructuredThreeSegment(text) {
    const lines = text
      .split(/\r?\n/u)
      .map(line => line.trim())
      .filter(Boolean);
    if (lines.length !== 3) return false;

    const middle = [...emojiSegmenter.segment(lines[1])]
      .map(item => item.segment);
    return middle.length === 1;
  }

  function normalizeForMatch(text) {
    return text
      .normalize('NFKC')
      .toLocaleLowerCase()
      .replace(/[\s\u200B-\u200D\u2060\uFEFF]/gu, '');
  }

  function normalizeWithoutSymbolNoise(text) {
    return normalizeForMatch(text).replace(/[\p{P}\p{S}]/gu, '');
  }

  function normalizeHanKeywordNoise(text) {
    return normalizeForMatch(text).replace(/[^\p{Script=Han}]/gu, '');
  }

  function matchingKeywords(text) {
    const normalizedText = normalizeForMatch(text);
    const noiseStrippedText = normalizeWithoutSymbolNoise(text);
    return SPAM.filter(keyword => {
      const normalizedKeyword = normalizeForMatch(keyword);
      if (!normalizedKeyword) return false;
      if (normalizedText.includes(normalizedKeyword)) return true;

      const noiseStrippedKeyword = normalizeWithoutSymbolNoise(keyword);
      if (
        noiseStrippedKeyword.length >= 2 &&
        noiseStrippedText.includes(noiseStrippedKeyword)
      ) return true;

      if (/^\p{Script=Han}{2,}$/u.test(noiseStrippedKeyword)) {
        return normalizeHanKeywordNoise(text).includes(noiseStrippedKeyword);
      }

      return false;
    });
  }

  function hasKeyword(text) {
    return matchingKeywords(text).length > 0;
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

      if (node instanceof HTMLBRElement) {
        text += '\n';
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

    const nameMatches = matchingKeywords(`${nameText}\n${username}`);
    const contentMatches = matchingKeywords(text);
    const matchedKeywords = [...new Set([...nameMatches, ...contentMatches])];
    const nameSpam = nameMatches.length > 0;
    const contentSpam = contentMatches.length > 0;
    const singleEmoji = singleEmojiEnabled && isSingleEmoji(text);
    const emojiContentEmoji =
      emojiEnglishEmojiEnabled && isEmojiContentEmoji(text);
    const structuredEmojiTime =
      structuredEmojiTimeEnabled && isStructuredEmojiTime(text);
    const structuredThreeSegment =
      structuredThreeSegmentEnabled && isStructuredThreeSegment(text);
    if (
      !nameSpam &&
      !contentSpam &&
      !singleEmoji &&
      !emojiContentEmoji &&
      !structuredEmojiTime &&
      !structuredThreeSegment
    ) return;

    el.style.opacity = '0.35';
    const reasons = [];
    if (nameSpam) reasons.push('用户名或显示名称命中关键词');
    if (contentSpam) reasons.push('内容命中关键词');
    if (singleEmoji) reasons.push('单 Emoji 内容');
    if (emojiContentEmoji) reasons.push('Emoji + 内容 + Emoji');
    if (structuredEmojiTime) reasons.push('非 Emoji + Emoji + 非 Emoji + Emoji + 非 Emoji');
    if (structuredThreeSegment) reasons.push('三段式中间单字符');
    blockUser(username, el, {
      displayName: nameText,
      reason: reasons.join('；'),
      matchedKeywords,
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
