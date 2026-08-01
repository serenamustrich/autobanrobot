extensionAPI.storage.local.get([
  'blockCount',
  'keywords',
  'singleEmojiEnabled',
  'emojiEnglishEmojiEnabled',
  'structuredEmojiTimeEnabled',
  'structuredThreeSegmentEnabled',
  'blockHistory',
  'pendingBlockQueue',
  'updateInfo'
]).then(r => {
  document.getElementById('count').textContent = r.blockCount ?? 0;
  document.getElementById('keywords').value =
    (Array.isArray(r.keywords) ? r.keywords : []).join('\n');
  document.getElementById('emojiEnglishEmojiEnabled').checked =
    r.emojiEnglishEmojiEnabled !== false;
  document.getElementById('singleEmojiEnabled').checked =
    r.singleEmojiEnabled !== false;
  document.getElementById('structuredEmojiTimeEnabled').checked =
    r.structuredEmojiTimeEnabled !== false;
  document.getElementById('structuredThreeSegmentEnabled').checked =
    r.structuredThreeSegmentEnabled !== false;
  document.getElementById('queueCount').textContent =
    Array.isArray(r.pendingBlockQueue) ? r.pendingBlockQueue.length : 0;
  renderBlockHistory(r.blockHistory);
  renderUpdateInfo(r.updateInfo);
});

document.getElementById('currentVersion').textContent =
  `当前版本 v${extensionAPI.runtime.getManifest().version}`;

document.getElementById('save').addEventListener('click', () => {
  const kws = document.getElementById('keywords').value
    .split('\n').map(s => s.trim()).filter(Boolean);

  extensionAPI.storage.local.set({
    keywords: kws,
    ...readRuleSettings()
  }).then(() => {
    showSaved();
  });
});

document.getElementById('loadPopular').addEventListener('click', async () => {
  const button = document.getElementById('loadPopular');
  const status = document.getElementById('popularStatus');
  button.disabled = true;
  status.textContent = '正在从线上服务读取可同步热门关键词…';
  try {
    const response = await fetch(
      'https://ban.richccy.com/api/popular-terms?limit=50'
    );
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const ranking = await response.json();
    const textarea = document.getElementById('keywords');
    const current = textarea.value
      .split('\n').map(value => value.trim()).filter(Boolean);
    const popular = Array.isArray(ranking)
      ? ranking.map(item => item.term).filter(Boolean)
      : [];
    const merged = [...new Set([...current, ...popular])];
    textarea.value = merged.join('\n');
    status.textContent =
      `已加载 ${popular.length} 个热门词，新增 ${merged.length - current.length} 个；请确认后点击保存`;
  } catch {
    status.textContent = '无法连接线上热门关键词服务，请稍后重试';
  } finally {
    button.disabled = false;
  }
});

[
  'singleEmojiEnabled',
  'emojiEnglishEmojiEnabled',
  'structuredEmojiTimeEnabled',
  'structuredThreeSegmentEnabled'
].forEach(id => {
  document.getElementById(id).addEventListener('change', () => {
    extensionAPI.storage.local.set(readRuleSettings()).then(showSaved);
  });
});

function readRuleSettings() {
  return {
    singleEmojiEnabled:
      document.getElementById('singleEmojiEnabled').checked,
    emojiEnglishEmojiEnabled:
      document.getElementById('emojiEnglishEmojiEnabled').checked,
    structuredEmojiTimeEnabled:
      document.getElementById('structuredEmojiTimeEnabled').checked,
    structuredThreeSegmentEnabled:
      document.getElementById('structuredThreeSegmentEnabled').checked
  };
}

let savedTimer = null;
function showSaved() {
  const saved = document.getElementById('saved');
  saved.style.display = 'block';
  clearTimeout(savedTimer);
  savedTimer = setTimeout(() => { saved.style.display = 'none'; }, 2000);
}

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
  extensionAPI.storage.local.set({ blockHistory: [] }).then(() => {
    renderBlockHistory([]);
  });
});

function renderUpdateInfo(info) {
  const status = document.getElementById('updateStatus');
  const release = document.getElementById('openRelease');
  release.hidden = true;
  if (!info) {
    status.textContent = '更新文件直接来自 GitHub Releases';
    return;
  }
  if (info.error) {
    status.textContent = '上次检查失败，可点击重新检查';
    return;
  }
  if (info.available) {
    status.textContent = `发现新版本 v${info.latestVersion}，请前往 GitHub 下载并安装`;
    release.href = info.releaseUrl ||
      'https://github.com/serenamustrich/autobanrobot/releases/latest';
    release.hidden = false;
    return;
  }
  status.textContent = `已是最新版本${info.latestVersion ? `（v${info.latestVersion}）` : ''}`;
}

document.getElementById('checkUpdate').addEventListener('click', async () => {
  const button = document.getElementById('checkUpdate');
  const status = document.getElementById('updateStatus');
  button.disabled = true;
  status.textContent = '正在检查 GitHub Releases…';
  try {
    const response = await extensionAPI.runtime.sendMessage({
      type: 'CHECK_FOR_UPDATE'
    });
    if (!response?.ok) throw new Error(response?.error || 'Update check failed');
    renderUpdateInfo(response.updateInfo);
  } catch {
    status.textContent = '检查失败，请确认网络可以访问 GitHub';
  } finally {
    button.disabled = false;
  }
});

extensionAPI.storage.onChanged.addListener(changes => {
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
  if (changes.updateInfo) renderUpdateInfo(changes.updateInfo.newValue);
});
