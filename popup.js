chrome.storage.local.get([
  'blockCount',
  'keywords',
  'remoteRuleConfig',
  'remoteRuleStates',
  'blockHistory',
  'pendingBlockQueue',
  'updateInfo',
  'popupActiveTab'
], r => {
  document.getElementById('count').textContent = r.blockCount ?? 0;
  document.getElementById('keywords').value =
    (Array.isArray(r.keywords) ? r.keywords : []).join('\n');
  renderRemoteRules(r.remoteRuleConfig, r.remoteRuleStates);
  document.getElementById('queueCount').textContent =
    Array.isArray(r.pendingBlockQueue) ? r.pendingBlockQueue.length : 0;
  renderBlockHistory(r.blockHistory);
  renderUpdateInfo(r.updateInfo);
  updateKeywordSummary();
  updateRuleSummary();
  showTab(r.popupActiveTab || 'keywords', false);
});

const validTabs = new Set(['keywords', 'rules', 'history', 'update']);
document.querySelectorAll('[data-tab]').forEach(button => {
  button.addEventListener('click', () => showTab(button.dataset.tab));
});

function showTab(requestedTab, remember = true) {
  const tab = validTabs.has(requestedTab) ? requestedTab : 'keywords';
  document.querySelectorAll('[data-tab]').forEach(button => {
    button.setAttribute('aria-selected', String(button.dataset.tab === tab));
  });
  document.querySelectorAll('.tab-panel').forEach(panel => {
    panel.hidden = panel.id !== `${tab}Panel`;
  });
  if (remember) chrome.storage.local.set({ popupActiveTab: tab });
}

document.getElementById('currentVersion').textContent =
  `当前版本 v${chrome.runtime.getManifest().version}`;

document.getElementById('save').addEventListener('click', () => {
  const kws = document.getElementById('keywords').value
    .split('\n').map(s => s.trim()).filter(Boolean);

  chrome.storage.local.set({
    keywords: kws
  }, () => {
    showSaved();
  });
});

document.getElementById('keywords').addEventListener('input', updateKeywordSummary);

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
    updateKeywordSummary();
    status.textContent =
      `已加载 ${popular.length} 个热门词，新增 ${merged.length - current.length} 个；请确认后点击保存`;
  } catch {
    status.textContent = '无法连接线上热门关键词服务，请稍后重试';
  } finally {
    button.disabled = false;
  }
});

function updateKeywordSummary() {
  const count = document.getElementById('keywords').value
    .split('\n').map(value => value.trim()).filter(Boolean).length;
  document.getElementById('keywordCount').textContent = `${count} 个`;
  document.getElementById('keywordBadge').textContent = count;
}

function updateRuleSummary() {
  const inputs = [...document.querySelectorAll('#rulesPanel input[type="checkbox"]')];
  const enabled = inputs.filter(input => input.checked).length;
  document.getElementById('ruleCount').textContent = `${enabled}/${inputs.length} 已启用`;
  document.getElementById('ruleBadge').textContent = enabled;
}

function renderRemoteRules(config, states = {}) {
  const container = document.getElementById('remoteRules');
  const status = document.getElementById('ruleStatus');
  container.replaceChildren();
  const rules = Array.isArray(config?.rules) ? config.rules : [];
  for (const rule of rules) {
    const label = document.createElement('label');
    label.className = 'toggle';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.dataset.ruleToggle = 'remote';
    input.checked = rule.enabled !== false && states[rule.id] !== false;
    input.addEventListener('change', () => {
      chrome.storage.local.get(['remoteRuleStates'], result => {
        const next = { ...(result.remoteRuleStates ?? {}), [rule.id]: input.checked };
        chrome.storage.local.set({ remoteRuleStates: next }, showSaved);
        updateRuleSummary();
      });
    });
    const slider = document.createElement('span');
    slider.className = 'switch-ui';
    slider.setAttribute('aria-hidden', 'true');
    const copy = document.createElement('span');
    copy.textContent = `启用“${rule.name}”规则`;
    const hint = document.createElement('div');
    hint.className = 'hint';
    hint.textContent = rule.description || '由在线规则服务管理';
    copy.appendChild(hint);
    label.append(input, slider, copy);
    container.appendChild(label);
  }
  const checkedAt = config?.checkedAt ? new Date(config.checkedAt).toLocaleString() : '本地预设';
  status.textContent = `在线规则 v${config?.version ?? '—'} · ${checkedAt}`;
  updateRuleSummary();
}

document.getElementById('refreshRules').addEventListener('click', () => {
  const button = document.getElementById('refreshRules');
  const status = document.getElementById('ruleStatus');
  button.disabled = true;
  status.textContent = '正在从服务端更新规则…';
  chrome.runtime.sendMessage({ type: 'REFRESH_RULES' }, response => {
    button.disabled = false;
    if (chrome.runtime.lastError || !response?.ok) {
      status.textContent = '规则更新失败，已继续使用本地缓存';
      return;
    }
    chrome.storage.local.get(['remoteRuleStates'], result => {
      renderRemoteRules(response.config, result.remoteRuleStates);
    });
  });
});

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
    const actions = document.createElement('div');
    actions.className = 'history-actions';
    const unblock = document.createElement('button');
    unblock.type = 'button';
    if (record.unblockedAt) {
      unblock.textContent = '已取消屏蔽';
      unblock.disabled = true;
      const unblockedTime = new Date(record.unblockedAt).toLocaleString();
      unblock.title = `取消时间：${unblockedTime}`;
    } else {
      unblock.textContent = '取消屏蔽';
      unblock.addEventListener('click', () => requestUnblock(record, unblock));
    }
    actions.appendChild(unblock);
    item.appendChild(actions);
    container.appendChild(item);
  });
}

function requestUnblock(record, button) {
  if (!confirm(`确定取消屏蔽 @${record.username}？`)) return;
  button.disabled = true;
  button.textContent = '处理中…';
  chrome.runtime.sendMessage({ type: 'UNBLOCK_USER', record }, response => {
    if (chrome.runtime.lastError || !response?.ok) {
      button.disabled = false;
      button.textContent = '重试取消屏蔽';
      button.title = response?.error || chrome.runtime.lastError?.message || '操作失败';
      return;
    }
    button.textContent = '已取消屏蔽';
    button.title = 'X 已确认取消屏蔽成功';
  });
}

document.getElementById('clearHistory').addEventListener('click', () => {
  chrome.storage.local.set({ blockHistory: [] }, () => {
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

document.getElementById('checkUpdate').addEventListener('click', () => {
  const button = document.getElementById('checkUpdate');
  const status = document.getElementById('updateStatus');
  button.disabled = true;
  status.textContent = '正在检查 GitHub Releases…';
  chrome.runtime.sendMessage({ type: 'CHECK_FOR_UPDATE' }, response => {
    button.disabled = false;
    if (chrome.runtime.lastError || !response?.ok) {
      status.textContent = '检查失败，请确认网络可以访问 GitHub';
      return;
    }
    renderUpdateInfo(response.updateInfo);
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
  if (changes.updateInfo) renderUpdateInfo(changes.updateInfo.newValue);
  if (changes.remoteRuleConfig || changes.remoteRuleStates) {
    chrome.storage.local.get(['remoteRuleConfig', 'remoteRuleStates'], r => {
      renderRemoteRules(r.remoteRuleConfig, r.remoteRuleStates);
    });
  }
});
