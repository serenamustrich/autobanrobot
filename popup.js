const DEFAULT_KEYWORDS = [
  '免费过夜',
  '主页联系',
  '主页匹配',
  '免费破处',
  '同城',
  '上门',
  '刷了半天',
  '看主页',
  '点我头像',
  '处男免费',
  '处男无偿',
  '体制内老师',
  '她太涩了',
  'sao货'
];

chrome.storage.local.get(['blockCount', 'keywords'], r => {
  document.getElementById('count').textContent = r.blockCount ?? 0;
  document.getElementById('keywords').value =
    [...new Set([...DEFAULT_KEYWORDS, ...(r.keywords ?? [])])].join('\n');
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
