let localeMessages = null;

function formatMessage(message, substitutions) {
  const values = Array.isArray(substitutions) ? substitutions : [substitutions];
  return String(message).replace(/\$(\d+)/g, (_, index) => values[Number(index) - 1] ?? '');
}

function t(key, substitutions) {
  const message = localeMessages?.[key]?.message;
  if (message) return formatMessage(message, substitutions);
  return chrome.i18n.getMessage(key, substitutions) || key;
}

function localizeAccountUi() {
  const labels = {
    accountTitle: 'accountTitle', accountLogin: 'login', accountRegister: 'register',
    accountRecover: 'recoverPassword', recoveryReset: 'resetPassword',
    accountSync: 'syncNow', accountLogout: 'logout',
    accountModeLogin: 'login', accountModeRegister: 'register', accountModeRecovery: 'recoverPassword'
  };
  for (const [id, key] of Object.entries(labels)) {
    const node = document.getElementById(id);
    if (node) node.textContent = t(key);
  }
  const placeholders = {
    accountUsername: 'accountUsername', accountPassword: 'accountPassword',
    registerUsername: 'accountUsername', registerPassword: 'accountPassword', recoveryUsername: 'accountUsername',
    securityAnswer: 'securityAnswer', recoveryAnswer: 'securityAnswer', recoveryPassword: 'newPassword'
  };
  for (const [id, key] of Object.entries(placeholders)) {
    const node = document.getElementById(id);
    if (node) node.placeholder = t(key);
  }
  const staticCopy = {
    accountProfileCopy: 'accountSyncDescription', accountGlobalLabel: 'accountGlobalBan',
    accountContributionLabel: 'accountLocalContribution', accountAchievementKicker: 'accountCurrentAchievement'
  };
  for (const [id, key] of Object.entries(staticCopy)) {
    const node = document.getElementById(id);
    if (node) node.textContent = t(key);
  }
}

function localizeStaticPopupUi() {
  document.querySelectorAll('[data-i18n]').forEach(node => { node.textContent = t(node.dataset.i18n); });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(node => { node.placeholder = t(node.dataset.i18nPlaceholder); });
  document.querySelectorAll('[data-i18n-aria-label]').forEach(node => { node.setAttribute('aria-label', t(node.dataset.i18nAriaLabel)); });
  document.getElementById('currentVersion').textContent = t('currentVersion', chrome.runtime.getManifest().version);
  localizeAccountUi();
}

chrome.storage.local.get([
  'blockCount',
  'keywords',
  'remoteRuleConfig',
  'remoteRuleStates',
  'blockHistory',
  'pendingBlockQueue',
  'updateInfo',
  'accountSession',
  'accountSyncAt',
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
  accountContribution = Number(r.blockCount ?? 0);
  renderAccount(r.accountSession, r.accountSyncAt);
  refreshAccountGlobalTotal();
  updateKeywordSummary();
  updateRuleSummary();
  showTab(r.popupActiveTab || 'keywords', false);
});

const validTabs = new Set(['keywords', 'rules', 'history', 'update', 'account']);
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

document.getElementById('save').addEventListener('click', () => {
  const entered = document.getElementById('keywords').value
    .split('\n').map(s => s.trim()).filter(Boolean);
  chrome.storage.local.get(['keywords'], stored => {
    const existing = Array.isArray(stored.keywords) ? stored.keywords : [];
    const existingSet = new Set(existing.map(keyword => String(keyword).trim()).filter(Boolean));
    const additions = entered.filter(keyword => !existingSet.has(keyword));
    const retained = entered.filter(keyword => existingSet.has(keyword));
    const keywords = [...new Set([...additions, ...retained])];
    chrome.storage.local.set({ keywords }, () => {
      document.getElementById('keywords').value = keywords.join('\n');
      updateKeywordSummary();
      showSaved();
    });
  });
});

document.getElementById('keywords').addEventListener('input', updateKeywordSummary);

