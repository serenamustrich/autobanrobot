// 启动时推关键词给 injected.js
extensionAPI.storage.local.get(['keywords']).then(r => {
  dispatchKeywords(Array.isArray(r.keywords) ? r.keywords : []);
});

extensionAPI.storage.local.get([
  'emojiEnglishEmojiEnabled',
  'singleEmojiEnabled',
  'structuredEmojiTimeEnabled'
]).then(r => {
  dispatchSettings({
    emojiEnglishEmojiEnabled: r.emojiEnglishEmojiEnabled !== false,
    singleEmojiEnabled: r.singleEmojiEnabled !== false,
    structuredEmojiTimeEnabled: r.structuredEmojiTimeEnabled !== false
  });
});

// 关键词更新时实时推送
extensionAPI.storage.onChanged.addListener(changes => {
  if (changes.keywords) {
    dispatchKeywords(Array.isArray(changes.keywords.newValue) ? changes.keywords.newValue : []);
  }
  if (
    changes.emojiEnglishEmojiEnabled ||
    changes.singleEmojiEnabled ||
    changes.structuredEmojiTimeEnabled
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
    dispatchSettings(settings);
  }
});

function dispatchKeywords(kws) {
  window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws } }));
}

function dispatchSettings(settings) {
  window.dispatchEvent(new CustomEvent('__twblocker_settings__', { detail: settings }));
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
