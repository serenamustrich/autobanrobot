const state = {
  page: 0,
  size: 30,
  total: 0,
  totalPages: 0,
  query: ''
};

const LANGUAGE_KEY = 'autobanrobot-dashboard-language';
const PAGE_KEY = 'autobanrobot-dashboard-page';
const translations = {
  'zh-CN': {
    pageTitle: 'AutoBanRobot · Ban 清单',
    heroTitle: '实时 Ban 清单',
    subtitle: '仅展示经过 X 确认成功并上传到服务器的屏蔽记录。',
    connecting: '正在连接实时数据',
    releaseLink: 'GitHub Releases · 下载插件',
    connected: '实时数据已连接',
    reconnecting: '连接中断，正在自动重试',
    loadFailed: '服务器数据加载失败',
    banTab: 'Ban 清单', keywordTab: '关键词分析', targetTab: '垃圾推广目标账号',
    confirmedTotal: '累计已确认屏蔽', allRecords: '所有已接收记录',
    todayAdded: '今日新增', serverDate: '按服务器本地日期',
    onlineUsers: '插件在线用户数', onlineWindow: '最近 2 分钟发送过心跳',
    cumulativeUsers: '插件累计用户数', anonymousInstalls: '不同匿名安装实例',
    blockRecords: '屏蔽记录', searchLabel: '搜索用户名',
    searchPlaceholder: '搜索 @username',
    account: '账号', matchedRule: '命中关键词 / 规则',
    contentSummary: '内容摘要', blockedAt: '屏蔽时间',
    noBlocks: '暂时没有屏蔽记录',
    noBlocksHint: '插件确认 Ban 成功后，新记录会实时出现在这里。',
    previous: '上一页', next: '下一页',
    range: '第 {start}–{end} 条，共 {total} 条',
    keywordRanking: '关键词排行榜',
    rankingNote: '按命中次数排序，数据随 Ban 记录实时更新',
    rank: '排名', keyword: '关键词', hitCount: '命中次数',
    noKeywords: '暂无关键词统计',
    noKeywordsHint: '关键词命中并完成 Ban 后会自动生成排名。',
    syncPreview: '可同步热门关键词', syncPreviewNote: '插件“加载热门关键词”将获取以下内容',
    syncTerm: '同步屏蔽词', source: '来源', count: '次数',
    keywordSource: '关键词命中', mentionSource: '@账号提及', combinedSource: '关键词命中 + @提及',
    noPopularTerms: '暂无可同步内容', mentionRanking: '@账号提及排行',
    mentionNote: '统计已 Ban 垃圾内容中出现的 @账号',
    mentionedAccount: '被提及账号', mentionCount: '提及次数',
    noMentions: '暂无 @账号统计',
    structuredRule: '结构化规则', backToTop: '回到顶部'
  },
  en: {
    pageTitle: 'AutoBanRobot · Ban List',
    heroTitle: 'Live Ban List',
    subtitle: 'Only blocks confirmed by X and uploaded to the server are shown.',
    connecting: 'Connecting to live data',
    releaseLink: 'GitHub Releases · Download Plugin',
    connected: 'Live data connected',
    reconnecting: 'Connection lost, retrying automatically',
    loadFailed: 'Failed to load server data',
    banTab: 'Ban List', keywordTab: 'Keyword Analytics', targetTab: 'Spam Promotion Targets',
    confirmedTotal: 'Confirmed Bans', allRecords: 'All received records',
    todayAdded: 'Added Today', serverDate: 'Server local date',
    onlineUsers: 'Online Plugin Users', onlineWindow: 'Heartbeat received in the last 2 minutes',
    cumulativeUsers: 'Cumulative Plugin Users', anonymousInstalls: 'Distinct anonymous installations',
    blockRecords: 'Block Records', searchLabel: 'Search username',
    searchPlaceholder: 'Search @username',
    account: 'Account', matchedRule: 'Matched Keyword / Rule',
    contentSummary: 'Content Summary', blockedAt: 'Blocked At',
    noBlocks: 'No block records yet',
    noBlocksHint: 'New records appear here after the plugin confirms a Ban.',
    previous: 'Previous', next: 'Next',
    range: '{start}–{end} of {total}',
    keywordRanking: 'Keyword Ranking',
    rankingNote: 'Sorted by hit count and updated with Ban records',
    rank: 'Rank', keyword: 'Keyword', hitCount: 'Hits',
    noKeywords: 'No keyword statistics',
    noKeywordsHint: 'Rankings appear after a keyword match results in a Ban.',
    syncPreview: 'Popular Terms Available to Sync', syncPreviewNote: 'The plugin loads exactly these terms',
    syncTerm: 'Synced Block Term', source: 'Source', count: 'Count',
    keywordSource: 'Keyword hit', mentionSource: '@account mention', combinedSource: 'Keyword hit + @mention',
    noPopularTerms: 'No terms available to sync', mentionRanking: '@Account Mention Ranking',
    mentionNote: 'Accounts mentioned in content posted by confirmed spam accounts',
    mentionedAccount: 'Mentioned Account', mentionCount: 'Mentions',
    noMentions: 'No @account statistics',
    structuredRule: 'Structured rule', backToTop: 'Back to top'
  },
  es: {
    pageTitle: 'AutoBanRobot · Lista de bloqueos',
    heroTitle: 'Lista de bloqueos en tiempo real',
    subtitle: 'Solo se muestran bloqueos confirmados por X y subidos al servidor.',
    connecting: 'Conectando con los datos en tiempo real',
    releaseLink: 'GitHub Releases · Descargar plugin',
    connected: 'Datos en tiempo real conectados',
    reconnecting: 'Conexión perdida; reintentando automáticamente',
    loadFailed: 'No se pudieron cargar los datos del servidor',
    banTab: 'Bloqueos', keywordTab: 'Análisis de palabras clave', targetTab: 'Objetivos de promoción spam',
    confirmedTotal: 'Bloqueos confirmados', allRecords: 'Todos los registros recibidos',
    todayAdded: 'Añadidos hoy', serverDate: 'Fecha local del servidor',
    onlineUsers: 'Usuarios del plugin en línea', onlineWindow: 'Heartbeat recibido en los últimos 2 minutos',
    cumulativeUsers: 'Usuarios acumulados', anonymousInstalls: 'Instalaciones anónimas distintas',
    blockRecords: 'Registros de bloqueo', searchLabel: 'Buscar usuario',
    searchPlaceholder: 'Buscar @usuario',
    account: 'Cuenta', matchedRule: 'Palabra clave / regla',
    contentSummary: 'Resumen del contenido', blockedAt: 'Hora del bloqueo',
    noBlocks: 'Aún no hay bloqueos',
    noBlocksHint: 'Los registros aparecen cuando el plugin confirma un bloqueo.',
    previous: 'Anterior', next: 'Siguiente',
    range: '{start}–{end} de {total}',
    keywordRanking: 'Clasificación de palabras clave',
    rankingNote: 'Ordenada por coincidencias y actualizada con los bloqueos',
    rank: 'Puesto', keyword: 'Palabra clave', hitCount: 'Coincidencias',
    noKeywords: 'Sin estadísticas de palabras clave',
    noKeywordsHint: 'La clasificación aparece cuando una coincidencia genera un bloqueo.',
    syncPreview: 'Términos populares para sincronizar', syncPreviewNote: 'El plugin cargará exactamente estos términos',
    syncTerm: 'Término de bloqueo', source: 'Origen', count: 'Cantidad',
    keywordSource: 'Coincidencia', mentionSource: 'Mención de @cuenta', combinedSource: 'Coincidencia + mención',
    noPopularTerms: 'No hay términos para sincronizar', mentionRanking: 'Clasificación de menciones',
    mentionNote: 'Cuentas mencionadas por contenido de spam bloqueado',
    mentionedAccount: 'Cuenta mencionada', mentionCount: 'Menciones',
    noMentions: 'Sin estadísticas de menciones',
    structuredRule: 'Regla estructurada', backToTop: 'Volver arriba'
  },
  ja: {
    pageTitle: 'AutoBanRobot · Ban 一覧',
    heroTitle: 'リアルタイム Ban 一覧',
    subtitle: 'X が確認し、サーバーへ送信されたブロックのみ表示します。',
    connecting: 'リアルタイムデータに接続中',
    releaseLink: 'GitHub Releases · プラグインをダウンロード',
    connected: 'リアルタイムデータ接続済み',
    reconnecting: '接続が切れました。自動再接続中',
    loadFailed: 'サーバーデータを読み込めません',
    banTab: 'Ban 一覧', keywordTab: 'キーワード分析', targetTab: 'スパム宣伝対象アカウント',
    confirmedTotal: '確認済み Ban 累計', allRecords: '受信した全記録',
    todayAdded: '本日の追加', serverDate: 'サーバーのローカル日付',
    onlineUsers: 'オンラインプラグインユーザー', onlineWindow: '直近 2 分以内にハートビート受信',
    cumulativeUsers: 'プラグイン累計ユーザー', anonymousInstalls: '異なる匿名インストール',
    blockRecords: 'ブロック記録', searchLabel: 'ユーザー名を検索',
    searchPlaceholder: '@username を検索',
    account: 'アカウント', matchedRule: '一致キーワード / ルール',
    contentSummary: '内容概要', blockedAt: 'ブロック時刻',
    noBlocks: 'ブロック記録はまだありません',
    noBlocksHint: 'プラグインが Ban を確認すると、ここへリアルタイム表示されます。',
    previous: '前へ', next: '次へ',
    range: '{total} 件中 {start}–{end} 件',
    keywordRanking: 'キーワードランキング',
    rankingNote: '一致回数順。Ban 記録に合わせて更新されます',
    rank: '順位', keyword: 'キーワード', hitCount: '一致回数',
    noKeywords: 'キーワード統計はありません',
    noKeywordsHint: 'キーワード一致による Ban の受信後に表示されます。',
    syncPreview: '同期可能な人気キーワード', syncPreviewNote: 'プラグインは以下の項目を読み込みます',
    syncTerm: '同期ブロック語', source: '取得元', count: '回数',
    keywordSource: 'キーワード一致', mentionSource: '@アカウント言及', combinedSource: '一致 + @言及',
    noPopularTerms: '同期可能な項目はありません', mentionRanking: '@アカウント言及ランキング',
    mentionNote: 'Ban 済みスパム内容に登場する @アカウント',
    mentionedAccount: '言及アカウント', mentionCount: '言及回数',
    noMentions: '@アカウント統計はありません',
    structuredRule: '構造化ルール', backToTop: 'ページ上部へ戻る'
  },
  ko: {
    pageTitle: 'AutoBanRobot · Ban 목록',
    heroTitle: '실시간 Ban 목록',
    subtitle: 'X에서 확인되고 서버에 업로드된 차단 기록만 표시합니다.',
    connecting: '실시간 데이터 연결 중',
    releaseLink: 'GitHub Releases · 플러그인 다운로드',
    connected: '실시간 데이터 연결됨',
    reconnecting: '연결 끊김, 자동 재연결 중',
    loadFailed: '서버 데이터를 불러오지 못했습니다',
    banTab: 'Ban 목록', keywordTab: '키워드 분석', targetTab: '스팸 홍보 대상 계정',
    confirmedTotal: '확인된 누적 Ban', allRecords: '수신한 전체 기록',
    todayAdded: '오늘 추가', serverDate: '서버 현지 날짜 기준',
    onlineUsers: '온라인 플러그인 사용자', onlineWindow: '최근 2분 이내 하트비트 수신',
    cumulativeUsers: '플러그인 누적 사용자', anonymousInstalls: '서로 다른 익명 설치',
    blockRecords: '차단 기록', searchLabel: '사용자 이름 검색',
    searchPlaceholder: '@username 검색',
    account: '계정', matchedRule: '일치 키워드 / 규칙',
    contentSummary: '내용 요약', blockedAt: '차단 시간',
    noBlocks: '차단 기록이 없습니다',
    noBlocksHint: '플러그인이 Ban을 확인하면 여기에 실시간 표시됩니다.',
    previous: '이전', next: '다음',
    range: '전체 {total}개 중 {start}–{end}',
    keywordRanking: '키워드 순위',
    rankingNote: '일치 횟수순 정렬, Ban 기록에 따라 갱신',
    rank: '순위', keyword: '키워드', hitCount: '일치 횟수',
    noKeywords: '키워드 통계가 없습니다',
    noKeywordsHint: '키워드 일치로 발생한 Ban을 받으면 표시됩니다.',
    syncPreview: '동기화 가능한 인기 키워드', syncPreviewNote: '플러그인이 아래 항목을 그대로 불러옵니다',
    syncTerm: '동기화 차단어', source: '출처', count: '횟수',
    keywordSource: '키워드 일치', mentionSource: '@계정 언급', combinedSource: '일치 + @언급',
    noPopularTerms: '동기화할 항목이 없습니다', mentionRanking: '@계정 언급 순위',
    mentionNote: '차단된 스팸 내용에 등장한 @계정',
    mentionedAccount: '언급된 계정', mentionCount: '언급 횟수',
    noMentions: '@계정 통계가 없습니다',
    structuredRule: '구조화 규칙', backToTop: '맨 위로'
  },
  de: {
    pageTitle: 'AutoBanRobot · Ban-Liste',
    heroTitle: 'Live-Ban-Liste',
    subtitle: 'Es werden nur von X bestätigte und hochgeladene Sperren angezeigt.',
    connecting: 'Live-Daten werden verbunden',
    releaseLink: 'GitHub Releases · Plugin herunterladen',
    connected: 'Live-Daten verbunden',
    reconnecting: 'Verbindung getrennt, automatischer Neuversuch',
    loadFailed: 'Serverdaten konnten nicht geladen werden',
    banTab: 'Ban-Liste', keywordTab: 'Schlüsselwortanalyse', targetTab: 'Spam-Werbeziele',
    confirmedTotal: 'Bestätigte Bans', allRecords: 'Alle empfangenen Einträge',
    todayAdded: 'Heute hinzugefügt', serverDate: 'Lokales Serverdatum',
    onlineUsers: 'Online-Plugin-Nutzer', onlineWindow: 'Heartbeat in den letzten 2 Minuten',
    cumulativeUsers: 'Plugin-Nutzer gesamt', anonymousInstalls: 'Unterschiedliche anonyme Installationen',
    blockRecords: 'Sperreinträge', searchLabel: 'Benutzernamen suchen',
    searchPlaceholder: '@username suchen',
    account: 'Konto', matchedRule: 'Schlüsselwort / Regel',
    contentSummary: 'Inhaltsübersicht', blockedAt: 'Sperrzeit',
    noBlocks: 'Noch keine Sperreinträge',
    noBlocksHint: 'Bestätigte Bans erscheinen hier in Echtzeit.',
    previous: 'Zurück', next: 'Weiter',
    range: '{start}–{end} von {total}',
    keywordRanking: 'Schlüsselwort-Rangliste',
    rankingNote: 'Nach Treffern sortiert und mit Ban-Daten aktualisiert',
    rank: 'Rang', keyword: 'Schlüsselwort', hitCount: 'Treffer',
    noKeywords: 'Keine Schlüsselwortstatistik',
    noKeywordsHint: 'Die Rangliste erscheint, wenn ein Treffer zu einem Ban führt.',
    syncPreview: 'Synchronisierbare beliebte Begriffe', syncPreviewNote: 'Das Plugin lädt genau diese Begriffe',
    syncTerm: 'Synchronisierter Sperrbegriff', source: 'Quelle', count: 'Anzahl',
    keywordSource: 'Schlüsselworttreffer', mentionSource: '@Konto-Erwähnung', combinedSource: 'Treffer + @Erwähnung',
    noPopularTerms: 'Keine Begriffe zum Synchronisieren', mentionRanking: '@Konto-Erwähnungen',
    mentionNote: 'In gesperrten Spam-Inhalten erwähnte Konten',
    mentionedAccount: 'Erwähntes Konto', mentionCount: 'Erwähnungen',
    noMentions: 'Keine @Konto-Statistik',
    structuredRule: 'Strukturierte Regel', backToTop: 'Nach oben'
  },
  fr: {
    pageTitle: 'AutoBanRobot · Liste des blocages',
    heroTitle: 'Liste des blocages en temps réel',
    subtitle: 'Seuls les blocages confirmés par X et envoyés au serveur sont affichés.',
    connecting: 'Connexion aux données en temps réel',
    releaseLink: 'GitHub Releases · Télécharger le plugin',
    connected: 'Données en temps réel connectées',
    reconnecting: 'Connexion perdue, nouvelle tentative automatique',
    loadFailed: 'Impossible de charger les données du serveur',
    banTab: 'Blocages', keywordTab: 'Analyse des mots-clés', targetTab: 'Cibles de promotion spam',
    confirmedTotal: 'Blocages confirmés', allRecords: 'Tous les enregistrements reçus',
    todayAdded: 'Ajouts du jour', serverDate: 'Date locale du serveur',
    onlineUsers: 'Utilisateurs du plugin en ligne', onlineWindow: 'Heartbeat reçu dans les 2 dernières minutes',
    cumulativeUsers: 'Utilisateurs cumulés', anonymousInstalls: 'Installations anonymes distinctes',
    blockRecords: 'Enregistrements', searchLabel: 'Rechercher un identifiant',
    searchPlaceholder: 'Rechercher @username',
    account: 'Compte', matchedRule: 'Mot-clé / règle',
    contentSummary: 'Résumé du contenu', blockedAt: 'Heure du blocage',
    noBlocks: 'Aucun blocage pour le moment',
    noBlocksHint: 'Les blocages confirmés apparaissent ici en temps réel.',
    previous: 'Précédent', next: 'Suivant',
    range: '{start}–{end} sur {total}',
    keywordRanking: 'Classement des mots-clés',
    rankingNote: 'Trié par correspondances et actualisé avec les blocages',
    rank: 'Rang', keyword: 'Mot-clé', hitCount: 'Correspondances',
    noKeywords: 'Aucune statistique de mot-clé',
    noKeywordsHint: 'Le classement apparaît lorsqu’une correspondance produit un blocage.',
    syncPreview: 'Termes populaires synchronisables', syncPreviewNote: 'Le plugin charge exactement ces termes',
    syncTerm: 'Terme de blocage', source: 'Source', count: 'Nombre',
    keywordSource: 'Correspondance', mentionSource: 'Mention de @compte', combinedSource: 'Correspondance + mention',
    noPopularTerms: 'Aucun terme à synchroniser', mentionRanking: 'Classement des mentions',
    mentionNote: 'Comptes mentionnés dans les contenus spam bloqués',
    mentionedAccount: 'Compte mentionné', mentionCount: 'Mentions',
    noMentions: 'Aucune statistique de mention',
    structuredRule: 'Règle structurée', backToTop: 'Retour en haut'
  },
  ru: {
    pageTitle: 'AutoBanRobot · Список блокировок',
    heroTitle: 'Список блокировок в реальном времени',
    subtitle: 'Показаны только подтверждённые X и загруженные на сервер блокировки.',
    connecting: 'Подключение к данным в реальном времени',
    releaseLink: 'GitHub Releases · Скачать плагин',
    connected: 'Данные в реальном времени подключены',
    reconnecting: 'Соединение потеряно, выполняется повторная попытка',
    loadFailed: 'Не удалось загрузить данные сервера',
    banTab: 'Блокировки', keywordTab: 'Аналитика ключевых слов', targetTab: 'Целевые аккаунты спам-рекламы',
    confirmedTotal: 'Подтверждённые блокировки', allRecords: 'Все полученные записи',
    todayAdded: 'Добавлено сегодня', serverDate: 'Локальная дата сервера',
    onlineUsers: 'Пользователи плагина онлайн', onlineWindow: 'Heartbeat за последние 2 минуты',
    cumulativeUsers: 'Всего пользователей плагина', anonymousInstalls: 'Уникальные анонимные установки',
    blockRecords: 'Записи блокировок', searchLabel: 'Поиск имени пользователя',
    searchPlaceholder: 'Поиск @username',
    account: 'Аккаунт', matchedRule: 'Ключевое слово / правило',
    contentSummary: 'Краткое содержание', blockedAt: 'Время блокировки',
    noBlocks: 'Записей пока нет',
    noBlocksHint: 'Подтверждённые блокировки появятся здесь в реальном времени.',
    previous: 'Назад', next: 'Далее',
    range: '{start}–{end} из {total}',
    keywordRanking: 'Рейтинг ключевых слов',
    rankingNote: 'Сортировка по совпадениям, обновление по данным Ban',
    rank: 'Место', keyword: 'Ключевое слово', hitCount: 'Совпадения',
    noKeywords: 'Нет статистики ключевых слов',
    noKeywordsHint: 'Рейтинг появится, когда совпадение приведёт к блокировке.',
    syncPreview: 'Популярные термины для синхронизации', syncPreviewNote: 'Плагин загрузит именно эти термины',
    syncTerm: 'Термин блокировки', source: 'Источник', count: 'Количество',
    keywordSource: 'Совпадение', mentionSource: 'Упоминание @аккаунта', combinedSource: 'Совпадение + упоминание',
    noPopularTerms: 'Нет терминов для синхронизации', mentionRanking: 'Рейтинг упоминаний',
    mentionNote: 'Аккаунты в заблокированном спам-контенте',
    mentionedAccount: 'Упомянутый аккаунт', mentionCount: 'Упоминания',
    noMentions: 'Нет статистики упоминаний',
    structuredRule: 'Структурированное правило', backToTop: 'Наверх'
  },
  it: {
    pageTitle: 'AutoBanRobot · Elenco Ban',
    heroTitle: 'Elenco Ban in tempo reale',
    subtitle: 'Sono mostrati solo i blocchi confermati da X e caricati sul server.',
    connecting: 'Connessione ai dati in tempo reale',
    releaseLink: 'GitHub Releases · Scarica il plugin',
    connected: 'Dati in tempo reale connessi',
    reconnecting: 'Connessione interrotta, nuovo tentativo automatico',
    loadFailed: 'Impossibile caricare i dati del server',
    banTab: 'Elenco Ban', keywordTab: 'Analisi parole chiave', targetTab: 'Account obiettivo dello spam',
    confirmedTotal: 'Ban confermati', allRecords: 'Tutti i record ricevuti',
    todayAdded: 'Aggiunti oggi', serverDate: 'Data locale del server',
    onlineUsers: 'Utenti plugin online', onlineWindow: 'Heartbeat negli ultimi 2 minuti',
    cumulativeUsers: 'Utenti plugin cumulativi', anonymousInstalls: 'Installazioni anonime distinte',
    blockRecords: 'Record dei blocchi', searchLabel: 'Cerca nome utente',
    searchPlaceholder: 'Cerca @username',
    account: 'Account', matchedRule: 'Parola chiave / regola',
    contentSummary: 'Riepilogo contenuto', blockedAt: 'Ora del blocco',
    noBlocks: 'Nessun blocco registrato',
    noBlocksHint: 'I Ban confermati appariranno qui in tempo reale.',
    previous: 'Precedente', next: 'Successivo',
    range: '{start}–{end} di {total}',
    keywordRanking: 'Classifica parole chiave',
    rankingNote: 'Ordinata per corrispondenze e aggiornata con i record Ban',
    rank: 'Posizione', keyword: 'Parola chiave', hitCount: 'Corrispondenze',
    noKeywords: 'Nessuna statistica',
    noKeywordsHint: 'La classifica appare quando una corrispondenza genera un Ban.',
    syncPreview: 'Termini popolari sincronizzabili', syncPreviewNote: 'Il plugin caricherà esattamente questi termini',
    syncTerm: 'Termine di blocco', source: 'Origine', count: 'Conteggio',
    keywordSource: 'Corrispondenza', mentionSource: 'Menzione @account', combinedSource: 'Corrispondenza + menzione',
    noPopularTerms: 'Nessun termine da sincronizzare', mentionRanking: 'Classifica menzioni',
    mentionNote: 'Account menzionati nei contenuti spam bloccati',
    mentionedAccount: 'Account menzionato', mentionCount: 'Menzioni',
    noMentions: 'Nessuna statistica delle menzioni',
    structuredRule: 'Regola strutturata', backToTop: 'Torna su'
  }
};

