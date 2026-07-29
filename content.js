// 启动时推关键词给 injected.js
chrome.storage.local.get(['keywords'], r => {
  dispatchKeywords(Array.isArray(r.keywords) ? r.keywords : []);
});

// 关键词更新时实时推送
chrome.storage.onChanged.addListener(changes => {
  if (changes.keywords) {
    dispatchKeywords(Array.isArray(changes.keywords.newValue) ? changes.keywords.newValue : []);
  }
});

function dispatchKeywords(kws) {
  window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws } }));
}

// 启动时向 background 要 bearer token
chrome.runtime.sendMessage({ type: 'GET_BEARER' }, res => {
  if (res?.token) dispatchBearer(res.token);
});

chrome.runtime.onMessage.addListener(msg => {
  if (msg.type === 'BEARER' && msg.token) dispatchBearer(msg.token);
});

function dispatchBearer(token) {
  window.dispatchEvent(new CustomEvent('__twblocker_bearer__', { detail: { token } }));
}

// 计数
let blockCount = 0;
chrome.storage.local.get(['blockCount'], r => { blockCount = r.blockCount || 0; });
window.addEventListener('__twblocker_blocked__', () => {
  blockCount++;
  chrome.storage.local.set({ blockCount });
});
