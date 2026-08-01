// 启动时推关键词给 injected.js
chrome.storage.local.get(['keywords'], r => {
  dispatchKeywords(Array.isArray(r.keywords) ? r.keywords : []);
});

chrome.storage.local.get(['remoteRuleConfig', 'remoteRuleStates'], r => {
  dispatchRules(r.remoteRuleConfig, r.remoteRuleStates);
});

chrome.storage.local.get([
  'emojiEnglishEmojiEnabled',
  'singleEmojiEnabled',
  'structuredEmojiTimeEnabled',
  'structuredThreeSegmentEnabled'
], r => {
  dispatchSettings({
    emojiEnglishEmojiEnabled: r.emojiEnglishEmojiEnabled !== false,
    singleEmojiEnabled: r.singleEmojiEnabled !== false,
    structuredEmojiTimeEnabled: r.structuredEmojiTimeEnabled !== false,
    structuredThreeSegmentEnabled: r.structuredThreeSegmentEnabled !== false
  });
});

// 关键词更新时实时推送
chrome.storage.onChanged.addListener(changes => {
  if (changes.keywords) {
    dispatchKeywords(Array.isArray(changes.keywords.newValue) ? changes.keywords.newValue : []);
  }
  if (changes.remoteRuleConfig || changes.remoteRuleStates) {
    chrome.storage.local.get(['remoteRuleConfig', 'remoteRuleStates'], r => {
      dispatchRules(r.remoteRuleConfig, r.remoteRuleStates);
    });
  }
  if (
    changes.emojiEnglishEmojiEnabled ||
    changes.singleEmojiEnabled ||
    changes.structuredEmojiTimeEnabled ||
    changes.structuredThreeSegmentEnabled
  ) {
    const settings = {};
    if (changes.emojiEnglishEmojiEnabled) {
      settings.emojiEnglishEmojiEnabled =
        changes.emojiEnglishEmojiEnabled.newValue !== false;
    }
    if (changes.singleEmojiEnabled) {
      settings.singleEmojiEnabled =
        changes.singleEmojiEnabled.newValue !== false;
    }
    if (changes.structuredEmojiTimeEnabled) {
      settings.structuredEmojiTimeEnabled =
        changes.structuredEmojiTimeEnabled.newValue !== false;
    }
    if (changes.structuredThreeSegmentEnabled) {
      settings.structuredThreeSegmentEnabled =
        changes.structuredThreeSegmentEnabled.newValue !== false;
    }
    dispatchSettings(settings);
  }
});

function dispatchKeywords(kws) {
  window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws } }));
}

function dispatchSettings(settings) {
  window.dispatchEvent(new CustomEvent('__twblocker_settings__', { detail: settings }));
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
  chrome.runtime.sendMessage({
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
    if (/No SW|Extension context invalidated|Receiving end does not exist/i.test(
      error?.message ?? ''
    )) return;
    console.error('Failed to send block job to background:', error);
  });
});
