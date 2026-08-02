(() => {
  if (window.__AUTOBANROBOT_INJECTED__) return;
  window.__AUTOBANROBOT_INJECTED__ = true;
  let SPAM = [];
  let remoteRules = [];
  const DEFAULT_ACCOUNT_WHITELIST = new Set(['AAAGodofWealth']);
  let accountWhitelist = new Set(DEFAULT_ACCOUNT_WHITELIST);

  window.addEventListener('__twblocker_keywords__', e => {
    SPAM = e.detail?.kws ?? SPAM;
    processedSignatures = new WeakMap();
    scanAll();
  });
  window.addEventListener('__twblocker_rules__', e => {
    const config = e.detail?.config;
    const states = e.detail?.states ?? {};
    remoteRules = Array.isArray(config?.rules)
      ? config.rules.flatMap(rule => {
          if (rule?.enabled === false || states[rule.id] === false) return [];
          if (typeof rule.matcher === 'string') return [{ ...rule }];
          try {
            const flags = String(rule.flags ?? '').replace(/g/g, '');
            return [{ ...rule, regex: new RegExp(rule.pattern, flags) }];
          } catch (error) {
            console.warn(`Ignored invalid remote rule ${rule?.id ?? ''}:`, error);
            return [];
          }
        })
      : [];
    processedSignatures = new WeakMap();
    scanAll();
  });
  window.addEventListener('__twblocker_whitelist__', e => {
    accountWhitelist = new Set([
      ...DEFAULT_ACCOUNT_WHITELIST,
      ...(Array.isArray(e.detail?.accounts) ? e.detail.accounts : [])
    ].map(value => String(value).trim().toLowerCase()).filter(Boolean));
    scanAll();
  });
  const blocked = new Set();
  const exemptAccounts = new Set();
  const queuedAccounts = new Set();
  const matchedElements = new Map();
  const stampAnchors = new Map();
  let processedSignatures = new WeakMap();
  let scanScheduled = false;
  let lastEngagementDiagnostic = '';
  let pageStats = createPageStats();
  const NOTIFICATION_ACTION_PATTERN = new RegExp([
    '点赞了你的(?:\\s*\\d+\\s*个)?(?:帖子|回复)',
    '喜欢了你的(?:\\s*\\d+\\s*个)?(?:帖子|回复)',
    '轉發了你的(?:\\s*\\d+\\s*個)?(?:貼文|帖子|回覆)',
    '转发了你的(?:\\s*\\d+\\s*个)?(?:帖子|回复)',
    '轉貼了你的(?:\\s*\\d+\\s*個)?(?:貼文|帖子|回覆)',
    '转帖了你的(?:\\s*\\d+\\s*个)?(?:帖子|回复)',
    'liked (?:\\d+ of )?your (?:post|posts|reply|replies)',
    'reposted (?:\\d+ of )?your (?:post|posts|reply|replies)',
    'retweeted (?:\\d+ of )?your (?:post|posts|reply|replies)'
  ].join('|'), 'iu');
  const ENGAGEMENT_ROUTE_PATTERN = /\/(?:i|[A-Za-z0-9_]{1,15})\/status\/\d+\/(?:retweets|retweets_with_comments|quotes|likes)\/?$/u;
  const ENGAGEMENT_TITLE_PATTERN = /(?:帖子活动|貼文活動|Post engagements)/iu;
  const ENGAGEMENT_TAB_PATTERNS = [
    /(?:引用|Quotes?)/iu,
    /(?:转帖|轉貼|转发|轉發|Reposts?|Retweets?)/iu,
    /(?:喜欢|喜歡|Likes?)/iu
  ];

  function positionStamp(anchor, stamp) {
    if (!anchor?.isConnected || !stamp?.isConnected) {
      stampAnchors.delete(anchor);
      stamp?.remove();
      return;
    }
    const rect = anchor.getBoundingClientRect();
    const size = 56;
    stamp.style.left = `${Math.max(8, Math.min(window.innerWidth - size - 8, rect.right - size - 12))}px`;
    stamp.style.top = `${Math.max(8, rect.top + (rect.height - size) / 2)}px`;
  }

  const repositionStamps = () => stampAnchors.forEach((stamp, anchor) => positionStamp(anchor, stamp));
  window.addEventListener('scroll', repositionStamps, true);
  window.addEventListener('resize', repositionStamps);

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
    if (window.__AUTOBANROBOT_MOBILE__) return;
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

  function applyStamp(el) {
    if (!el?.isConnected || stampAnchors.has(el)) {
      if (el?.isConnected) positionStamp(el, stampAnchors.get(el));
      return;
    }
    const stamp = document.createElement('span');
    stamp.dataset.autobanStamp = 'true';
    stamp.setAttribute('aria-hidden', 'true');
    stamp.style.cssText = `
      position:fixed;left:0;top:0;z-index:2147483646;
      width:56px;height:56px;border-radius:50%;
      display:flex;align-items:center;justify-content:center;
      box-sizing:border-box;background:rgba(255,246,249,.72);
      color:#d97896;font-size:16px;font-weight:800;letter-spacing:1px;
      line-height:1;transform:rotate(-30deg);pointer-events:none;
      font-family:-apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif;
      text-shadow:0 1px 0 rgba(255,255,255,.7);`;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 64 64');
    svg.setAttribute('aria-hidden', 'true');
    svg.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;overflow:visible;';
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    const filter = document.createElementNS('http://www.w3.org/2000/svg', 'filter');
    const filterId = `autoban-ink-${Math.random().toString(36).slice(2)}`;
    filter.setAttribute('id', filterId);
    filter.setAttribute('x', '-15%');
    filter.setAttribute('y', '-15%');
    filter.setAttribute('width', '130%');
    filter.setAttribute('height', '130%');
    const turbulence = document.createElementNS('http://www.w3.org/2000/svg', 'feTurbulence');
    turbulence.setAttribute('type', 'fractalNoise');
    turbulence.setAttribute('baseFrequency', '.75');
    turbulence.setAttribute('numOctaves', '2');
    turbulence.setAttribute('seed', String(Math.floor(Math.random() * 1000)));
    turbulence.setAttribute('result', 'inkNoise');
    const displacement = document.createElementNS('http://www.w3.org/2000/svg', 'feDisplacementMap');
    displacement.setAttribute('in', 'SourceGraphic');
    displacement.setAttribute('in2', 'inkNoise');
    displacement.setAttribute('scale', '.7');
    filter.append(turbulence, displacement);
    defs.appendChild(filter);
    svg.appendChild(defs);
    const circle = (radius, width, dash, opacity) => {
      const node = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      node.setAttribute('cx', '32');
      node.setAttribute('cy', '32');
      node.setAttribute('r', String(radius));
      node.setAttribute('fill', 'none');
      node.setAttribute('stroke', '#d97896');
      node.setAttribute('stroke-width', String(width));
      node.setAttribute('stroke-opacity', String(opacity));
      node.setAttribute('stroke-linecap', 'round');
      node.setAttribute('stroke-dasharray', dash);
      return node;
    };
    svg.appendChild(circle(28, 2.4, '1.2 2.7 3.4 1.6', .62));
    svg.appendChild(circle(24.5, 1.5, '4.8 2.1 1.1 3.8', .42));
    for (let index = 0; index < 24; index += 1) {
      const angle = Math.random() * Math.PI * 2;
      const radius = 26 + (Math.random() * 4 - 2);
      const mark = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      mark.setAttribute('cx', String(32 + Math.cos(angle) * radius));
      mark.setAttribute('cy', String(32 + Math.sin(angle) * radius));
      mark.setAttribute('r', String(.35 + Math.random() * .8));
      mark.setAttribute('fill', '#d97896');
      mark.setAttribute('fill-opacity', String(.24 + Math.random() * .35));
      svg.appendChild(mark);
    }
    const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    label.setAttribute('x', '32');
    label.setAttribute('y', '38');
    label.setAttribute('text-anchor', 'middle');
    label.setAttribute('fill', '#d97896');
    label.setAttribute('fill-opacity', '.84');
    label.setAttribute('stroke', '#d97896');
    label.setAttribute('stroke-opacity', '.16');
    label.setAttribute('stroke-width', '.35');
    label.setAttribute('font-family', '-apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif');
    label.setAttribute('font-size', '18');
    label.setAttribute('font-weight', '800');
    label.setAttribute('letter-spacing', '1');
    label.setAttribute('filter', `url(#${filterId})`);
    label.textContent = '扑街';
    svg.appendChild(label);
    stamp.appendChild(svg);
    document.body.appendChild(stamp);
    stampAnchors.set(el, stamp);
    positionStamp(el, stamp);
  }

  function removeStamp(el) {
    const stamp = stampAnchors.get(el);
    if (!stamp) return;
    stampAnchors.delete(el);
    stamp.remove();
  }

  function updateMatchedElements(username, opacity, title = '', stamped = false) {
    matchedElements.get(username)?.forEach(el => {
      if (!el?.isConnected) return;
      el.style.opacity = opacity;
      el.title = title;
      if (stamped) applyStamp(el);
      else removeStamp(el);
    });
  }

  function blockUser(username, el, match) {
    const stats = syncPageStats();
    stats.matchedAccounts.add(username);
    if (!matchedElements.has(username)) matchedElements.set(username, new Set());
    matchedElements.get(username).add(el);
    el.style.opacity = '0.35';
    el.title = `[处理中：屏蔽和隐藏] @${username}`;
    applyStamp(el);
    if (
      accountWhitelist.has(username.toLowerCase()) ||
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
    const samePage = result.historical === true || result.pageKey === currentPageKey();

    if (result.state === 'success') {
      blocked.add(result.username);
      if (!samePage) return;
      const stats = syncPageStats();
      stats.successfulBlockCount++;
      updateMatchedElements(
        result.username,
        '0.12',
        `[已屏蔽和隐藏] @${result.username}`,
        true
      );
      toast(`✅ 已屏蔽和隐藏 @${result.username}`, true, stats);
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
        `[已屏蔽和隐藏] @${result.username}`,
        true
      );
      toast(`ℹ️ @${result.username} 已经处于屏蔽和隐藏状态`);
      return;
    }

    if (!samePage) return;
    updateMatchedElements(
      result.username,
      '0.35',
      `[等待重试：屏蔽和隐藏] @${result.username}`,
      true
    );
  });

  function isSingleEmoji(text) {
    const s = text.replace(/\s/g, '');
    if (!s) return false;
    return /^(?:\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?(?:\u{200D}\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?)*|[\u{1F1E0}-\u{1F1FF}]{2}|[0-9#*]\u{FE0F}?\u{20E3})$/u.test(s);
  }

  const emojiSegmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

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

  function matchingRemoteRules(text, scope = 'content', context = {}) {
    return remoteRules.filter(rule => {
      if ((rule.scope ?? 'content') !== scope) return false;
      if (rule.requiresDefaultAvatar === true && !context.defaultAvatar) {
        return false;
      }
      if (rule.matcher) {
        const matcher = {
          singleEmoji: isSingleEmoji,
          structuredEmojiTime: isStructuredEmojiTime,
          structuredThreeSegment: isStructuredThreeSegment
        }[rule.matcher];
        return typeof matcher === 'function' && matcher(text);
      }
      const target = rule.normalization === 'compact'
        ? normalizeForMatch(text)
        : rule.normalization === 'noSymbols'
          ? normalizeWithoutSymbolNoise(text)
          : text;
      rule.regex.lastIndex = 0;
      return rule.regex.test(target);
    });
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

  function extractAccountIdentity(nameBlock) {
    const links = [...nameBlock.querySelectorAll('a[href^="/"]')];
    const candidates = links.flatMap(link => {
      const href = link.getAttribute('href') ?? '';
      const match = /^\/([A-Za-z0-9_]{1,15})\/?$/u.exec(href);
      return match ? [{ link, username: match[1] }] : [];
    });
    const usernameCandidate = candidates.find(({ link, username }) =>
      link.textContent.trim() === `@${username}`
    ) ?? candidates[0];
    if (!usernameCandidate) return null;

    const { username } = usernameCandidate;
    const displayNameLink = candidates.find(({ link, username: candidate }) =>
      candidate.toLocaleLowerCase() === username.toLocaleLowerCase() &&
      link.textContent.trim() !== `@${candidate}` &&
      !link.querySelector('time')
    )?.link;
    return {
      username,
      displayName: displayNameLink?.textContent.trim() ?? ''
    };
  }

  function notificationText(el) {
    return String(el?.innerText || el?.textContent || '')
      .replace(/\s+/gu, ' ')
      .trim();
  }

  function isLikeOrRepostNotification(el) {
    if (!/^\/notifications(?:\/|$)/u.test(location.pathname)) return false;
    return NOTIFICATION_ACTION_PATTERN.test(notificationText(el));
  }

  function actorDisplayName(link, username) {
    const values = [
      link.textContent,
      link.getAttribute('aria-label'),
      link.getAttribute('title'),
      link.querySelector('img')?.getAttribute('alt')
    ];
    return values
      .map(value => String(value || '').replace(/\s+/gu, ' ').trim())
      .find(value => value && value.toLocaleLowerCase() !== `@${username}`.toLocaleLowerCase()) || '';
  }

  function extractNotificationActors(el) {
    const actors = new Map();
    [...el.querySelectorAll('a[href]')].forEach(link => {
      if (link.closest('[data-testid="tweet"], article[role="article"]')) return;
      let path = '';
      try {
        path = new URL(link.getAttribute('href') || '', location.origin).pathname;
      } catch (_) {
        return;
      }
      const match = /^\/([A-Za-z0-9_]{1,15})\/?$/u.exec(path);
      if (!match) return;
      const username = match[1];
      const key = username.toLocaleLowerCase();
      const displayName = actorDisplayName(link, username);
      const existing = actors.get(key);
      if (!existing || (!existing.displayName && displayName)) {
        actors.set(key, { username, displayName });
      }
    });
    return [...actors.values()];
  }

  function processNotification(el) {
    if (!isLikeOrRepostNotification(el)) return;
    const actors = extractNotificationActors(el);
    if (!actors.length) return;
    const actionText = notificationText(el);
    const signature = actors
      .map(actor => `${actor.username}\u0000${actor.displayName}`)
      .sort()
      .join('\u0001') + `\u0002${actionText}`;
    if (processedSignatures.get(el) === signature) return;
    processedSignatures.set(el, signature);

    actors.forEach(({ username, displayName }) => {
      if (accountWhitelist.has(username.toLowerCase()) || exemptAccounts.has(username)) return;
      if (blocked.has(username)) {
        el.style.opacity = '0.12';
        el.title = `[已屏蔽和隐藏] @${username}`;
        applyStamp(el);
        return;
      }

      const nameMatches = matchingKeywords(`${displayName}\n${username}`);
      const ruleContext = {
        defaultAvatar: Boolean(
          [...el.querySelectorAll(`a[href="/${username}"] img, a[href$="/${username}"] img`)]
            .some(node => String(node.getAttribute?.('src') || '').includes('default_profile'))
        )
      };
      const remoteMatches = [
        ...matchingRemoteRules(username, 'username', ruleContext),
        ...matchingRemoteRules(displayName, 'displayName', ruleContext)
      ];
      if (!nameMatches.length && !remoteMatches.length) return;

      const matchedKeywords = [...new Set(nameMatches)];
      const reasons = ['点赞或转发通知账号命中规则'];
      if (nameMatches.length) reasons.push('用户名或显示名称命中关键词');
      reasons.push(...remoteMatches.map(rule => rule.name));
      blockUser(username, el, {
        displayName,
        reason: reasons.join('；'),
        matchedKeywords,
        content: actionText.slice(0, 160)
      });
    });
  }

  function isEngagementActivityPage() {
    if (ENGAGEMENT_ROUTE_PATTERN.test(location.pathname)) return true;
    const pageText = notificationText(document.body);
    if (!ENGAGEMENT_TITLE_PATTERN.test(pageText)) return false;
    return ENGAGEMENT_TAB_PATTERNS.filter(pattern => pattern.test(pageText)).length >= 2;
  }

  function processEngagementActor(el) {
    const userCell = el.matches?.('[data-testid="UserCell"]')
      ? el
      : el.querySelector?.('[data-testid="UserCell"]') || el;
    const nameBlock = userCell.querySelector('[data-testid="User-Name"]') || userCell;
    const identity = extractEngagementIdentity(nameBlock);
    if (!identity) return;
    const { username, displayName } = identity;
    const signature = `engagement\u0000${username}\u0000${displayName}`;
    if (processedSignatures.get(el) === signature) return;
    processedSignatures.set(el, signature);

    if (accountWhitelist.has(username.toLowerCase())) {
      exemptAccounts.add(username);
      el.style.opacity = '1';
      el.title = `[已跳过：白名单账号] @${username}`;
      removeStamp(el);
      return;
    }
    if (exemptAccounts.has(username)) {
      el.style.opacity = '1';
      el.title = `[已跳过：你正在关注该账号] @${username}`;
      removeStamp(el);
      return;
    }
    if (blocked.has(username)) {
      el.style.opacity = '0.12';
      el.title = `[已屏蔽和隐藏] @${username}`;
      applyStamp(el);
      return;
    }

    const nameMatches = matchingKeywords(`${displayName}\n${username}`);
    const ruleContext = {
      defaultAvatar: Boolean(userCell.querySelector(
        'img[src*="default_profile_images"], img[src*="default_profile"]'
      ))
    };
    const remoteMatches = [
      ...matchingRemoteRules(username, 'username', ruleContext),
      ...matchingRemoteRules(displayName, 'displayName', ruleContext)
    ];
    if (!nameMatches.length && !remoteMatches.length) {
      el.style.opacity = '1';
      removeStamp(el);
      return;
    }

    const reasons = ['帖子活动账号命中规则'];
    if (nameMatches.length) reasons.push('用户名或显示名称命中关键词');
    reasons.push(...remoteMatches.map(rule => rule.name));
    blockUser(username, el, {
      displayName,
      reason: reasons.join('；'),
      matchedKeywords: [...new Set(nameMatches)],
      content: `${displayName} @${username}`.trim()
    });
  }

  function extractEngagementIdentity(row) {
    const identity = extractAccountIdentity(row);
    if (!identity) return null;
    const rowText = notificationText(row);
    const marker = `@${identity.username}`;
    const markerIndex = rowText.toLocaleLowerCase().indexOf(marker.toLocaleLowerCase());
    const visibleDisplayName = markerIndex > 0
      ? rowText.slice(0, markerIndex).replace(/\s+/gu, ' ').trim()
      : '';
    return {
      ...identity,
      displayName: visibleDisplayName || identity.displayName
    };
  }

  function engagementActorRows() {
    const rows = new Set(document.querySelectorAll('[data-testid="UserCell"]'));
    document.querySelectorAll('a[href^="/"]').forEach(link => {
      const href = link.getAttribute('href') || '';
      const match = /^\/([A-Za-z0-9_]{1,15})\/?$/u.exec(href);
      if (!match) return;
      const row = link.closest(
        '[data-testid="UserCell"], [data-testid="cellInnerDiv"], button, [role="button"]'
      );
      if (row) rows.add(row);
    });
    return rows;
  }

  function reportEngagementDiagnostic(rows) {
    if (!window.__AUTOBANROBOT_MOBILE__ || !window.AutoBanBridge?.reportScanDiagnostic) return;
    const identities = [...rows]
      .map(row => extractEngagementIdentity(row.querySelector('[data-testid="User-Name"]') || row))
      .filter(Boolean);
    const matched = identities.filter(identity =>
      hasKeyword(`${identity.displayName}\n${identity.username}`)
    );
    const message = [
      `rows=${rows.size}`,
      `userCells=${document.querySelectorAll('[data-testid="UserCell"]').length}`,
      `profileLinks=${document.querySelectorAll('a[href^="/"]').length}`,
      `identities=${identities.length}`,
      `matched=${matched.length}`
    ].join(' ');
    if (message === lastEngagementDiagnostic) return;
    lastEngagementDiagnostic = message;
    try { window.AutoBanBridge.reportScanDiagnostic(message); } catch (_) {}
  }

  function processTweet(el) {
    const tweetText = el.querySelector('[data-testid="tweetText"]');
    const text = extractTweetText(tweetText);
    const nameBlock = el.querySelector('[data-testid="User-Name"]');
    if (!nameBlock) return;

    const identity = extractAccountIdentity(nameBlock);
    if (!identity) return;
    const { username, displayName: nameText } = identity;
    const signature = `${username}\u0000${nameText}\u0000${text}`;
    if (processedSignatures.get(el) === signature) return;
    processedSignatures.set(el, signature);

    if (blocked.has(username)) {
      el.style.opacity = '0.12';
      el.title = `[已屏蔽和隐藏] @${username}`;
      applyStamp(el);
      return;
    }
    if (exemptAccounts.has(username)) {
      el.style.opacity = '1';
      el.title = `[已跳过：你正在关注该账号] @${username}`;
      removeStamp(el);
      return;
    }
    if (accountWhitelist.has(username.toLowerCase())) {
      exemptAccounts.add(username);
      el.style.opacity = '1';
      el.title = '[已跳过：白名单账号] @' + username;
      removeStamp(el);
      return;
    }

    const nameMatches = matchingKeywords(`${nameText}\n${username}`);
    const contentMatches = matchingKeywords(text);
    const matchedKeywords = [...new Set([...nameMatches, ...contentMatches])];
    const nameSpam = nameMatches.length > 0;
    const contentSpam = contentMatches.length > 0;
    const ruleContext = {
      defaultAvatar: Boolean(el.querySelector(
        'img[src*="default_profile_images"], img[src*="default_profile"]'
      ))
    };
    const remoteMatches = [
      ...matchingRemoteRules(text, 'content', ruleContext),
      ...matchingRemoteRules(username, 'username', ruleContext),
      ...matchingRemoteRules(nameText, 'displayName', ruleContext)
    ];
    if (
      !nameSpam &&
      !contentSpam &&
      remoteMatches.length === 0
    ) {
      el.style.opacity = '1';
      removeStamp(el);
      return;
    }

    el.style.opacity = '0.35';
    el.title = `[处理中：屏蔽和隐藏] @${username}`;
    applyStamp(el);
    const reasons = [];
    if (nameSpam) reasons.push('用户名或显示名称命中关键词');
    if (contentSpam) reasons.push('内容命中关键词');
    reasons.push(...remoteMatches.map(rule => rule.name));
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
    if (/^\/notifications(?:\/|$)/u.test(location.pathname)) {
      document.querySelectorAll('[data-testid="cellInnerDiv"]')
        .forEach(processNotification);
    }
    if (isEngagementActivityPage()) {
      const rows = engagementActorRows();
      reportEngagementDiagnostic(rows);
      rows.forEach(processEngagementActor);
    }
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