document.getElementById('loadPopular').addEventListener('click', async () => {
  const button = document.getElementById('loadPopular');
  const status = document.getElementById('popularStatus');
  button.disabled = true;
  status.textContent = t('popularLoading');
  try {
    const response = await fetch(
      'https://ban.richccy.com/api/popular-terms'
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
    status.textContent = t('popularLoaded', [popular.length, merged.length - current.length]);
  } catch {
    status.textContent = t('popularFailed');
  } finally {
    button.disabled = false;
  }
});

function updateKeywordSummary() {
  const count = document.getElementById('keywords').value
    .split('\n').map(value => value.trim()).filter(Boolean).length;
  document.getElementById('keywordCount').textContent = t('keywordCount', count);
  document.getElementById('keywordBadge').textContent = count;
}

function updateRuleSummary() {
  const inputs = [...document.querySelectorAll('#rulesPanel input[type="checkbox"]')];
  const enabled = inputs.filter(input => input.checked).length;
  document.getElementById('ruleCount').textContent = t('ruleCount', [enabled, inputs.length]);
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
    input.checked = states[rule.id] ?? rule.enabled ?? true;
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
    copy.textContent = t('ruleToggle', rule.name);
    const hint = document.createElement('div');
    hint.className = 'hint';
    hint.textContent = rule.description || t('ruleManaged');
    copy.appendChild(hint);
    label.append(input, slider, copy);
    container.appendChild(label);
  }
  const checkedAt = config?.checkedAt ? new Date(config.checkedAt).toLocaleString() : t('ruleLocalPreset');
  status.textContent = t('ruleStatus', [config?.version ?? '—', checkedAt]);
  updateRuleSummary();
}

