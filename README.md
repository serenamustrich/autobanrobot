# AutoBanRobot

Twitter/X spam-account blocker for Chromium browsers.

[中文](#中文) · [English](#english) · [Español](#español) · [日本語](#日本語) · [한국어](#한국어) · [Deutsch](#deutsch) · [Français](#français) · [Русский](#русский) · [Italiano](#italiano)

> This extension performs real account blocks through the logged-in Twitter/X session. Review your keyword list before enabling it.

## 中文

AutoBanRobot 是一款适用于 Chromium 浏览器的 Twitter/X 垃圾账号自动屏蔽扩展。

### 功能

- 扫描当前页面及动态加载的推文和回复。
- 当用户名、显示名称或发布内容命中关键词时，自动屏蔽对应账号。
- 当发布内容去除空白后只有一个完整 Emoji 时，自动屏蔽对应账号。
- 支持在扩展弹窗中添加自定义关键词，保存后立即重新扫描当前页面。
- 内置常见垃圾推广关键词，并记录成功屏蔽数量。
- 同时支持 `twitter.com` 和 `x.com`。

### 安装

1. 下载或克隆本仓库。
2. 打开 Chrome、Edge 或其他 Chromium 浏览器的扩展管理页面。
3. 开启“开发者模式”。
4. 点击“加载已解压的扩展程序”，选择本仓库目录。
5. 登录 Twitter/X 并正常浏览。

### 使用与注意事项

点击工具栏中的扩展图标即可编辑关键词，每行填写一个。扩展使用当前 Twitter/X 登录会话执行真实屏蔽操作。关键词过于宽泛可能造成误屏蔽，请谨慎配置。本项目与 X Corp. 无关。

## English

AutoBanRobot is a Twitter/X spam-account blocker for Chromium-based browsers.

### Features

- Scans tweets and replies already on the page and those loaded dynamically.
- Blocks an account when its username, display name, or post content matches a keyword.
- Blocks an account when the post, after whitespace is removed, consists of exactly one complete emoji.
- Supports custom keywords from the extension popup and immediately rescans the current page after saving.
- Includes built-in spam keywords and keeps a successful-block counter.
- Works on both `twitter.com` and `x.com`.

### Installation

1. Download or clone this repository.
2. Open the extensions page in Chrome, Edge, or another Chromium browser.
3. Enable Developer mode.
4. Choose “Load unpacked” and select this repository directory.
5. Sign in to Twitter/X and browse normally.

### Usage and warning

Open the toolbar popup to edit keywords, one per line. The extension uses your active Twitter/X session to perform real account blocks. Broad keywords may cause false positives, so review them carefully. This project is not affiliated with X Corp.

## Español

AutoBanRobot es una extensión para navegadores basados en Chromium que bloquea automáticamente cuentas de spam en Twitter/X.

### Funciones

- Analiza publicaciones y respuestas visibles o cargadas dinámicamente.
- Bloquea una cuenta si el nombre de usuario, el nombre visible o el contenido coincide con una palabra clave.
- Bloquea una cuenta cuando el contenido, sin espacios, contiene exactamente un solo emoji completo.
- Permite añadir palabras clave personalizadas y vuelve a analizar la página inmediatamente después de guardarlas.
- Incluye palabras clave antispam y un contador de bloqueos realizados.
- Funciona en `twitter.com` y `x.com`.

### Instalación y uso

Descarga o clona el repositorio, activa el modo de desarrollador en la página de extensiones de tu navegador y selecciona “Cargar descomprimida”. Abre el icono de la extensión para editar una palabra clave por línea. La extensión realiza bloqueos reales mediante tu sesión activa; revisa las reglas para evitar falsos positivos. Este proyecto no está afiliado a X Corp.

## 日本語

AutoBanRobot は、Chromium 系ブラウザー向けの Twitter/X スパムアカウント自動ブロック拡張機能です。

### 機能

- 表示中および動的に読み込まれた投稿・返信を監視します。
- ユーザー名、表示名、投稿内容のいずれかがキーワードに一致すると、そのアカウントをブロックします。
- 空白を除いた投稿内容が完全な Emoji 1個だけの場合もブロックします。
- ポップアップからキーワードを追加でき、保存すると現在のページを直ちに再スキャンします。
- スパム用の組み込みキーワードとブロック件数表示を備えています。
- `twitter.com` と `x.com` の両方に対応します。

### インストールと注意

このリポジトリをダウンロードまたはクローンし、ブラウザーの拡張機能ページでデベロッパーモードを有効にして、「パッケージ化されていない拡張機能を読み込む」からフォルダーを選択してください。本拡張機能はログイン中のセッションで実際にアカウントをブロックします。誤検知を避けるため、キーワードを慎重に確認してください。本プロジェクトは X Corp. とは関係ありません。

## 한국어

AutoBanRobot은 Chromium 기반 브라우저에서 동작하는 Twitter/X 스팸 계정 자동 차단 확장 프로그램입니다.

### 기능

- 현재 페이지와 동적으로 불러온 게시물 및 답글을 검사합니다.
- 사용자 이름, 표시 이름 또는 게시물 내용이 키워드와 일치하면 해당 계정을 차단합니다.
- 공백을 제거한 게시물 내용이 완전한 이모지 하나뿐인 경우에도 차단합니다.
- 팝업에서 사용자 키워드를 추가할 수 있으며 저장 즉시 현재 페이지를 다시 검사합니다.
- 기본 스팸 키워드와 성공한 차단 횟수 표시를 제공합니다.
- `twitter.com`과 `x.com`을 모두 지원합니다.

### 설치 및 주의사항

저장소를 다운로드하거나 복제한 뒤 브라우저 확장 프로그램 페이지에서 개발자 모드를 켜고 “압축해제된 확장 프로그램을 로드합니다”를 선택하세요. 이 확장 프로그램은 로그인된 세션을 사용해 실제 계정 차단을 수행합니다. 오탐을 방지하려면 키워드를 신중하게 검토하세요. 이 프로젝트는 X Corp.와 관련이 없습니다.

## Deutsch

AutoBanRobot ist eine Erweiterung für Chromium-Browser, die Spam-Konten auf Twitter/X automatisch blockiert.

### Funktionen

- Prüft sichtbare sowie dynamisch geladene Beiträge und Antworten.
- Blockiert ein Konto, wenn Benutzername, Anzeigename oder Beitragsinhalt einem Schlüsselwort entspricht.
- Blockiert ein Konto, wenn der Beitrag nach dem Entfernen von Leerzeichen aus genau einem vollständigen Emoji besteht.
- Unterstützt eigene Schlüsselwörter und durchsucht die aktuelle Seite nach dem Speichern sofort erneut.
- Enthält integrierte Spam-Schlüsselwörter und einen Zähler erfolgreicher Blockierungen.
- Funktioniert auf `twitter.com` und `x.com`.

### Installation und Hinweis

Repository herunterladen oder klonen, den Entwicklermodus auf der Erweiterungsseite aktivieren und „Entpackte Erweiterung laden“ wählen. Die Erweiterung führt über die angemeldete Sitzung echte Kontoblockierungen aus. Zu allgemeine Schlüsselwörter können Fehlblockierungen verursachen. Dieses Projekt steht in keiner Verbindung zu X Corp.

## Français

AutoBanRobot est une extension pour navigateurs Chromium qui bloque automatiquement les comptes indésirables sur Twitter/X.

### Fonctionnalités

- Analyse les publications et réponses visibles ou chargées dynamiquement.
- Bloque un compte lorsque son identifiant, son nom affiché ou son contenu correspond à un mot-clé.
- Bloque un compte lorsque le contenu, une fois les espaces retirés, contient exactement un seul emoji complet.
- Accepte des mots-clés personnalisés et réanalyse immédiatement la page après leur enregistrement.
- Inclut des mots-clés antispam et un compteur de blocages réussis.
- Fonctionne sur `twitter.com` et `x.com`.

### Installation et avertissement

Téléchargez ou clonez le dépôt, activez le mode développeur sur la page des extensions puis choisissez « Charger l’extension non empaquetée ». L’extension effectue de véritables blocages avec votre session connectée. Vérifiez soigneusement les mots-clés afin d’éviter les faux positifs. Ce projet n’est pas affilié à X Corp.

## Русский

AutoBanRobot — расширение для браузеров на базе Chromium, автоматически блокирующее спам-аккаунты в Twitter/X.

### Возможности

- Проверяет видимые и динамически загружаемые публикации и ответы.
- Блокирует аккаунт, если имя пользователя, отображаемое имя или текст публикации совпадает с ключевым словом.
- Блокирует аккаунт, если после удаления пробелов публикация состоит ровно из одного полноценного эмодзи.
- Поддерживает пользовательские ключевые слова и сразу повторно проверяет текущую страницу после сохранения.
- Содержит встроенные антиспам-слова и счётчик успешных блокировок.
- Работает на `twitter.com` и `x.com`.

### Установка и предупреждение

Скачайте или клонируйте репозиторий, включите режим разработчика на странице расширений и выберите «Загрузить распакованное расширение». Расширение выполняет реальные блокировки через активный сеанс Twitter/X. Тщательно проверяйте ключевые слова, чтобы избежать ложных срабатываний. Проект не связан с X Corp.

## Italiano

AutoBanRobot è un’estensione per browser Chromium che blocca automaticamente gli account spam su Twitter/X.

### Funzionalità

- Analizza post e risposte visibili o caricati dinamicamente.
- Blocca un account quando nome utente, nome visualizzato o contenuto corrispondono a una parola chiave.
- Blocca un account quando il contenuto, rimossi gli spazi, è composto esattamente da una sola emoji completa.
- Supporta parole chiave personalizzate e riesamina subito la pagina corrente dopo il salvataggio.
- Include parole chiave antispam integrate e un contatore dei blocchi riusciti.
- Funziona su `twitter.com` e `x.com`.

### Installazione e avvertenza

Scarica o clona il repository, abilita la modalità sviluppatore nella pagina delle estensioni e scegli “Carica estensione non pacchettizzata”. L’estensione esegue blocchi reali tramite la sessione Twitter/X attiva. Controlla attentamente le parole chiave per evitare falsi positivi. Il progetto non è affiliato a X Corp.

## License

[MIT](LICENSE)
