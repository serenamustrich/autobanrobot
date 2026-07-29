// 启动时推关键词给 injected.js
chrome.storage.local.get(['keywords'], r => {
  dispatchKeywords(Array.isArray(r.keywords) ? r.keywords : []);
});

chrome.storage.local.get(['emojiEnglishEmojiEnabled'], r => {
  dispatchSettings({
    emojiEnglishEmojiEnabled: r.emojiEnglishEmojiEnabled !== false
  });
});

// 关键词更新时实时推送
chrome.storage.onChanged.addListener(changes => {
  if (changes.keywords) {
    dispatchKeywords(Array.isArray(changes.keywords.newValue) ? changes.keywords.newValue : []);
  }
  if (changes.emojiEnglishEmojiEnabled) {
    dispatchSettings({
      emojiEnglishEmojiEnabled: changes.emojiEnglishEmojiEnabled.newValue !== false
    });
  }
});

function dispatchKeywords(kws) {
  window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws } }));
}

function dispatchSettings(settings) {
  window.dispatchEvent(new CustomEvent('__twblocker_settings__', { detail: settings }));
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
  });
});
