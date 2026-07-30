const state = {
  page: 0,
  size: 30,
  total: 0,
  totalPages: 0,
  query: ''
};

const elements = {
  rows: document.getElementById('banRows'),
  empty: document.getElementById('emptyState'),
  template: document.getElementById('rowTemplate'),
  total: document.getElementById('totalCount'),
  today: document.getElementById('todayCount'),
  range: document.getElementById('rangeText'),
  previous: document.getElementById('prevButton'),
  next: document.getElementById('nextButton'),
  search: document.getElementById('searchInput'),
  connection: document.getElementById('connection'),
  connectionText: document.getElementById('connectionText'),
  status: document.getElementById('statusValue')
};

const keywordElements = {
  rows: document.getElementById('keywordRows'),
  empty: document.getElementById('keywordEmpty'),
  template: document.getElementById('keywordRowTemplate')
};

async function fetchJson(url) {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

async function loadPage() {
  const params = new URLSearchParams({
    page: state.page,
    size: state.size,
    query: state.query
  });
  const data = await fetchJson(`/api/bans?${params}`);
  state.total = data.total;
  state.totalPages = data.totalPages;
  renderRows(data.items);
  renderPager();
}

async function loadStats() {
  const data = await fetchJson('/api/bans/stats');
  elements.total.textContent = data.total.toLocaleString();
  elements.today.textContent = data.today.toLocaleString();
}

async function loadKeywords() {
  const items = await fetchJson('/api/keywords?limit=200');
  keywordElements.rows.replaceChildren();
  keywordElements.empty.hidden = items.length !== 0;
  items.forEach(item => {
    const row = keywordElements.template.content.cloneNode(true);
    row.querySelector('.rank').textContent = `#${item.rank}`;
    row.querySelector('.keyword').textContent = item.keyword;
    row.querySelector('.configured-count').textContent =
      item.configuredCount.toLocaleString();
    row.querySelector('.hit-count').textContent =
      item.hitCount.toLocaleString();
    row.querySelector('.account-count').textContent =
      item.banAccountCount.toLocaleString();
    keywordElements.rows.appendChild(row);
  });
}

function renderRows(items) {
  elements.rows.replaceChildren();
  elements.empty.hidden = items.length !== 0;
  items.forEach(item => {
    const row = elements.template.content.cloneNode(true);
    const account = row.querySelector('.account');
    const name = item.displayName || `@${item.username}`;
    account.href = `https://x.com/${encodeURIComponent(item.username)}`;
    row.querySelector('.avatar').dataset.initial =
      [...name.trim()][0]?.toLocaleUpperCase() || '?';
    row.querySelector('.display-name').textContent = name;
    row.querySelector('.username').textContent = `@${item.username}`;
    const keywords = item.matchedKeywords?.length
      ? item.matchedKeywords.join('、')
      : item.reason || '结构化规则';
    row.querySelector('.reason').textContent = keywords;
    row.querySelector('.reason').title =
      item.reason ? `${keywords} · ${item.reason}` : keywords;
    row.querySelector('.content').textContent = item.content || '—';
    row.querySelector('.content').title = item.content || '';
    const time = row.querySelector('.blocked-at');
    time.dateTime = item.blockedAt;
    time.textContent = new Date(item.blockedAt).toLocaleString();
    elements.rows.appendChild(row);
  });
}

function renderPager() {
  const start = state.total === 0 ? 0 : state.page * state.size + 1;
  const end = Math.min((state.page + 1) * state.size, state.total);
  elements.range.textContent =
    `第 ${start.toLocaleString()}–${end.toLocaleString()} 条，共 ${state.total.toLocaleString()} 条`;
  elements.previous.disabled = state.page <= 0;
  elements.next.disabled =
    state.totalPages === 0 || state.page >= state.totalPages - 1;
}

function setConnection(mode, text) {
  elements.connection.classList.remove('live', 'offline');
  if (mode) elements.connection.classList.add(mode);
  elements.connectionText.textContent = text;
  elements.status.textContent =
    mode === 'live' ? '实时在线' : mode === 'offline' ? '等待重连' : '连接中';
}

function connectStream() {
  const stream = new EventSource('/api/bans/stream');
  stream.addEventListener('connected', () => {
    setConnection('live', '实时数据已连接');
  });
  stream.addEventListener('ban', async event => {
    const record = JSON.parse(event.data);
    if (!state.query && state.page === 0) {
      await Promise.all([loadPage(), loadStats(), loadKeywords()]);
    } else if (
      state.query &&
      record.username.toLocaleLowerCase().includes(state.query.toLocaleLowerCase())
    ) {
      await Promise.all([loadPage(), loadStats()]);
    } else {
      await loadStats();
    }
  });
  stream.onerror = () => {
    setConnection('offline', '连接中断，正在自动重试');
  };
}

document.querySelectorAll('.page-tab').forEach(button => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.page-tab').forEach(tab => {
      tab.classList.toggle('active', tab === button);
    });
    document.getElementById('bansPage').hidden = button.dataset.page !== 'bans';
    document.getElementById('keywordsPage').hidden =
      button.dataset.page !== 'keywords';
  });
});

let searchTimer = null;
elements.search.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(async () => {
    state.query = elements.search.value.trim();
    state.page = 0;
    await loadPage();
  }, 250);
});

elements.previous.addEventListener('click', async () => {
  if (state.page <= 0) return;
  state.page--;
  await loadPage();
});

elements.next.addEventListener('click', async () => {
  if (state.page >= state.totalPages - 1) return;
  state.page++;
  await loadPage();
});

Promise.all([loadPage(), loadStats(), loadKeywords()])
  .then(connectStream)
  .catch(error => {
    console.error(error);
    setConnection('offline', '服务器数据加载失败');
  });
