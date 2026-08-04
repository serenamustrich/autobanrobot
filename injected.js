(() => {
  if (window.__AUTOBANROBOT_INJECTED__) return;
  window.__AUTOBANROBOT_INJECTED__ = true;
  let SPAM = [];
  let remoteRules = [];
  let onlineKeywords = [];
  let keywordPolicies = [];
  // Account targets are deliberately kept separate from normal keywords.
  // A target such as @example can only match the explicitly declared scopes
  // and boundaries from the server rule pack; it can never degrade to a
  // substring match of ordinary text such as "everyone".
  let accountPolicies = [];
  let ui = {
    stamp: '扑街', processing: '处理中：屏蔽和隐藏', done: '已屏蔽和隐藏',
    skipped: '已跳过', waiting: '等待重试：屏蔽和隐藏',
    stats: '当前页面：已匹配 $1 · 已屏蔽 $2'
  };
  const DEFAULT_ACCOUNT_WHITELIST = new Set(['AAAGodofWealth']);
  let accountWhitelist = new Set(DEFAULT_ACCOUNT_WHITELIST);

  window.addEventListener('__twblocker_locale__', e => {
    ui = { ...ui, ...(e.detail || {}) };
  });

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
          const enabled = states[rule.id] ?? rule?.enabled ?? true;
          if (!enabled) return [];
          if (rule?.condition && typeof rule.condition === 'object') return [{ ...rule }];
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
    accountPolicies = Array.isArray(config?.accountPolicies)
      ? config.accountPolicies.flatMap(policy => {
          try {
            const keywordFlags = String(policy?.keywordFlags ?? '').replace(/g/gu, '');
            if (
              typeof policy?.keywordPattern !== 'string' ||
              !Array.isArray(policy?.targets)
            ) return [];
            const targets = policy.targets.flatMap(target => {
              if (
                !['content', 'username'].includes(target?.scope) ||
                typeof target?.pattern !== 'string' ||
                !/\{\{[1-9]\}\}/u.test(target.pattern)
              ) return [];
              return [{
                ...target,
                flags: String(target.flags ?? '').replace(/g/gu, '')
              }];
            });
            return targets.length
              ? [{ ...policy, keywordRegex: new RegExp(policy.keywordPattern, keywordFlags), targets }]
              : [];
          } catch (error) {
            console.warn(`Ignored invalid account policy ${policy?.id ?? ''}:`, error);
            return [];
          }
        })
      : [];
    keywordPolicies = Array.isArray(config?.keywordPolicies)
      ? config.keywordPolicies.flatMap(policy => {
          if (
            !['includes', 'token'].includes(policy?.operator) ||
            !Array.isArray(policy?.scopes) ||
            !policy.scopes.every(scope => ['content', 'username', 'displayName'].includes(scope)) ||
            !['raw', 'compact', 'noSymbols', 'hanNoise'].includes(policy?.normalization) ||
            !Number.isSafeInteger(policy?.minLength) || policy.minLength < 1 || policy.minLength > 100
          ) return [];
          try {
            return [{
              ...policy,
              keywordRegex: policy.keywordPattern
                ? new RegExp(policy.keywordPattern, String(policy.keywordFlags ?? '').replace(/g/gu, ''))
                : null,
              flags: String(policy.flags ?? '').replace(/g/gu, '')
            }];
          } catch (error) {
            console.warn(`Ignored invalid keyword policy ${policy?.id ?? ''}:`, error);
            return [];
          }
        })
      : [];
    onlineKeywords = Array.isArray(config?.keywordSets)
      ? config.keywordSets.flatMap(set => {
          if (set?.enabled === false || !Array.isArray(set?.keywords)) return [];
          return set.keywords
            .map(keyword => String(keyword ?? '').trim())
            .filter(keyword => keyword.length > 0 && keyword.length <= 100);
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

  // X on iOS can occasionally lose the sticky containing block for the home
  // timeline tabs and render them halfway through the feed. WKWebView is
  // already laid out below the physical status-bar safe area, so this must
  // never add another artificial top inset.
  let timelineTabPinScheduled = false;
  function isHomeTimelineTabList(tabList) {
    const text = String(tabList?.textContent || '').replace(/\s+/gu, ' ').trim();
    return /(?:为你推荐|For you|推薦|正在关注|Following|正在追蹤)/iu.test(text);
  }
  function pinDriftedTimelineTabs() {
    timelineTabPinScheduled = false;
    if (!window.__AUTOBANROBOT_IOS_BRIDGE__) return;
    if (!/^\/home\/?$/u.test(location.pathname)) return;
    const tabList = Array.from(document.querySelectorAll('[role="tablist"]'))
      .find(isHomeTimelineTabList);
    if (!tabList) return;
    const rect = tabList.getBoundingClientRect();
    // A normal header is already at the visible top. Wait until the compact
    // tab bar has moved far into the feed; initial X layout settles around
    // the upper quarter and must not be mistaken for a drift.
    if (rect.height > 96 || rect.top <= Math.max(300, window.innerHeight * 0.40)) return;
    tabList.dataset.autobanTimelinePinned = 'true';
    tabList.style.setProperty('position', 'fixed', 'important');
    tabList.style.setProperty('top', '0', 'important');
    tabList.style.setProperty('left', '0', 'important');
    tabList.style.setProperty('right', '0', 'important');
    tabList.style.setProperty('width', '100vw', 'important');
    tabList.style.setProperty('z-index', '2147483000', 'important');
    tabList.style.setProperty('background', 'var(--background, #fff)', 'important');
  }
  function scheduleTimelineTabPin() {
    if (timelineTabPinScheduled) return;
    timelineTabPinScheduled = true;
    requestAnimationFrame(pinDriftedTimelineTabs);
  }
  window.addEventListener('scroll', scheduleTimelineTabPin, true);
  window.addEventListener('resize', scheduleTimelineTabPin);
  setInterval(scheduleTimelineTabPin, 1000);

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
        statsEl.textContent = ui.stats.replace('$1', pageStats.matchedAccounts.size).replace('$2', pageStats.successfulBlockCount);
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
    label.textContent = ui.stamp;
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
    el.title = `[${ui.processing}] @${username}`;
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
        `[${ui.done}] @${result.username}`,
        true
      );
      toast(`✅ ${ui.done} @${result.username}`, true, stats);
      return;
    }

    if (result.state === 'skipped') {
      exemptAccounts.add(result.username);
      if (!samePage) return;
      updateMatchedElements(
        result.username,
        '1',
        `[${ui.skipped}：${result.message}] @${result.username}`
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
      `[${ui.waiting}] @${result.username}`,
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

  function isStructuredFourSegmentCodeEmoji(text) {
    const lines = text
      .split(/\r?\n/u)
      .map(line => line.trim())
      .filter(Boolean);
    if (lines.length < 3 || lines.length > 6) return false;

    const emojiLines = lines.filter(isSingleEmoji);
    if (emojiLines.length !== 1) return false;

    // This targets the observed lure format such as "31 / 78dv / 🍌 / rz53".
    // Emoji position is intentionally unconstrained. Short code-like lines
    // plus at least one digit distinguish the lure from normal multi-line text.
    const codeLines = lines.filter(line => !isSingleEmoji(line));
    return codeLines.length >= 2 &&
      codeLines.every(line => /^[A-Za-z0-9._-]{1,16}$/u.test(line)) &&
      codeLines.some(line => /\d/u.test(line));
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

  function normalizedLines(text) {
    return String(text ?? '')
      .split(/\r?\n/u)
      .map(line => line.trim())
      .filter(Boolean);
  }

  function matchesNumberConstraint(value, condition) {
    if (Number.isInteger(condition.equals) && value !== condition.equals) return false;
    if (Number.isInteger(condition.min) && value < condition.min) return false;
    if (Number.isInteger(condition.max) && value > condition.max) return false;
    return Number.isInteger(condition.equals) ||
      Number.isInteger(condition.min) ||
      Number.isInteger(condition.max);
  }

  function conditionTarget(value, normalization) {
    if (normalization === 'compact') return normalizeForMatch(value);
    if (normalization === 'noSymbols') return normalizeWithoutSymbolNoise(value);
    if (normalization === 'hanNoise') return normalizeHanKeywordNoise(value);
    return String(value ?? '');
  }

  // The engine evaluates a JSON rule tree. New rules change this tree on the
  // server; no remote JavaScript is evaluated in the extension.
  function matchesRuleCondition(condition, context, depth = 0) {
    if (!condition || typeof condition !== 'object' || depth > 12) return false;
    if (Array.isArray(condition.all)) {
      return condition.all.length > 0 &&
        condition.all.every(item => matchesRuleCondition(item, context, depth + 1));
    }
    if (Array.isArray(condition.any)) {
      return condition.any.length > 0 &&
        condition.any.some(item => matchesRuleCondition(item, context, depth + 1));
    }
    if (condition.not && typeof condition.not === 'object') {
      return !matchesRuleCondition(condition.not, context, depth + 1);
    }

    const type = condition.type;
    if (type === 'singleEmoji') return isSingleEmoji(context.value);
    if (type === 'graphemeCount') {
      return matchesNumberConstraint(
        [...emojiSegmenter.segment(String(context.value ?? ''))].length,
        condition
      );
    }
    if (type === 'lineCount') return matchesNumberConstraint(context.lines.length, condition);
    if (type === 'lineAt') {
      if (!Number.isInteger(condition.index) || condition.index < 0) return false;
      const line = context.lines[condition.index];
      return line !== undefined && matchesRuleCondition(
        condition.condition,
        { ...context, value: line },
        depth + 1
      );
    }
    if (type === 'anyLine' || type === 'countLines' || type === 'allLines') {
      const selected = context.lines.filter(line =>
        !condition.where || matchesRuleCondition(
          condition.where,
          { ...context, value: line },
          depth + 1
        )
      );
      if (type === 'anyLine') {
        return selected.some(line => matchesRuleCondition(
          condition.condition,
          { ...context, value: line },
          depth + 1
        ));
      }
      if (type === 'countLines') {
        const count = selected.filter(line => matchesRuleCondition(
          condition.condition,
          { ...context, value: line },
          depth + 1
        )).length;
        return matchesNumberConstraint(count, condition);
      }
      return selected.length > 0 && selected.every(line => matchesRuleCondition(
        condition.condition,
        { ...context, value: line },
        depth + 1
      ));
    }
    if (type === 'regex') {
      try {
        const flags = String(condition.flags ?? '').replace(/g/gu, '');
        const regex = new RegExp(String(condition.pattern ?? ''), flags);
        return regex.test(conditionTarget(context.value, condition.normalization));
      } catch (_) {
        return false;
      }
    }
    return false;
  }

  function matchingAccountPolicies(text, keyword, scope) {
    return accountPolicies.find(policy => {
      policy.keywordRegex.lastIndex = 0;
      const match = policy.keywordRegex.exec(keyword);
      if (!match) return false;
      return policy.targets.some(target => {
        if (target.scope !== scope) return false;
        try {
          let missingCapture = false;
          const pattern = target.pattern.replace(/\{\{([1-9])\}\}/gu, (_, group) => {
            const value = match[Number(group)];
            if (!value) {
              missingCapture = true;
              return '(?!)';
            }
            return String(value).replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
          });
          if (missingCapture) return false;
          return new RegExp(pattern, target.flags).test(
            conditionTarget(text, target.normalization)
          );
        } catch (_) {
          return false;
        }
      });
    });
  }

  function matchingKeywordPolicies(text, keyword, scope) {
    return keywordPolicies.some(policy => {
      if (!policy.scopes.includes(scope)) return false;
      if (policy.keywordRegex) {
        policy.keywordRegex.lastIndex = 0;
        if (!policy.keywordRegex.test(keyword)) return false;
      }
      const target = conditionTarget(keyword, policy.normalization);
      if (target.length < policy.minLength) return false;
      const value = conditionTarget(text, policy.normalization);
      if (policy.operator === 'includes') return value.includes(target);
      try {
        const escaped = String(keyword).replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
        return new RegExp(
          `(^|[^\\p{L}\\p{N}_])${escaped}(?=$|[^\\p{L}\\p{N}_])`,
          policy.flags
        ).test(value);
      } catch (_) {
        return false;
      }
    });
  }

  function matchingRemoteRules(text, scope = 'content', context = {}) {
    return remoteRules.filter(rule => {
      if ((rule.scope ?? 'content') !== scope) return false;
      if (rule.requiresDefaultAvatar === true && !context.defaultAvatar) {
        return false;
      }
      if (rule.condition) {
        return matchesRuleCondition(rule.condition, {
          value: text,
          lines: normalizedLines(text)
        });
      }
      // Backward compatibility only: servers on an older rule package can
      // still serve historical matcher names while the new DSL rolls out.
      if (rule.matcher) {
        const matcher = {
          singleEmoji: isSingleEmoji,
          structuredEmojiTime: isStructuredEmojiTime,
          structuredThreeSegment: isStructuredThreeSegment,
          structuredFourSegmentCodeEmoji: isStructuredFourSegmentCodeEmoji
        }[rule.matcher];
        return typeof matcher === 'function' && matcher(text);
      }
      if (typeof rule.pattern !== 'string' || !rule.pattern.trim()) return false;
      const target = rule.normalization === 'compact'
        ? normalizeForMatch(text)
        : rule.normalization === 'noSymbols'
          ? normalizeWithoutSymbolNoise(text)
          : text;
      rule.regex.lastIndex = 0;
      return rule.regex.test(target);
    });
  }

  function matchingKeywords(text, scope = 'content') {
    return [...new Set([...SPAM, ...onlineKeywords])].filter(keyword => {
      const normalizedKeyword = normalizeForMatch(keyword);
      if (!normalizedKeyword) return false;
      if (/^@[A-Za-z0-9_]{1,15}$/u.test(normalizedKeyword)) {
        return Boolean(matchingAccountPolicies(text, keyword, scope));
      }
      return matchingKeywordPolicies(text, keyword, scope);
    });
  }

  function hasKeyword(text) {
    return matchingKeywords(text).length > 0;
  }

  function matchingAccountFields(displayName, username) {
    return matchingIdentityKeywords(displayName, username).matches;
  }

  function matchingIdentityKeywords(displayName, username) {
    const displayNameMatches = matchingKeywords(displayName, 'displayName');
    const accountMatches = matchingKeywords(username, 'username');
    return {
      displayNameMatches,
      accountMatches,
      matches: [...new Set([...displayNameMatches, ...accountMatches])]
    };
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

      const identityMatches = matchingIdentityKeywords(displayName, username);
      const nameMatches = identityMatches.matches;
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
      if (identityMatches.accountMatches.length) reasons.push('账号 ID 命中关键词');
      if (identityMatches.displayNameMatches.length) reasons.push('用户名命中关键词');
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

    const identityMatches = matchingIdentityKeywords(displayName, username);
    const nameMatches = identityMatches.matches;
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
    if (identityMatches.accountMatches.length) reasons.push('账号 ID 命中关键词');
    if (identityMatches.displayNameMatches.length) reasons.push('用户名命中关键词');
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
      matchingAccountFields(identity.displayName, identity.username).length > 0
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

    const identityMatches = matchingIdentityKeywords(nameText, username);
    const nameMatches = identityMatches.matches;
    const contentMatches = matchingKeywords(text, 'content');
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
    if (identityMatches.accountMatches.length) reasons.push('账号 ID 命中关键词');
    if (identityMatches.displayNameMatches.length) reasons.push('用户名命中关键词');
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
