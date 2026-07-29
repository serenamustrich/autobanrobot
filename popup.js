chrome.storage.local.get(['blockCount', 'keywords'], r => {
  document.getElementById('count').textContent = r.blockCount ?? 0;
  document.getElementById('keywords').value =
    (Array.isArray(r.keywords) ? r.keywords : []).join('\n');
});

document.getElementById('save').addEventListener('click', () => {
  const kws = document.getElementById('keywords').value
    .split('\n').map(s => s.trim()).filter(Boolean);

  chrome.storage.local.set({ keywords: kws }, () => {
    const saved = document.getElementById('saved');
    saved.style.display = 'block';
    setTimeout(() => { saved.style.display = 'none'; }, 2000);
  });
});