document.getElementById('refreshRules').addEventListener('click', () => {
  const button = document.getElementById('refreshRules');
  const status = document.getElementById('ruleStatus');
  button.disabled = true;
  status.textContent = t('ruleUpdating');
  chrome.runtime.sendMessage({ type: 'REFRESH_RULES' }, response => {
    button.disabled = false;
    if (chrome.runtime.lastError || !response?.ok) {
      status.textContent = t('ruleUpdateFailed');
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
    empty.textContent = t('historyEmpty');
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
    const cleanName = String(record.displayName ?? '')
      .replace(/\s*[·•]\s*(?:\d+\s*(?:秒|分钟|小时|天)|\d{1,2}月\d{1,2}日)\s*$/u, '')
      .trim();
    user.textContent = cleanName
      ? `${cleanName} (@${record.username})`
      : `@${record.username}`;

    const meta = document.createElement('div');
    meta.className = 'history-meta';
    const time = record.blockedAt
      ? new Date(record.blockedAt).toLocaleString()
      : t('unknownTime');
    meta.textContent = time;

    item.append(user, meta);
    const keywords = [...new Set((Array.isArray(record.matchedKeywords)
      ? record.matchedKeywords
      : []).map(value => String(value ?? '').trim()).filter(Boolean))];
    if (keywords.length || record.reason) {
      const evidence = document.createElement('div');
      evidence.className = 'history-evidence';
      if (keywords.length) {
        const keyword = document.createElement('div');
        keyword.className = 'history-evidence-keyword';
        keyword.textContent = t('matchedKeywords', keywords.join('、'));
        evidence.appendChild(keyword);
      }
      const rule = document.createElement('div');
      rule.className = 'history-evidence-rule';
      rule.textContent = t('ruleReason', record.reason || t('defaultRuleReason'));
      evidence.appendChild(rule);
      item.appendChild(evidence);
    }
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
      unblock.textContent = t('reblock');
      unblock.addEventListener('click', () => requestReblock(record, unblock));
      const unblockedTime = new Date(record.unblockedAt).toLocaleString();
      unblock.title = t('unblockedAt', unblockedTime);
    } else {
      unblock.textContent = t('unblock');
      unblock.addEventListener('click', () => requestUnblock(record, unblock));
    }
    actions.appendChild(unblock);
    item.appendChild(actions);
    container.appendChild(item);
  });
}

function requestUnblock(record, button) {
  if (!confirm(t('confirmUnblock', record.username))) return;
  button.disabled = true;
  button.textContent = t('processing');
  chrome.runtime.sendMessage({ type: 'UNBLOCK_USER', record }, response => {
    if (chrome.runtime.lastError || !response?.ok) {
      button.disabled = false;
      button.textContent = t('retryUnblock');
      button.title = response?.error || chrome.runtime.lastError?.message || t('actionFailed');
      return;
    }
    chrome.storage.local.get(['blockHistory'], result => {
      renderBlockHistory(result.blockHistory);
    });
  });
}

function requestReblock(record, button) {
  if (!confirm(t('confirmReblock', record.username))) return;
  button.disabled = true;
  button.textContent = t('processing');
  chrome.runtime.sendMessage({ type: 'REBLOCK_USER', record }, response => {
    if (chrome.runtime.lastError || !response?.ok) {
      button.disabled = false;
      button.textContent = t('retryReblock');
      button.title = response?.error || chrome.runtime.lastError?.message || t('actionFailed');
      return;
    }
    chrome.storage.local.get(['blockHistory'], result => {
      renderBlockHistory(result.blockHistory);
    });
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
    status.textContent = t('updateGitHub');
    return;
  }
  if (info.error) {
    status.textContent = t('updateCheckFailed');
    return;
  }
  if (info.available) {
    status.textContent = t('updateAvailable', info.latestVersion);
    release.href = info.releaseUrl ||
      'https://github.com/serenamustrich/autobanrobot/releases/latest';
    release.hidden = false;
    return;
  }
  status.textContent = t('updateCurrent', info.latestVersion ? t('updateCurrentVersion', info.latestVersion) : '');
}

document.getElementById('checkUpdate').addEventListener('click', () => {
  const button = document.getElementById('checkUpdate');
  const status = document.getElementById('updateStatus');
  button.disabled = true;
  status.textContent = t('updateChecking');
  chrome.runtime.sendMessage({ type: 'CHECK_FOR_UPDATE' }, response => {
    button.disabled = false;
    if (chrome.runtime.lastError || !response?.ok) {
      status.textContent = t('updateCheckFailed');
      return;
    }
    renderUpdateInfo(response.updateInfo);
  });
});

const securityQuestionKeys = {
  first_teacher: 'questionFirstTeacher', childhood_nickname: 'questionChildhoodNickname', first_pet: 'questionFirstPet', favorite_book: 'questionFavoriteBook', favorite_food: 'questionFavoriteFood', dream_job: 'questionDreamJob', first_concert: 'questionFirstConcert', favorite_city: 'questionFavoriteCity', childhood_friend: 'questionChildhoodFriend', favorite_film: 'questionFavoriteFilm'
};
function securityQuestions() {
  return Object.fromEntries(Object.entries(securityQuestionKeys).map(([key, message]) => [key, t(message)]));
}
const questionSelect = document.getElementById('securityQuestion');
function renderSecurityQuestions() {
  const selected = questionSelect.value;
  questionSelect.replaceChildren();
  Object.entries(securityQuestions()).forEach(([key, label]) => {
    const option = document.createElement('option'); option.value = key; option.textContent = label; questionSelect.append(option);
  });
  questionSelect.value = selected || questionSelect.options[0]?.value || '';
}
renderSecurityQuestions();

const contributionAchievements = [
  [1, 'achievement1', 10], [2, 'achievement2', 30], [3, 'achievement3', 100], [4, 'achievement4', 300], [5, 'achievement5', 1000],
  [6, 'achievement6', 3000], [7, 'achievement7', 10000], [8, 'achievement8', 30000], [9, 'achievement9', 100000], [10, 'achievement10', 300000]
];
let accountContribution = 0;
let accountGlobalTotal = null;
let renderedAccountSession = null;

function accountBadgeUrl(level) {
  return chrome.runtime.getURL(`assets/badges/contribution-badge-${String(level).padStart(2, '0')}.png`);
}

function renderAchievementSummary(contribution) {
  const current = [...contributionAchievements].reverse().find(([, , threshold]) => contribution >= threshold);
  const next = contributionAchievements.find(([, , threshold]) => contribution < threshold);
  const display = current || contributionAchievements[0];
  document.getElementById('accountContribution').textContent = contribution.toLocaleString();
  document.getElementById('accountAchievementArtwork').src = accountBadgeUrl(display[0]);
  document.getElementById('accountAchievementArtwork').style.opacity = current ? '1' : '.38';
  document.getElementById('accountAchievementName').textContent = current
    ? `Lv.${current[0]} ${t(current[1])}`
    : t('accountFirstAchievement');
  document.getElementById('accountAchievementNext').textContent = next
    ? t('accountNextAchievement', [(next[2] - contribution).toLocaleString(), next[0], t(next[1])])
    : t('accountAllAchievements');
  const wall = document.getElementById('accountAchievementWall');
  wall.replaceChildren();
  contributionAchievements.forEach(([level, title, threshold]) => {
    const unlocked = contribution >= threshold;
    const tile = document.createElement('button');
    tile.type = 'button';
    tile.className = `achievement-tile${unlocked ? ' unlocked' : ''}`;
    tile.title = t('accountAchievementRequirement', [level, t(title), threshold.toLocaleString()]);
    const image = document.createElement('img');
    image.src = accountBadgeUrl(level);
    image.alt = `Lv.${level} ${t(title)}`;
    const label = document.createElement('span');
    label.textContent = `Lv.${level}`;
    tile.append(image, label);
    wall.appendChild(tile);
  });
}

async function refreshAccountGlobalTotal() {
  try {
    const response = await fetch('https://ban.richccy.com/api/bans/stats', { cache: 'no-store' });
    if (!response.ok) return;
    const body = await response.json();
    accountGlobalTotal = Math.max(0, Number(body?.total ?? 0));
    document.getElementById('accountGlobalTotal').textContent = accountGlobalTotal.toLocaleString();
  } catch {
    // Keep the placeholder while offline; local contribution remains available.
  }
}

function renderAccount(session, syncedAt) {
  renderedAccountSession = session;
  const signedIn = Boolean(session?.accessToken);
  document.getElementById('accountSignedOut').hidden = signedIn;
  document.getElementById('accountSignedIn').hidden = !signedIn;
  if (signedIn) {
    document.getElementById('accountUsernameLabel').textContent = session.username;
    document.getElementById('accountGlobalTotal').textContent = accountGlobalTotal === null ? '—' : accountGlobalTotal.toLocaleString();
    renderAchievementSummary(accountContribution);
  }
}

function accountMessage(action, payload = {}) {
  return new Promise(resolve => chrome.runtime.sendMessage({ type: action, ...payload }, resolve));
}
async function submitAccount(mode) {
  const username = document.getElementById(mode === 'register' ? 'registerUsername' : 'accountUsername').value.trim();
  const password = document.getElementById(mode === 'register' ? 'registerPassword' : 'accountPassword').value;
  const status = document.getElementById('accountStatus');
  status.textContent = t('accountWorking');
  const payload = mode === 'register'
    ? { username, password, securityQuestionKey: questionSelect.value, securityAnswer: document.getElementById('securityAnswer').value }
    : { username, password };
  const result = await accountMessage('ACCOUNT_AUTH', { mode, payload });
  if (!result?.ok) { status.textContent = localizeAccountError(result?.error); return; }
  status.textContent = t('accountMerged');
  chrome.storage.local.get(['accountSession', 'accountSyncAt'], value => renderAccount(value.accountSession, value.accountSyncAt));
}
document.getElementById('accountLogin').addEventListener('click', () => submitAccount('login'));
document.getElementById('accountRegister').addEventListener('click', () => submitAccount('register'));
document.getElementById('accountLogout').addEventListener('click', async () => { await accountMessage('ACCOUNT_LOGOUT'); renderAccount(null, null); });
document.getElementById('accountSync').addEventListener('click', async () => {
  const button = document.getElementById('accountSync');
  button.disabled = true;
  button.textContent = t('accountWorking');
  const result = await accountMessage('ACCOUNT_SYNC');
  button.disabled = false;
  button.textContent = t('syncNow');
  document.getElementById('accountStatus').textContent = result?.ok ? t('accountSynced') : localizeAccountError(result?.error);
  if (result?.ok) refreshAccountGlobalTotal();
});
document.getElementById('accountRecover').addEventListener('click', async () => {
  const username = document.getElementById('recoveryUsername').value.trim();
  const result = await accountMessage('ACCOUNT_RECOVERY_QUESTION', { username });
  if (!result?.ok) { document.getElementById('accountStatus').textContent = localizeAccountError(result?.error); return; }
  document.getElementById('accountRecovery').hidden = false;
  document.getElementById('recoveryQuestionLabel').textContent = securityQuestions()[result.securityQuestionKey] || t('securityQuestion');
  document.getElementById('accountRecovery').dataset.question = result.securityQuestionKey;
});
document.getElementById('recoveryReset').addEventListener('click', async () => {
  const status = document.getElementById('accountStatus');
  const recovery = document.getElementById('accountRecovery');
  const result = await accountMessage('ACCOUNT_RECOVERY_RESET', { payload: {
    username: document.getElementById('recoveryUsername').value.trim(), securityQuestionKey: recovery.dataset.question,
    securityAnswer: document.getElementById('recoveryAnswer').value, newPassword: document.getElementById('recoveryPassword').value
  }});
  status.textContent = result?.ok ? t('accountResetDone') : localizeAccountError(result?.error);
  if (result?.ok) chrome.storage.local.get(['accountSession', 'accountSyncAt'], value => renderAccount(value.accountSession, value.accountSyncAt));
});

function setAccountMode(mode) {
  document.getElementById('accountLoginForm').hidden = mode !== 'login';
  document.getElementById('accountRegisterForm').hidden = mode !== 'register';
  document.getElementById('accountRecoveryForm').hidden = mode !== 'recovery';
  document.getElementById('accountRecovery').hidden = true;
  document.querySelectorAll('[id^="accountMode"]').forEach(button => {
    button.setAttribute('aria-selected', String(button.id === `accountMode${mode[0].toUpperCase()}${mode.slice(1)}`));
  });
  document.getElementById('accountStatus').textContent = '';
}
document.getElementById('accountModeLogin').addEventListener('click', () => setAccountMode('login'));
document.getElementById('accountModeRegister').addEventListener('click', () => setAccountMode('register'));
document.getElementById('accountModeRecovery').addEventListener('click', () => setAccountMode('recovery'));

function localizeAccountError(code) {
  if (!code) return t('errorAuthFailed');
  if (code === 'AUTH_NETWORK_ERROR' || code === 'Failed to fetch') return t('errorNetwork');
  return code;
}

chrome.storage.onChanged.addListener(changes => {
  if (changes.blockHistory) renderBlockHistory(changes.blockHistory.newValue);
  if (changes.blockCount) {
    accountContribution = Number(changes.blockCount.newValue ?? 0);
    document.getElementById('count').textContent = accountContribution;
    if (renderedAccountSession?.accessToken) renderAchievementSummary(accountContribution);
  }
  if (changes.pendingBlockQueue) {
    document.getElementById('queueCount').textContent =
      Array.isArray(changes.pendingBlockQueue.newValue)
        ? changes.pendingBlockQueue.newValue.length
        : 0;
  }
  if (changes.updateInfo) renderUpdateInfo(changes.updateInfo.newValue);
  if (changes.accountSession || changes.accountSyncAt) chrome.storage.local.get(['accountSession', 'accountSyncAt'], value => renderAccount(value.accountSession, value.accountSyncAt));
  if (changes.remoteRuleConfig || changes.remoteRuleStates) {
    chrome.storage.local.get(['remoteRuleConfig', 'remoteRuleStates'], r => {
      renderRemoteRules(r.remoteRuleConfig, r.remoteRuleStates);
    });
  }
});

async function selectSystemLocale() {
  const languages = await new Promise(resolve => chrome.i18n.getAcceptLanguages(resolve));
  const candidates = [...(Array.isArray(languages) ? languages : []), ...navigator.languages, navigator.language, chrome.i18n.getUILanguage()]
    .filter(Boolean)
    .map(value => String(value).toLowerCase());
  const folder = candidates.some(value => /^zh[-_](tw|hk|mo)/.test(value))
    ? 'zh_TW'
    : candidates.some(value => value === 'zh' || value.startsWith('zh-') || value.startsWith('zh_'))
      ? 'zh_CN'
      : 'en';
  const response = await fetch(chrome.runtime.getURL(`_locales/${folder}/messages.json`));
  if (!response.ok) {
    localizeStaticPopupUi();
    return;
  }
  localeMessages = await response.json();
  localizeStaticPopupUi();
  renderSecurityQuestions();
  chrome.storage.local.get(['keywords', 'remoteRuleConfig', 'remoteRuleStates', 'blockHistory', 'updateInfo', 'accountSession', 'accountSyncAt'], state => {
    document.getElementById('keywords').value = (Array.isArray(state.keywords) ? state.keywords : []).join('\n');
    updateKeywordSummary();
    renderRemoteRules(state.remoteRuleConfig, state.remoteRuleStates);
    renderBlockHistory(state.blockHistory);
    renderUpdateInfo(state.updateInfo);
    renderAccount(state.accountSession, state.accountSyncAt);
  });
}

selectSystemLocale().catch(() => {
  localizeStaticPopupUi();
});
