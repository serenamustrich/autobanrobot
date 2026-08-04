// 启动时推关键词给 injected.js
chrome.storage.local.get(['keywords'], r => {
  dispatchKeywords(Array.isArray(r.keywords) ? r.keywords : []);
});

window.dispatchEvent(new CustomEvent('__twblocker_locale__', {
  detail: {
    stamp: chrome.i18n.getMessage('stampLabel') || '扑街',
    processing: chrome.i18n.getMessage('pageProcessing') || '处理中：屏蔽和隐藏',
    done: chrome.i18n.getMessage('pageDone') || '已屏蔽和隐藏',
    skipped: chrome.i18n.getMessage('pageSkipped') || '已跳过',
    waiting: chrome.i18n.getMessage('pageWaiting') || '等待重试：屏蔽和隐藏',
    stats: chrome.i18n.getMessage('pageStats') || 'Current page: matched $1 · blocked $2'
  }
}));

chrome.storage.local.get(['accountWhitelist'], r => {
  dispatchAccountWhitelist(Array.isArray(r.accountWhitelist) ? r.accountWhitelist : []);
});

chrome.storage.local.get(['remoteRuleConfig', 'remoteRuleStates'], r => {
  dispatchRules(r.remoteRuleConfig, r.remoteRuleStates);
});

chrome.runtime.sendMessage({ type: 'RESTORE_HISTORY_VISUALS' }).catch(() => {});

// 关键词更新时实时推送
chrome.storage.onChanged.addListener(changes => {
  if (changes.keywords) {
    dispatchKeywords(Array.isArray(changes.keywords.newValue) ? changes.keywords.newValue : []);
  }
  if (changes.accountWhitelist) {
    dispatchAccountWhitelist(Array.isArray(changes.accountWhitelist.newValue) ? changes.accountWhitelist.newValue : []);
  }
  if (changes.remoteRuleConfig || changes.remoteRuleStates) {
    chrome.storage.local.get(['remoteRuleConfig', 'remoteRuleStates'], r => {
      dispatchRules(r.remoteRuleConfig, r.remoteRuleStates);
    });
  }
});

function dispatchKeywords(kws) {
  window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws } }));
}

function dispatchAccountWhitelist(accounts) {
  window.dispatchEvent(new CustomEvent('__twblocker_whitelist__', { detail: { accounts } }));
}

function dispatchRules(config, states) {
  window.dispatchEvent(new CustomEvent('__twblocker_rules__', {
    detail: {
      config: config && typeof config === 'object' ? config : null,
      states: states && typeof states === 'object' ? states : {}
    }
  }));
}

chrome.runtime.onMessage.addListener(msg => {
  if (msg.type === 'BLOCK_RESULT' && msg.result) {
    window.dispatchEvent(new CustomEvent('__twblocker_block_result__', {
      detail: msg.result
    }));
  }
});

window.addEventListener('__twblocker_enqueue__', event => {
  if (!event.detail?.username) return;
  let request;
  try {
    if (!chrome.runtime?.id) return;
    request = chrome.runtime.sendMessage({
      type: 'ENQUEUE_BLOCK',
      job: event.detail
    });
  } catch (error) {
    if (isExtensionContextError(error)) return;
    console.error('Failed to send block job to background:', error);
    return;
  }
  Promise.resolve(request).then(response => {
    if (response?.queued) return;
    window.dispatchEvent(new CustomEvent('__twblocker_block_result__', {
      detail: {
        ...event.detail,
        state: 'failed',
        message: response?.error || '无法加入后台队列'
      }
    }));
  }).catch(error => {
    if (isExtensionContextError(error)) return;
    console.error('Failed to send block job to background:', error);
  });
});

function isExtensionContextError(error) {
  return /No SW|Extension context invalidated|Receiving end does not exist|message port closed/i
    .test(error?.message ?? '');
}
