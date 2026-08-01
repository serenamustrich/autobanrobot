// 启动时推关键词给 injected.js
extensionAPI.storage.local.get(['keywords']).then(r => {
  dispatchKeywords(Array.isArray(r.keywords) ? r.keywords : []);
});

extensionAPI.storage.local.get(['remoteRuleConfig', 'remoteRuleStates']).then(r => {
  dispatchRules(r.remoteRuleConfig, r.remoteRuleStates);
});

// 关键词更新时实时推送
extensionAPI.storage.onChanged.addListener(changes => {
  if (changes.keywords) {
    dispatchKeywords(Array.isArray(changes.keywords.newValue) ? changes.keywords.newValue : []);
  }
  if (changes.remoteRuleConfig || changes.remoteRuleStates) {
    extensionAPI.storage.local.get(['remoteRuleConfig', 'remoteRuleStates']).then(r => {
      dispatchRules(r.remoteRuleConfig, r.remoteRuleStates);
    });
  }
});

function dispatchKeywords(kws) {
  window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws } }));
}

function dispatchRules(config, states) {
  window.dispatchEvent(new CustomEvent('__twblocker_rules__', {
    detail: {
      config: config && typeof config === 'object' ? config : null,
      states: states && typeof states === 'object' ? states : {}
    }
  }));
}

extensionAPI.runtime.onMessage.addListener(msg => {
  if (msg.type === 'BLOCK_RESULT' && msg.result) {
    window.dispatchEvent(new CustomEvent('__twblocker_block_result__', {
      detail: msg.result
    }));
  }
});

window.addEventListener('__twblocker_enqueue__', event => {
  if (!event.detail?.username) return;
  extensionAPI.runtime.sendMessage({
    type: 'ENQUEUE_BLOCK',
    job: event.detail
  }).then(response => {
    if (response?.queued) return;
    window.dispatchEvent(new CustomEvent('__twblocker_block_result__', {
      detail: {
        ...event.detail,
        state: 'failed',
        message: response?.error || '无法加入后台队列'
      }
    }));
  }).catch(error => {
    window.dispatchEvent(new CustomEvent('__twblocker_block_result__', {
      detail: {
        ...event.detail,
        state: 'failed',
        message: error.message || '无法连接 Safari 后台队列'
      }
    }));
  });
});
