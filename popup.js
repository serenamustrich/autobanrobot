chrome.storage.local.get([
  'blockCount',
  'keywords',
  'emojiEnglishEmojiEnabled',
  'blockHistory',
  'pendingBlockQueue'
], r => {
  document.getElementById('count').textContent = r.blockCount ?? 0;
  document.getElementById('keywords').value =
    (Array.isArray(r.keywords) ? r.keywords : []).join('\n');
  document.getElementById('emojiEnglishEmojiEnabled').checked =
    r.emojiEnglishEmojiEnabled !== false;
  document.getElementById('queueCount').textContent =
    Array.isArray(r.pendingBlockQueue) ? r.pendingBlockQueue.length : 0;
  renderBlockHistory(r.blockHistory);
});

document.getElementById('save').addEventListener('click', () => {
  const kws = document.getElementById('keywords').value
    .split('\n').map(s => s.trim()).filter(Boolean);

  const emojiEnglishEmojiEnabled =
    document.getElementById('emojiEnglishEmojiEnabled').checked;

  chrome.storage.local.set({ keywords: kws, emojiEnglishEmojiEnabled }, () => {
    const saved = document.getElementById('saved');
    saved.style.display = 'block';
    setTimeout(() => { saved.style.display = 'none'; }, 2000);
  });
});

function renderBlockHistory(value) {
  const history = Array.isArray(value) ? value : [];
  const container = document.getElementById('blockHistory');
  container.replaceChildren();

  if (!history.length) {
    const empty = document.createElement('div');
    empty.className = 'history-empty';
    empty.textContent = '暂无已确认屏蔽记录';
    container.appendChild(empty);
    return;
  }

  history.forEach(record => {
    const item = document.createElement('div');
    item.className = 'history-item';

    const user = document.createElement('a');
    user.className = 'history-user';
    user.href = `https://x.com/${encodeURIComponent(record.username)}`;
    user.target = '_blank';
    user.rel = 'noreferrer';
    user.textContent = record.displayName
      ? `${record.displayName} (@${record.username})`
      : `@${record.username}`;

    const meta = document.createElement('div');
    meta.className = 'history-meta';
    const time = record.blockedAt
      ? new Date(record.blockedAt).toLocaleString()
      : '时间未知';
    meta.textContent = `${time} · ${record.reason || '规则命中'}`;

    item.append(user, meta);
    if (record.content) {
      const content = document.createElement('div');
      content.className = 'history-content';
      content.title = record.content;
      content.textContent = record.content;
      item.appendChild(content);
    }
    container.appendChild(item);
  });
}

document.getElementById('clearHistory').addEventListener('click', () => {
  chrome.storage.local.set({ blockHistory: [] }, () => {
    renderBlockHistory([]);
  });
});

chrome.storage.onChanged.addListener(changes => {
  if (changes.blockHistory) renderBlockHistory(changes.blockHistory.newValue);
  if (changes.blockCount) {
    document.getElementById('count').textContent = changes.blockCount.newValue ?? 0;
  }
  if (changes.pendingBlockQueue) {
    document.getElementById('queueCount').textContent =
      Array.isArray(changes.pendingBlockQueue.newValue)
        ? changes.pendingBlockQueue.newValue.length
        : 0;
  }
});