function normalizeLanguage(value) {
  if (translations[value]) return value;
  const prefix = String(value || '').toLocaleLowerCase().split('-')[0];
  return Object.keys(translations).find(language =>
    language.toLocaleLowerCase().split('-')[0] === prefix
  ) || 'zh-CN';
}

let currentLanguage = normalizeLanguage(
  localStorage.getItem(LANGUAGE_KEY) || navigator.language
);
let connectionTextKey = 'connecting';

function t(key, values = {}) {
  let text = translations[currentLanguage]?.[key] ??
    translations['zh-CN'][key] ?? key;
  Object.entries(values).forEach(([name, value]) => {
    text = text.replaceAll(`{${name}}`, value);
  });
  return text;
}

function applyLanguage(language) {
  currentLanguage = normalizeLanguage(language);
  localStorage.setItem(LANGUAGE_KEY, currentLanguage);
  document.documentElement.lang = currentLanguage;
  document.title = t('pageTitle');
  document.querySelectorAll('[data-i18n]').forEach(element => {
    element.textContent = t(element.dataset.i18n);
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
    element.placeholder = t(element.dataset.i18nPlaceholder);
  });
  document.querySelectorAll('[data-i18n-aria-label]').forEach(element => {
    const label = t(element.dataset.i18nAriaLabel);
    element.setAttribute('aria-label', label);
    element.title = label;
  });
  document.querySelectorAll('[data-lang]').forEach(button => {
    button.classList.toggle('active', button.dataset.lang === currentLanguage);
  });
  document.getElementById('connectionText').textContent =
    t(connectionTextKey);
  renderPager();
}

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
  onlineUsers: document.getElementById('onlineUsers'),
  cumulativeUsers: document.getElementById('cumulativeUsers'),
  backToTop: document.getElementById('backToTop')
};

