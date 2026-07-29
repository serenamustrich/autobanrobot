let bearer = null;
const bearerReady = chrome.storage.session.get(['bearer']).then(result => {
  if (!bearer) bearer = result.bearer ?? null;
});

async function initializeKeywords() {
  const stored = await chrome.storage.local.get(['keywords']);
  if (Array.isArray(stored.keywords)) return;

  const response = await fetch(chrome.runtime.getURL('default-keywords.json'));
  const keywords = await response.json();
  await chrome.storage.local.set({ keywords });
}

chrome.runtime.onInstalled.addListener(() => {
  initializeKeywords().catch(error => {
    console.error('Failed to initialize keyword preset:', error);
  });
});

chrome.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    const auth = details.requestHeaders?.find(
      h => h.name.toLowerCase() === 'authorization' && h.value?.startsWith('Bearer ')
    );
    if (auth && auth.value !== bearer) {
      bearer = auth.value;
      chrome.storage.session.set({ bearer });
      chrome.tabs.query({ url: ['https://twitter.com/*', 'https://x.com/*'] }, tabs => {
        tabs.forEach(tab =>
          chrome.tabs.sendMessage(tab.id, { type: 'BEARER', token: bearer }).catch(() => {})
        );
      });
    }
  },
  { urls: ['https://twitter.com/i/api/*', 'https://x.com/i/api/*'] },
  ['requestHeaders']
);

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg.type !== 'GET_BEARER') return;
  bearerReady.then(() => sendResponse({ token: bearer }));
  return true;
});
