(() => {
  let SPAM = [
    '免费过夜',
    '主页联系',
    '主页匹配',
    '免费破处',
    '同城',
    '上门',
    '刷了半天',
    '看主页',
    '点我头像',
    '处男免费',
    '处男无偿',
    '体制内老师',
    '她太涩了',
    'sao货'
  ];

  window.addEventListener('__twblocker_keywords__', e => {
    SPAM = e.detail?.kws ?? SPAM;
    processed = new WeakSet();
    scanAll();
  });
  let bearer = null;
  const blockQueue = [];
  const blocked = new Set();
  let processed = new WeakSet();
  const _f = window.fetch.bind(window);

  function toast(msg, ok = true) {
    const show = () => {
      const el = document.createElement('div');
      el.style.cssText = `
        position:fixed;bottom:24px;right:24px;z-index:2147483647;
        background:${ok ? '#1d9bf0' : '#e0245e'};color:#fff;
        padding:10px 16px;border-radius:10px;font-size:13px;
        font-family:-apple-system,sans-serif;
        box-shadow:0 2px 12px rgba(0,0,0,.5);max-width:300px;word-break:break-all;`;
      el.textContent = msg;
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
      const pending = blockQueue.splice(0);
      pending.forEach(({ username, el }) => doBlock(username, el));
    }
  });

  async function doBlock(username, el) {
    if (blocked.has(username)) return;
    blocked.add(username);

    const csrfRaw = document.cookie.match(/ct0=([^;]+)/)?.[1];
    if (!csrfRaw) {
      toast('❌ 无 CSRF token', false);
      blocked.delete(username);
      if (el) el.style.opacity = '1';
      return;
    }
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
        toast(`✅ 已屏蔽 @${username}`);
        if (el) { el.style.opacity = '0.12'; el.title = `[已屏蔽] @${username}`; }
        window.dispatchEvent(new CustomEvent('__twblocker_blocked__'));
      } else {
        const body = await res.text().catch(() => '');
        toast(`❌ @${username} HTTP ${res.status}: ${body.slice(0, 60)}`, false);
        blocked.delete(username);
        if (el) el.style.opacity = '1';
      }
    } catch (e) {
      toast(`❌ 异常: ${e.message}`, false);
      blocked.delete(username);
      if (el) el.style.opacity = '1';
    }
  }

  function blockUser(username, el) {
    if (blocked.has(username)) return;
    if (!bearer) {
      blockQueue.push({ username, el });
    } else {
      doBlock(username, el);
    }
  }

  function isSingleEmoji(text) {
    const s = text.replace(/\s/g, '');
    if (!s) return false;
    return /^(?:\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?(?:\u{200D}\p{Extended_Pictographic}\u{FE0F}?(?:\p{Emoji_Modifier})?)*|[\u{1F1E0}-\u{1F1FF}]{2}|[0-9#*]\u{FE0F}?\u{20E3})$/u.test(s);
  }

  function hasKeyword(t) { return SPAM.some(k => t.includes(k)); }

  function processTweet(el) {
    if (processed.has(el)) return;
    processed.add(el);

    const text = el.querySelector('[data-testid="tweetText"]')?.textContent ?? '';
    const nameBlock = el.querySelector('[data-testid="User-Name"]');
    if (!nameBlock) return;

    const link = nameBlock.querySelector('a[href^="/"]');
    const username = link?.getAttribute('href')?.replace(/^\//, '') ?? '';
    if (!username || blocked.has(username)) return;

    const nameText = nameBlock.textContent.replace(`@${username}`, '').trim();
    const nameSpam = hasKeyword(nameText) || hasKeyword(username);
    const contentSpam = hasKeyword(text);
    const singleEmoji = isSingleEmoji(text);
    if (!nameSpam && !contentSpam && !singleEmoji) return;

    el.style.opacity = '0.35';
    blockUser(username, el);
  }

  function scanAll() {
    ['[data-testid="tweet"]', 'article[role="article"]']
      .forEach(sel => document.querySelectorAll(sel).forEach(processTweet));
  }

  new MutationObserver(scanAll).observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(scanAll, 2000);
})();