const keywordElements = {
  rows: document.getElementById('keywordRows'),
  empty: document.getElementById('keywordEmpty'),
  template: document.getElementById('keywordRowTemplate')
};

const popularElements = {
  rows: document.getElementById('popularRows'),
  empty: document.getElementById('popularEmpty'),
  template: document.getElementById('popularRowTemplate')
};

const mentionElements = {
  rows: document.getElementById('mentionRows'),
  empty: document.getElementById('mentionEmpty'),
  template: document.getElementById('mentionRowTemplate')
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

async function loadUserStats() {
  const data = await fetchJson('/api/clients/stats');
  elements.onlineUsers.textContent = data.onlineUsers.toLocaleString();
  elements.cumulativeUsers.textContent = data.cumulativeUsers.toLocaleString();
}

async function loadKeywords() {
  const items = await fetchJson('/api/keywords?limit=200');
  keywordElements.rows.replaceChildren();
  keywordElements.empty.hidden = items.length !== 0;
  items.forEach(item => {
    const row = keywordElements.template.content.cloneNode(true);
    row.querySelector('.rank').textContent = `#${item.rank}`;
    row.querySelector('.keyword').textContent = item.keyword;
    row.querySelector('.hit-count').textContent =
      item.hitCount.toLocaleString();
    keywordElements.rows.appendChild(row);
  });
}

async function loadPopularTerms() {
  const items = await fetchJson('/api/popular-terms?limit=200');
  popularElements.rows.replaceChildren();
  popularElements.empty.hidden = items.length !== 0;
  items.forEach(item => {
    const row = popularElements.template.content.cloneNode(true);
    row.querySelector('.rank').textContent = `#${item.rank}`;
    row.querySelector('.term').textContent = item.term;
    const sourceKey = item.source === 'mention'
      ? 'mentionSource'
      : item.source === 'keyword_and_mention'
        ? 'combinedSource'
        : 'keywordSource';
    row.querySelector('.source').textContent = t(sourceKey);
    row.querySelector('.count').textContent = item.count.toLocaleString();
    popularElements.rows.appendChild(row);
  });
}

async function loadMentions() {
  const items = await fetchJson('/api/mentions?limit=200');
  mentionElements.rows.replaceChildren();
  mentionElements.empty.hidden = items.length !== 0;
  items.forEach(item => {
    const row = mentionElements.template.content.cloneNode(true);
    row.querySelector('.rank').textContent = `#${item.rank}`;
    const account = row.querySelector('.mentioned-account');
    account.href = `https://x.com/${encodeURIComponent(item.username)}`;
    account.textContent = `@${item.username}`;
    row.querySelector('.mention-count').textContent =
      item.mentionCount.toLocaleString();
    mentionElements.rows.appendChild(row);
  });
}

async function loadAnalytics() {
  await Promise.all([loadKeywords(), loadPopularTerms(), loadMentions()]);
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
      : item.reason || t('structuredRule');
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
  const locale = currentLanguage;
  elements.range.textContent = t('range', {
    start: start.toLocaleString(locale),
    end: end.toLocaleString(locale),
    total: state.total.toLocaleString(locale)
  });
  elements.previous.disabled = state.page <= 0;
  elements.next.disabled =
    state.totalPages === 0 || state.page >= state.totalPages - 1;
}

function setConnection(mode, textKey) {
  elements.connection.classList.remove('live', 'offline');
  if (mode) elements.connection.classList.add(mode);
  connectionTextKey = textKey;
  elements.connectionText.textContent = t(connectionTextKey);
}

function connectStream() {
  const stream = new EventSource('/api/bans/stream');
  stream.addEventListener('connected', () => {
    setConnection('live', 'connected');
  });
  stream.addEventListener('ban', async event => {
    const record = JSON.parse(event.data);
    if (!state.query && state.page === 0) {
      await Promise.all([loadPage(), loadStats(), loadAnalytics()]);
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
    setConnection('offline', 'reconnecting');
  };
}

function applyPage(page, updateUrl = true, replaceUrl = false) {
  const selectedPage = ['bans', 'keywords', 'targets'].includes(page) ? page : 'bans';
  document.querySelectorAll('.page-tab').forEach(tab => {
    tab.classList.toggle('active', tab.dataset.page === selectedPage);
  });
  document.getElementById('bansPage').hidden = selectedPage !== 'bans';
  document.getElementById('keywordsPage').hidden = selectedPage !== 'keywords';
  document.getElementById('targetsPage').hidden = selectedPage !== 'targets';
  localStorage.setItem(PAGE_KEY, selectedPage);
  if (updateUrl) {
    const url = `#${selectedPage}`;
    if (replaceUrl) {
      history.replaceState(null, '', url);
    } else {
      history.pushState(null, '', url);
    }
  }
}

document.querySelectorAll('.page-tab').forEach(button => {
  button.addEventListener('click', () => applyPage(button.dataset.page));
});

window.addEventListener('popstate', () => {
  applyPage(location.hash.slice(1), false);
});

window.addEventListener('hashchange', () => {
  applyPage(location.hash.slice(1), false);
});

function updateBackToTopVisibility() {
  elements.backToTop.hidden = window.scrollY < 360;
}

elements.backToTop.addEventListener('click', () => {
  window.scrollTo({ top: 0, behavior: 'smooth' });
});
window.addEventListener('scroll', updateBackToTopVisibility, { passive: true });
updateBackToTopVisibility();

document.querySelectorAll('[data-lang]').forEach(button => {
  button.addEventListener('click', async () => {
    applyLanguage(button.dataset.lang);
    await Promise.all([loadPage(), loadAnalytics()]);
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

Promise.all([loadPage(), loadStats(), loadAnalytics(), loadUserStats()])
  .then(connectStream)
  .catch(error => {
    console.error(error);
    setConnection('offline', 'loadFailed');
  });

setInterval(() => {
  loadUserStats().catch(error => console.error('用户统计刷新失败', error));
}, 15_000);

const initialPage = ['bans', 'keywords', 'targets'].includes(location.hash.slice(1))
  ? location.hash.slice(1)
  : localStorage.getItem(PAGE_KEY);
applyPage(initialPage, true, true);
applyLanguage(currentLanguage);
