# AutoBanRobot

<img src="icon.png" alt="AutoBanRobot cat assistant icon" width="160">

Twitter/X spam-account blocker for Chrome, Microsoft Edge, and Safari.

[中文](#中文) · [English](#english) · [Español](#español) · [日本語](#日本語) · [한국어](#한국어) · [Deutsch](#deutsch) · [Français](#français) · [Русский](#русский) · [Italiano](#italiano)

> This extension performs real account blocks through the logged-in Twitter/X session. Review your keyword list before enabling it.

> **Safari notice:** the Safari download contains adaptation source code and an
> Xcode project, not a ready-to-install app. Safari users must build and package
> it themselves with Xcode. Chrome and Microsoft Edge use the ready-to-load
> Chromium package.
>
> **Safari 说明：**Safari 下载包是适配源码和 Xcode 工程，不是可直接安装的应用。
> Safari 用户需要自行使用 Xcode 构建和打包；Chrome 与 Microsoft Edge 使用
> 可直接加载的 Chromium 插件包。

## Repository branches / 仓库分支

- [`main`](https://github.com/serenamustrich/autobanrobot/tree/main): the single shared Chromium codebase for both Chrome and Microsoft Edge. Chrome and Edge use the same source files, manifest, features, and release package; they are not separate implementations.
- [`safari`](https://github.com/serenamustrich/autobanrobot/tree/safari): Safari adaptation and packaging source. Safari-specific code is maintained in the `safari/` directory on that branch.

- [`main`](https://github.com/serenamustrich/autobanrobot/tree/main)：Chrome 与 Microsoft Edge 共用的同一套 Chromium 源码。两者使用完全相同的代码、Manifest、功能和发布包，不是两个独立实现。
- [`safari`](https://github.com/serenamustrich/autobanrobot/tree/safari)：Safari 适配与打包源码；Safari 专用代码统一维护在该分支的 `safari/` 目录。

## Release notes / 更新说明

### Server v1.2.2 — 2026-08-01

- Added the dedicated spam-promotion target dashboard, full popular-term
  synchronization without a top-50 cap, and cleanup of X timestamp suffixes
  from displayed account names.
- 服务端升级至 1.2.2：新增独立的垃圾推广目标账号页面，热门词同步不再限制
  前 50 条，并清理账号显示名称末尾误采集的 X 发布时间。

### Server v1.2.3 — 2026-08-02

- 将“可同步热门关键词”独立为“热门关键词”页面，关键词分析页仅保留命中排行。
- Server v1.2.3 separates the syncable popular-keyword dashboard from keyword analytics.

### Server v1.2.4 — 2026-08-02

- 为服务端页面脚本增加版本化地址，避免新菜单 HTML 搭配浏览器缓存的旧 JavaScript，确保多语言和页面路由立即生效。

### v1.6.18 — 2026-08-01

- Fixed X relative timestamps and dates being appended to captured display
  names. Identity extraction now reads only the account profile-name link.
- 修复 X 的“分钟、小时、日期”等发布时间被错误拼入账号显示名称的问题；
  账号识别现在只读取用户资料名称链接。

### v1.6.17 — 2026-08-01

- Removed the top-50 cap from popular-term loading. The server now returns all
  current keyword hits and mentioned promotion-target accounts to the plugin.
- 移除热门词加载的前 50 条限制；服务端现在会把当前全部关键词命中项及
  垃圾推广目标账号提供给插件加载。
- Added a dedicated `Spam Promotion Targets` dashboard page for accounts most
  frequently mentioned by confirmed spam, separate from keyword analytics.
- Added a floating back-to-top control and preserved the selected dashboard
  page across refreshes, direct links, and browser Back/Forward navigation.
- Updated the server-managed `Emoji + content + Emoji` regular expression to
  require at least one Unicode letter or number between the outer Emoji.
  Emoji-only posts no longer match, without requiring an extension update.
- Tests cover Emoji-only content, Emoji surrounding Chinese or Latin text, and
  punctuation-only middle segments.
- 服务端看板新增独立的“垃圾推广目标账号”菜单，集中展示已确认垃圾内容中
  被提及次数最多的账号，不再与关键词分析混在同一页面。
- “可同步热门关键词”现已独立为“热门关键词”菜单，位于“关键词分析”和
  “垃圾推广目标账号”之间；关键词分析页仅保留关键词命中排行。
- 新增右下角悬浮回到顶部按钮；刷新、直接访问及浏览器前进后退时均保留当前菜单。
- 在线更新“Emoji + 内容 + Emoji”正则：首尾 Emoji 之间必须至少包含一个任意语言的
  文字或数字；整条内容全部由 Emoji、空白或符号组成时不再命中，无需更新插件。
- 已覆盖纯 Emoji、Emoji 包围中英文内容以及中间仅标点符号等回归场景。

### v1.6.16 — 2026-08-01

- Added a per-account Unblock action to the confirmed Ban history. The action
  checks the current X relationship, submits the unblock request, and marks the
  record as unblocked only after X confirms `blocking=false`.
- Pending block jobs for the same account are removed before unblocking to
  prevent an immediate re-block. The cumulative blocked counter remains an
  immutable historical count.
- 已确认 Ban 清单中的每个账号新增“取消屏蔽”按钮。插件先检查当前关系，提交
  取消请求，并且只在 X 再次确认 `blocking=false` 后标记为“已取消屏蔽”。
- 取消前会移除同账号尚未执行的屏蔽任务，避免刚解除又被队列重新屏蔽；
  “累计已屏蔽”继续表示历史成功次数，不会回退。

### v1.6.15 — 2026-08-01

- Prevented stale X-page content scripts from throwing an uncaught `Extension
  context invalidated` error after the extension is reloaded or updated.
- The enqueue bridge now handles both synchronous API failures and asynchronous
  message rejections; stale pages stop submitting quietly until refreshed.
- 修复插件重新加载或更新后，旧 X 页面内容脚本抛出未捕获的
  `Extension context invalidated` 错误。
- 入队通信现在同时处理同步抛错和异步拒绝；旧页面会安静停止提交，刷新该 X 页面后
  即恢复为新版本脚本。

### v1.6.14 — 2026-08-01

- Redesigned the popup into four focused tabs: Keywords, Rules, Ban history,
  and Updates. Large keyword and rule collections no longer compete for space
  in one long page.
- Added live keyword and enabled-rule counts, presented all rules in one
  unified updateable list, and remembered the last selected tab.
- Migrated the four former built-in rules into the same server-managed rule
  configuration. Their existing disabled states are preserved automatically.
- 插件弹窗改为“关键词 / 规则 / Ban / 更新”四个标签页，关键词和规则较多时
  不再全部挤在同一个长页面中。
- 新增关键词数量、已启用规则数量展示，所有规则统一显示、统一在线更新，并记住
  上次打开的标签页。
- 原先写死的 4 条规则已迁入服务端规则清单；以后规则统一由服务端新增、删除、
  调整和热更新，升级时会自动保留用户原来关闭过的规则状态。

### v1.6.13 — 2026-08-01

- Extended server-managed hot rules with `content`, `username`, and
  `displayName` scopes plus an optional default-avatar requirement.
- Historical analysis of 1,297 distinct confirmed blocked accounts found 850
  (65.5%) in a generated CamelCase English-name cluster. Of 837 distinct
  three-character endings in that cluster, 824 occurred only once, strongly
  indicating randomized account generation.
- Added separate switches for the strict `name + 2 digits + 2 letters + 1 digit`
  ID pattern, the broader CamelCase random-tail pattern combined with a default
  avatar, and the previously added praise-solicitation content pattern.
- 在线规则新增 `content`、`username`、`displayName` 匹配范围，
  并可选要求账号使用默认头像。
- 分析 1,297 个已确认屏蔽的不同账号后，发现 850 个（65.5%）
  属于 CamelCase 英文姓名生成簇；该簇 837 种不同的末三位中，
  824 种仅出现一次，随机生成特征明显。
- 严格数字尾 ID、“CamelCase 随机尾 + 默认头像”以及夸赞引流文案
  保留为三个独立滑块规则，可分别关闭。

### v1.6.12 — 2026-08-01

- Added server-managed hot detection rules. The extension loads a bundled safe
  fallback, fetches versioned JSON rules from `https://ban.richccy.com/api/rules`
  at startup and every five minutes, caches the last successful response, and
  immediately rescans the current X page after a rule change.
- Online rules are displayed as normal slider switches in the popup and can be
  refreshed manually. New language-independent regular-expression rules can be
  added or changed without publishing another extension package.
- The Java 21 server now persists rule versions in MySQL 5.7 `MEDIUMTEXT` and
  exposes a public read endpoint plus a protected update endpoint. Updates
  require the deployment-only `AUTOBAN_RULE_ADMIN_TOKEN`; arbitrary remote
  JavaScript is never accepted or executed.
- 新增服务端管理的检测规则热更新。插件内置安全兜底规则，启动时及
  每 5 分钟从 `https://ban.richccy.com/api/rules` 拉取版本化 JSON，
  成功后缓存并立即重新扫描当前 X 页面；断网时继续使用上次规则。
- 在线规则会以滑块开关显示在插件中，也可手动点击“立即更新规则”。
  以后新增或调整这类正则检测无需重新发布插件。
- Java 21 服务端使用 MySQL 5.7 `MEDIUMTEXT` 保存规则和版本，
  写入接口必须通过部署环境中的 `AUTOBAN_RULE_ADMIN_TOKEN` 验证；
  服务端不会下发或执行任意 JavaScript。

### v1.6.11 — 2026-08-01

- Generalized the short-link rule so it no longer depends on fixed phrases or
  language. It now detects the shared spam structure: meaningful copy containing
  multiple Emoji plus a standalone randomized `t.cn/<code>` link line.
- A bare short link, ordinary copy without multiple Emoji, and links embedded
  inside a sentence are excluded to reduce false positives.
- 短链规则改为通用结构识别，不再依赖固定话术、关键词或语言：
  正文包含有意义的文案和多个 Emoji，并有单独成行的 `t.cn/<code>` 随机短链即命中。
- 只有短链、文案中没有多个 Emoji，或短链嵌在普通句子中时不会命中，
  以降低误判。

### v1.6.10 — 2026-08-01

- Added an independently configurable rule for the repeated spam template
  `说的就是这个vlog吧` followed by a randomized `t.cn` short link.
- Emoji and punctuation around the phrase are optional. A match requires both
  the normalized phrase and a valid `t.cn/<code>` link, reducing false positives
  from ordinary vlog discussion or unrelated short links.
- 新增可独立开关的 vlog 短链引流规则：同时出现“说的就是这个vlog吧”
  类引导语和随机 `t.cn/<code>` 短链时命中。
- 引导语前后的 Emoji 和标点可有可无；只有引导语、只有短链或使用其他域名
  都不会命中，以降低误判。

### v1.6.9 — 2026-08-01

- Renamed the popup counter label from “Blocked this session” to “Total
  blocked” so it accurately describes the persisted cumulative `blockCount`.
- 将插件弹窗的“本次已屏蔽”改为“累计已屏蔽”，与实际持久化的
  `blockCount` 统计口径保持一致。

### v1.6.8 — 2026-08-01

- Added an independently configurable three-segment spam rule for posts such as
  `29 → ɞ → 63`, `01 → 🕯 → 99`, and `60 → * → 29`.
- The rule requires exactly three non-empty lines. The first and third may
  contain any non-empty content; the middle must be exactly one grapheme.
- The new slider is enabled by default and retains the user's saved state.
- 新增可独立开关的三段式垃圾内容规则，可识别 `29 → ɞ → 63`、
  `74 → ɞ → 47` 等结构。
- 必须恰好为三个非空行：首尾可以是任意非空内容，中间恰好为一个完整字形。
- 新滑块默认开启，关闭后会保留用户选择，不会自动恢复。

### v1.6.7 — 2026-08-01

- Fixed the browser extension error `Uncaught (in promise) Error: No SW`
  during extension reloads or service-worker shutdown windows.
- Background storage, alarm scheduling, message delivery, and queue processing
  now handle rejected extension API promises without discarding persisted jobs.
- Unexpected API failures remain visible in the console; only expected
  extension-shutdown errors are suppressed.
- 修复插件重载或 Service Worker 关闭窗口期出现的
  `Uncaught (in promise) Error: No SW`。
- 后台存储、定时器、消息发送和队列处理现会完整接住异步 API 拒绝，
  已持久化的 Ban 任务不会丢失。
- 只忽略扩展关闭时的预期错误，其他真实 API 错误仍会保留在控制台。

### v1.6.6 — 2026-08-01

- Pure Han keywords now tolerate inserted Latin letters, numbers, punctuation,
  Emoji, whitespace, and invisible characters. For example,
  `👆她太涩h6了fh6l 我真顶不住 🪐 ❤ c` matches the keyword `她太涩了`.
- Extra Han characters are still significant, so `她今天太涩了` does not falsely match
  `她太涩了`. Mixed-language keywords such as `sao货` keep their meaningful Latin
  characters and do not use the Han-only fallback.
- 纯汉字关键词现允许在汉字之间插入英文字母、数字、标点、Emoji、空格和
  零宽字符；例如 `👆她太涩h6了fh6l 我真顶不住 🪐 ❤ c` 会命中 `她太涩了`。
- 额外汉字仍然会参与匹配，因此 `她今天太涩了` 不会误命中 `她太涩了`；`sao货`
  等中英文混合关键词不会删除有意义的英文部分。

### v1.6.5 — 2026-08-01

- Generalized the five-segment rule to `non-Emoji → Emoji → non-Emoji →
  Emoji → non-Emoji`. The three non-Emoji segments may now contain text, a
  date/time, numbers, or other non-empty content in any position.
- Added symbol-noise-resistant keyword matching. Punctuation, Emoji, whitespace,
  and invisible separators inserted between keyword characters no longer bypass
  a match, while additional letters or CJK characters still prevent a false
  match.
- The existing rule switch and saved keyword list remain unchanged during the
  upgrade.
- 五段式规则改为“非 Emoji → Emoji → 非 Emoji → Emoji → 非
  Emoji”，三个非 Emoji 段可以任意是文字、日期时间、数字或其他非空内容。
- 关键词匹配新增抗符号干扰：在关键词字符之间插入标点、Emoji、空格或
  零宽字符仍会命中；插入额外字母或汉字不会被忽略，避免过度放宽。
- 升级沿用现有规则开关和用户已保存的关键词，不重置配置。

### v1.6.4 — 2026-07-31

- The server dashboard now preserves the active page in the URL hash and local
  storage. Refreshing `#keywords` stays on Keyword Analytics instead of
  returning to the Ban list.
- Expanded the structured Emoji rule from `Emoji + English + Emoji` to
  `Emoji + any non-empty content + Emoji`.
- Mixed Chinese, Latin letters, numbers, punctuation, and additional Emoji are
  now accepted between the leading and trailing Emoji.
- The existing slider setting is preserved, so user preferences are not reset
  during the upgrade.
- 将原“Emoji + 英文 + Emoji”规则扩展为“Emoji + 任意非空内容 + Emoji”。
- 首尾为 Emoji 时，中间可包含中文、字母、数字、标点或其他 Emoji。
- 沿用现有滑块设置键，升级不会重置用户已保存的开关状态。
- 服务端网页会保存当前标签页；在关键词分析页刷新后仍停留在关键词分析，
  不再跳回 Ban 清单。

### v1.6.3 — 2026-07-31

- Added a prominent multilingual GitHub Releases / plugin download entry to
  the server dashboard header.
- Removed the misleading configured-keyword count and Ban-account count from
  keyword analytics. Keyword ranking now shows hit count only.
- Added an `@account` mention ranking derived from content posted by confirmed
  blocked spam accounts, including automatic history backfill.
- Added a clear synchronization preview showing the exact popular terms
  offered to the extension and whether each term comes from a keyword hit,
  an `@account` mention, or both.
- Popular-term loading now includes frequently mentioned `@accounts`, allowing
  the extension to match spam accounts that repeatedly promote the same target.
- Removed the misleading “configured count” and retained full configured
  keyword snapshots only as Ban-event evidence.
- 关键词排行只显示实际命中次数，不再展示容易误解的“被设置次数”和 Ban
  账号数。
- 新增已确认垃圾内容的 `@账号` 提及排行，并自动回填现有历史数据。
- 网页明确展示插件实际可同步的热门屏蔽词及其来源；高频被提及账号会以
  `@username` 形式加入热门词。
- 完整关键词快照仍随 Ban 记录保存，但不再用于排行榜统计。
- 服务端网页顶部新增多语言 GitHub Releases / 插件下载入口。

### v1.6.2 — 2026-07-30

- Switched Ban uploads, anonymous heartbeats, and popular-keyword loading from
  the local test endpoint to the production HTTPS endpoint at
  `https://ban.richccy.com`.
- Added the shared cat-assistant plugin icon as the server dashboard favicon
  and Apple touch icon.
- Fixed Ban uploads being rejected by MySQL 5.7 when the complete configured
  keyword snapshot exceeded the previous text-column capacity.
- Failed uploads remain in the extension's persistent queue and are retried
  automatically after the server schema is corrected. A successful one-minute
  heartbeat also wakes the upload queue, shortening backlog recovery.
- 服务端网页浏览器标签页及 Apple 触控图标改为与插件一致的猫咪助手 icon。
- 修复全部插件关键词快照超过 MySQL 5.7 原字段容量时，Ban 上传被拒绝的问题。
- 已失败记录仍保留在插件持久化上传队列中，服务端字段修复后会自动补传。
- 每分钟心跳成功后也会立即唤醒上传队列，缩短积压记录的恢复等待时间。
- Ban 上传、匿名心跳和热门关键词加载已从本机测试地址统一切换到正式 HTTPS
  服务 `https://ban.richccy.com`。

### v1.6.1 — 2026-07-30

Anonymous plugin user metrics and multilingual server dashboard.

- Replaced the dashboard's generic data-status card with real online plugin
  users and cumulative plugin users.
- Each installation creates one random anonymous installation ID. No Twitter/X
  identity, username, cookie, or authentication credential is used for user
  counting.
- Plugins send one heartbeat per minute. An installation is online when the
  server received its heartbeat during the last two minutes.
- Added complete dashboard switching for Chinese, English, Spanish, Japanese,
  Korean, German, French, Russian, and Italian. The selected language is saved
  in the browser.
- Changed the Chinese dashboard subtitle to “仅展示经过 X 确认成功并上传到服务器的屏蔽记录。”

新增匿名插件用户统计及服务端网页九语言切换：

- 将原“数据状态”卡片替换为真实的“插件在线用户数”和“插件累计用户数”。
- 每个安装实例只生成一个随机匿名 ID；用户统计不会使用 Twitter/X 身份、
  用户名、Cookie 或登录凭证。
- 插件每分钟发送一次心跳，最近两分钟内收到心跳的安装实例计为在线。
- 服务端网页完整支持中文、英语、西班牙语、日语、韩语、德语、法语、俄语和
  意大利语切换，并在浏览器中保存用户选择。
- 中文副标题改为“仅展示经过 X 确认成功并上传到服务器的屏蔽记录。”

### v1.6.0 — 2026-07-30

Data synchronization, analytics dashboard, popular keywords, and GitHub-based
update discovery.

- Added a persistent, independent upload queue for confirmed Ban events. A slow
  or unavailable server never pauses the actual Ban queue, and unsent records
  resume automatically after the browser restarts.
- Each event includes the blocked username, display name, match reason, exact
  matched keywords, the complete user-configured keyword list, content excerpt,
  source page, and confirmed block time.
- Added a Spring Boot JAR service on port `59999`, backed exclusively by MySQL
  5.7.44-compatible persistence, with an embedded live Ban list and keyword
  analytics dashboard.
- Keyword rankings show configuration count, hit count, and distinct blocked
  account count. The extension can load popular keywords into the editor, but
  never saves or applies them until the user confirms.
- Added automatic checks of GitHub Releases every 12 hours and a manual
  “Check for updates” action. Update files come directly from GitHub and do not
  consume the AutoBanRobot server.
- Fixed exact matched keywords being omitted from the page-to-background event,
  and separated upload scheduling from the serialized 500 ms Ban queue.

新增数据同步、分析看板、热门关键词与 GitHub 更新检查：

- 新增独立且持久化的 Ban 数据上传队列；服务端缓慢或离线不会阻塞实际
  Ban 队列，未发送记录在浏览器重启后继续自动补传。
- 每条记录同步被屏蔽账号、账号名称、命中原因、实际命中关键词、用户设置的
  全部关键词、被屏蔽时内容、来源页面及确认屏蔽时间。
- 新增固定使用 `59999` 端口的 Spring Boot JAR 服务端，仅使用兼容
  MySQL 5.7.44 的持久化方案，并在 JAR 内置实时 Ban 清单与关键词分析页面。
- 关键词排名展示被设置次数、命中次数和不同 Ban 账号数；插件可将热门词加载
  到编辑框，但必须由用户确认并保存，服务端不能强制下发或修改规则。
- 新增每 12 小时自动检查 GitHub Releases 以及手动“检查更新”入口；
  更新文件直接由 GitHub 提供，不消耗 AutoBanRobot 服务端资源。
- 修复页面识别结果漏传实际命中关键词的问题，并将上传调度与 500 毫秒串行
  Ban 队列完全分离。

### v1.5.2 — 2026-07-29

License policy update.

- Replaced the MIT License with the PolyForm Noncommercial License 1.0.0.
- Source code remains publicly readable, modifiable, and distributable for permitted noncommercial purposes.
- Commercial use is not permitted without a separate license from the project owner.

许可策略更新：

- 将 MIT License 替换为 PolyForm Noncommercial License 1.0.0。
- 源代码继续公开，并允许在协议规定的非商业用途范围内查看、修改和分发。
- 未经项目所有者另行授权，不得用于商业用途。

### v1.5.1 — 2026-07-29

Rule-switch persistence fix.

- Rule switches now save immediately when toggled; closing the popup no longer discards their new state.
- Replaced checkbox controls with clearer animated slider switches.
- The keyword Save button continues to save keywords and the current rule states together.
- Repeated setting changes reuse one confirmation timer to avoid stale confirmation messages.

修复规则开关状态无法可靠保留的问题：

- 开关切换时立即写入扩展设置，关闭弹窗不会再丢失刚刚选择的状态。
- 将勾选框替换为状态更直观的动画滑块开关。
- 关键词“保存”按钮仍会同时保存关键词与当前全部规则状态。
- 连续修改设置时复用同一个保存提示计时器，避免旧提示状态互相干扰。

### v1.5.0 — 2026-07-29

Structured multiline spam-pattern detection.

- Added an independently configurable `Text → Emoji → Text → Emoji → Date/time` rule for five-line spam posts.
- The rule requires exactly five non-empty lines and validates the entire post structure.
- Both text lines must contain Latin text, both Emoji lines must contain exactly one complete Emoji, and the final line must be a date and time.
- Added explicit `<br>` extraction so visual line breaks in Twitter/X posts are preserved during matching.
- The new rule is enabled by default and can be switched off independently without affecting the other built-in rules.

新增结构化多行垃圾内容识别：

- 新增独立开关的“文字 → Emoji → 文字 → Emoji → 日期时间”五段式规则。
- 规则要求整条内容严格由五个非空行组成，避免普通多行内容因局部相似而误判。
- 两个文字行必须包含拉丁文字，两个 Emoji 行必须分别只有一个完整 Emoji，最后一行必须为日期时间。
- 新增 `<br>` 换行提取兼容，确保 Twitter/X 页面中视觉换行能够参与规则判断。
- 新规则默认开启，可单独关闭，不会影响其他内置规则。

### v1.4.0 — 2026-07-29

Safari support, unified branding, and independently configurable built-in rules.

- Added a dedicated macOS Safari adaptation and Xcode packaging project on the `safari` branch.
- Chrome and Microsoft Edge continue to share the exact same Chromium implementation on `main`.
- Added a visible, independent switch for the strict single-Emoji rule. It remains enabled by default for existing users.
- Saving either built-in rule switch takes effect immediately and triggers a fresh scan of the current page.
- Switching one built-in rule no longer changes the state of the other.
- Adopted the same full-bleed cat-assistant icon across Chrome, Edge, and Safari.

新增 Safari 支持、统一品牌图标及可独立配置的内置规则：

- 在 `safari` 分支新增 macOS Safari 专用适配代码与 Xcode 打包工程。
- Chrome 与 Microsoft Edge 继续在 `main` 分支共用完全相同的 Chromium 实现。
- 为严格的“仅单个 Emoji”规则增加独立且可见的开关；现有用户默认保持开启。
- 保存任一内置规则开关后立即生效，并重新扫描当前页面。
- 切换其中一个内置规则时，不会再改变另一个规则的状态。
- Chrome、Edge 与 Safari 统一使用同一枚铺满画布的猫咪助手图标。

### v1.3.0 — 2026-07-29

Configurable pattern detection, verified blocks, and local Ban history.

- Added an optional `Emoji + Latin text + Emoji` rule for spam patterns such as `💝charming✌`, `🍹refined🙈`, and `🖼athletic💞`.
- The new pattern must cover the entire post. Ordinary sentences that merely contain an emoji do not match.
- Added a settings-popup switch for enabling or disabling this rule; it is enabled by default.
- Added a mandatory pre-block relationship check. Accounts followed by the authenticated user, including mutual follows, are always exempt even when a rule matches.
- If the relationship cannot be confirmed, AutoBanRobot fails closed and does not submit a block request.
- Moved blocking from the page script to a persistent Manifest V3 background queue. Matches are recorded immediately and processed asynchronously, one account at a time.
- The original timeline or profile page may be closed or changed while queued jobs continue. Pending jobs survive browser restarts and resume after Twitter/X authentication becomes available.
- The queue keeps a minimum 500 ms interval between accounts and exposes its pending count in the extension popup.
- Bearer and CSRF credentials remain in session-only storage; persistent queue records never contain authentication credentials.
- Block API responses are no longer considered successful based only on HTTP `2xx`. AutoBanRobot now requires an explicit `blocking=true` relationship and uses a follow-up relationship check when necessary.
- Unconfirmed blocks are reported as failures, restored visually, excluded from counters, and never added to history.
- Added a local list of up to 500 uniquely confirmed accounts blocked by AutoBanRobot, including account, time, match reason, and content excerpt.
- Ban history entries link to the corresponding X profile and can be cleared without changing X block status.

新增可配置模式识别、屏蔽结果确认及本地 Ban 清单：

- 新增可选的“Emoji + 拉丁文字 + Emoji”规则，可识别 `💝charming✌`、`🍹refined🙈`、`🖼athletic💞` 等模式。
- 该模式必须覆盖整条内容，普通句子中仅夹带 Emoji 不会命中。
- 设置弹窗新增独立开关，默认开启，可随时关闭。
- 在提交屏蔽前强制检查关注关系；当前登录用户正在关注的账号（包括互关账号）即使命中规则也始终跳过。
- 如果关注关系暂时无法确认，AutoBanRobot 会采用安全策略，不提交屏蔽请求。
- 将屏蔽执行从页面脚本迁移到 Manifest V3 持久化后台队列；命中后立即记录，再由后台逐个异步处理。
- 原时间线或用户页面可以关闭或切换，已排队任务仍会继续；浏览器重启后，任务会在 Twitter/X 登录凭证重新可用时恢复。
- 后台队列在账号之间保持至少 500 毫秒间隔，弹窗会展示待处理数量。
- Bearer 和 CSRF 仅保存在会话级存储中，持久化任务记录不包含任何登录凭证。
- 不再仅凭 HTTP `2xx` 判定屏蔽成功；现在要求服务端明确返回 `blocking=true`，必要时调用关系接口二次核验。
- 未确认的屏蔽会按失败处理、恢复页面显示、不计入数量，也不会写入 Ban 清单。
- 新增 AutoBanRobot 本地已确认屏蔽清单，最多保留 500 个唯一账号，并记录账号、时间、命中原因和内容摘要。
- 清单可打开对应 X 账号主页，也可清空本地记录；清空记录不会解除 X 上的屏蔽。

### v1.2.1 — 2026-07-29

Page-scoped notification statistics.

- Changed matched and blocked counts from extension-session totals to statistics scoped to the current Twitter/X URL.
- Counts reset automatically when navigating between Home, Search, user profiles, post details, or other routes without a full page reload.
- A queued block originating from a previous route cannot be counted in the newly opened page.
- Successful-block notifications now explicitly label the values as current-page statistics.
- Moved the initial keyword preset to `default-keywords.json`; it is copied to extension storage only when no saved keyword list exists.
- Preset entries are fully editable and deletable. An intentionally empty keyword list remains empty and is never restored or forcibly merged by runtime code.

当前页面范围统计优化：

- 将“已匹配 / 已屏蔽”从扩展页面脚本运行期间累计值改为当前 Twitter/X URL 页面独立统计。
- 在主页、搜索、用户主页、推文详情等路由之间无刷新切换时，统计自动重新开始。
- 旧页面进入队列的屏蔽任务即使稍后完成，也不会计入新页面。
- 屏蔽成功提示明确标注为“当前页面”统计。
- 将初始屏蔽词预设移至 `default-keywords.json`，仅在不存在已保存关键词列表时写入扩展存储。
- 所有预设词均可编辑和删除；用户主动保存空列表后会保持为空，运行时代码不会恢复或强制合并预设。

### v1.2.0 — 2026-07-29

Transient on-page blocking statistics.

- Added matched and successfully blocked counts to the existing bottom-right successful-block notification.
- The combined notification disappears automatically after four seconds; no additional or persistent widget is created.
- Matching the same account in multiple posts is counted only once.

新增非驻留式网页内屏蔽统计：

- 在 Twitter/X 页面右下角现有的“屏蔽成功”提示中展示已匹配和已成功屏蔽数量。
- 合并后的提示在四秒后自动消失，不增加额外弹窗或常驻组件。
- 同一账号在多条推文中重复命中时只计数一次。

### v1.1.1 — 2026-07-29

Emoji extraction compatibility fix.

- Fixed single-emoji posts not being detected when Twitter/X renders the emoji as an `<img>` element instead of a text node.
- Tweet text extraction now preserves DOM order while combining ordinary text nodes with image `alt` values.
- Added support for accessible emoji elements rendered with `role="img"` or `data-emoji`.
- The single-emoji rule remains strict: sentences containing an emoji and posts containing multiple emojis do not match this rule.

Emoji 提取兼容性修复：

- 修复 Twitter/X 将 Emoji 渲染为 `<img>` 而非普通文本节点时，单 Emoji 内容无法识别的问题。
- 正文提取现在按照 DOM 顺序合并普通文本节点和图片的 `alt` 内容。
- 增加对带有 `role="img"` 或 `data-emoji` 的可访问 Emoji 元素的兼容。
- 单 Emoji 规则仍保持严格：句子中夹带 Emoji 或包含多个 Emoji 的内容不会命中该规则。

### v1.1.0 — 2026-07-28

Reliability and rate-limit protection update.

- Fixed intermittent missed matches caused by marking partially rendered Twitter/X posts as processed too early.
- Replaced one-time DOM tracking with content signatures, allowing posts to be checked again when their author or text changes.
- Added a global sequential blocking queue with a mandatory 500 ms interval between accounts.
- Added up to three controlled retries for temporary network errors, HTTP `408`, `425`, `429`, and `5xx` responses.
- Deduplicated pending, queued, in-progress, and successfully blocked accounts.
- Improved keyword matching with Unicode NFKC normalization, case-insensitive comparison, and removal of whitespace and invisible separator characters.
- Preserved the captured Bearer token in session-only extension storage so Manifest V3 service-worker restarts do not temporarily interrupt blocking.
- Coalesced DOM mutation scans with `requestAnimationFrame` to reduce redundant work on busy timelines.

本版本重点提升动态时间线中的识别可靠性并降低接口限流风险：

- 修复推文尚未渲染完整就被提前标记为“已处理”造成的偶发漏判。
- 使用内容签名跟踪 DOM；用户名、显示名称或正文变化后会重新检查。
- 新增全局串行屏蔽队列，每个账号之间强制等待 500 毫秒。
- 针对临时网络异常及 HTTP `408`、`425`、`429`、`5xx` 响应，最多进行三次受控重试。
- 对等待中、队列中、执行中及已成功屏蔽的账号统一去重。
- 关键词匹配新增 Unicode NFKC 规范化、大小写兼容、空白及不可见分隔符清理。
- Bearer Token 使用浏览器会话级存储，避免 Manifest V3 后台休眠重启造成短暂失效；关闭浏览器后自动清除。
- 使用 `requestAnimationFrame` 合并 DOM 变化扫描，降低繁忙时间线中的重复计算。

### v1.0.0 — 2026-07-28

- Initial public release.
- Added username, display-name, post-content, and single-emoji blocking rules.
- Added built-in and user-defined keywords with immediate page rescanning.
- Added multilingual documentation in nine languages.

## 中文

AutoBanRobot 是一款适用于 Chromium 浏览器的 Twitter/X 垃圾账号自动屏蔽扩展。

### 功能

- 扫描当前页面及动态加载的推文和回复。
- 当用户名、显示名称或发布内容命中关键词时，自动屏蔽对应账号。
- 当发布内容去除空白后只有一个完整 Emoji 时，自动屏蔽对应账号。
- 支持在扩展弹窗中添加自定义关键词，保存后立即重新扫描当前页面。
- “仅单个 Emoji”、“Emoji + 拉丁文字 + Emoji”与“五段式 Emoji 时间”规则均提供独立开关。
- 仅将经过 X 关系状态确认成功的账号写入本地 Ban 清单。
- 当前登录用户正在关注或互关的账号不会被自动屏蔽。
- 内置常见垃圾推广关键词，并记录成功屏蔽数量。
- 已确认 Ban 事件可异步同步到本机 JAR 服务端，并在关键词分析页查看排名。
- 可手动加载热门关键词，确认保存后才会应用；服务端不能强制修改插件规则。
- 自动从 GitHub Releases 检查新版本，不经过数据服务端。
- 服务端网页支持九语言切换，并展示匿名插件在线用户数和累计用户数。
- 同时支持 `twitter.com` 和 `x.com`。

### 安装

1. 下载或克隆本仓库。
2. 打开 Chrome、Edge 或其他 Chromium 浏览器的扩展管理页面。
3. 开启“开发者模式”。
4. 点击“加载已解压的扩展程序”，选择本仓库目录。
5. 登录 Twitter/X 并正常浏览。

Safari 版本下载的是适配源码和 Xcode 工程，不能像 Chrome/Edge 插件一样直接加载；
Safari 用户需要安装 Xcode，并自行构建、签名和打包 macOS App。

### 使用与注意事项

点击工具栏中的扩展图标即可编辑关键词，每行填写一个。扩展使用当前 Twitter/X 登录会话执行真实屏蔽操作。关键词过于宽泛可能造成误屏蔽，请谨慎配置。本项目与 X Corp. 无关。

弹窗可以分别开关“仅单个 Emoji”、“Emoji + 任意非空内容 + Emoji”和“五段式 Emoji 时间”规则，并查看由本扩展执行且经过 X 关系接口确认成功的 Ban 清单。该清单不是 X 账号全部历史屏蔽列表。

### 初始屏蔽词预设

`免费过夜`、`主页联系`、`主页匹配`、`免费破处`、`同城`、`上门`、`刷了半天`、`看主页`、`点我头像`、`处男免费`、`处男无偿`、`体制内老师`、`她太涩了`、`sao货`

该列表只在首次初始化时写入设置，之后可以在扩展弹窗中任意修改或全部删除。

## English

AutoBanRobot is a Twitter/X spam-account blocker for Chromium-based browsers.

### Features

- Scans tweets and replies already on the page and those loaded dynamically.
- Blocks an account when its username, display name, or post content matches a keyword.
- Blocks an account when the post, after whitespace is removed, consists of exactly one complete emoji.
- Supports custom keywords from the extension popup and immediately rescans the current page after saving.
- Supports an optional `Emoji + Latin text + Emoji` spam-pattern rule.
- Keeps a local history of accounts whose blocked relationship was confirmed by X.
- Includes built-in spam keywords and keeps a successful-block counter.
- Asynchronously syncs confirmed Ban events to the local JAR dashboard.
- Loads popular keywords for review without applying them automatically.
- Checks GitHub Releases directly for new versions.
- Shows anonymous online and cumulative plugin users on a nine-language dashboard.
- Works on both `twitter.com` and `x.com`.

### Installation

1. Download or clone this repository.
2. Open the extensions page in Chrome, Edge, or another Chromium browser.
3. Enable Developer mode.
4. Choose “Load unpacked” and select this repository directory.
5. Sign in to Twitter/X and browse normally.

The Safari download contains adaptation source code and an Xcode project. It is
not a ready-to-install extension; Safari users must build, sign, and package the
containing macOS app themselves with Xcode.

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
- Sincroniza de forma asíncrona los bloqueos confirmados con el panel JAR local.
- Permite revisar palabras clave populares antes de guardarlas.
- Comprueba nuevas versiones directamente en GitHub Releases.
- Muestra usuarios anónimos en línea y acumulados en un panel de nueve idiomas.
- Funciona en `twitter.com` y `x.com`.

### Instalación y uso

Descarga o clona el repositorio, activa el modo de desarrollador en la página de extensiones de tu navegador y selecciona “Cargar descomprimida”. Abre el icono de la extensión para editar una palabra clave por línea. La extensión realiza bloqueos reales mediante tu sesión activa; revisa las reglas para evitar falsos positivos. Este proyecto no está afiliado a X Corp.

La descarga para Safari contiene el código fuente adaptado y un proyecto de Xcode; no es una aplicación instalable. El usuario debe compilar, firmar y empaquetar la aplicación para macOS con Xcode.

## 日本語

AutoBanRobot は、Chromium 系ブラウザー向けの Twitter/X スパムアカウント自動ブロック拡張機能です。

### 機能

- 表示中および動的に読み込まれた投稿・返信を監視します。
- ユーザー名、表示名、投稿内容のいずれかがキーワードに一致すると、そのアカウントをブロックします。
- 空白を除いた投稿内容が完全な Emoji 1個だけの場合もブロックします。
- ポップアップからキーワードを追加でき、保存すると現在のページを直ちに再スキャンします。
- スパム用の組み込みキーワードとブロック件数表示を備えています。
- 確認済み Ban をローカル JAR ダッシュボードへ非同期で同期します。
- 人気キーワードは確認後にのみ保存・適用されます。
- GitHub Releases から新しいバージョンを直接確認します。
- 9 言語対応ダッシュボードに匿名のオンライン・累計ユーザー数を表示します。
- `twitter.com` と `x.com` の両方に対応します。

### インストールと注意

このリポジトリをダウンロードまたはクローンし、ブラウザーの拡張機能ページでデベロッパーモードを有効にして、「パッケージ化されていない拡張機能を読み込む」からフォルダーを選択してください。本拡張機能はログイン中のセッションで実際にアカウントをブロックします。誤検知を避けるため、キーワードを慎重に確認してください。本プロジェクトは X Corp. とは関係ありません。

Safari 用ダウンロードは適応済みソースコードと Xcode プロジェクトであり、直接インストールできるアプリではありません。Safari ユーザーは Xcode で macOS アプリをビルド、署名、パッケージ化する必要があります。

## 한국어

AutoBanRobot은 Chromium 기반 브라우저에서 동작하는 Twitter/X 스팸 계정 자동 차단 확장 프로그램입니다.

### 기능

- 현재 페이지와 동적으로 불러온 게시물 및 답글을 검사합니다.
- 사용자 이름, 표시 이름 또는 게시물 내용이 키워드와 일치하면 해당 계정을 차단합니다.
- 공백을 제거한 게시물 내용이 완전한 이모지 하나뿐인 경우에도 차단합니다.
- 팝업에서 사용자 키워드를 추가할 수 있으며 저장 즉시 현재 페이지를 다시 검사합니다.
- 기본 스팸 키워드와 성공한 차단 횟수 표시를 제공합니다.
- 확인된 Ban 기록을 로컬 JAR 대시보드에 비동기로 동기화합니다.
- 인기 키워드는 사용자가 확인하고 저장한 뒤에만 적용됩니다.
- GitHub Releases에서 새 버전을 직접 확인합니다.
- 9개 언어 대시보드에서 익명 온라인 및 누적 사용자 수를 표시합니다.
- `twitter.com`과 `x.com`을 모두 지원합니다.

### 설치 및 주의사항

저장소를 다운로드하거나 복제한 뒤 브라우저 확장 프로그램 페이지에서 개발자 모드를 켜고 “압축해제된 확장 프로그램을 로드합니다”를 선택하세요. 이 확장 프로그램은 로그인된 세션을 사용해 실제 계정 차단을 수행합니다. 오탐을 방지하려면 키워드를 신중하게 검토하세요. 이 프로젝트는 X Corp.와 관련이 없습니다.

Safari 다운로드는 변환된 소스 코드와 Xcode 프로젝트이며 바로 설치할 수 있는 앱이 아닙니다. Safari 사용자는 Xcode로 macOS 앱을 직접 빌드, 서명 및 패키징해야 합니다.

## Deutsch

AutoBanRobot ist eine Erweiterung für Chromium-Browser, die Spam-Konten auf Twitter/X automatisch blockiert.

### Funktionen

- Prüft sichtbare sowie dynamisch geladene Beiträge und Antworten.
- Blockiert ein Konto, wenn Benutzername, Anzeigename oder Beitragsinhalt einem Schlüsselwort entspricht.
- Blockiert ein Konto, wenn der Beitrag nach dem Entfernen von Leerzeichen aus genau einem vollständigen Emoji besteht.
- Unterstützt eigene Schlüsselwörter und durchsucht die aktuelle Seite nach dem Speichern sofort erneut.
- Enthält integrierte Spam-Schlüsselwörter und einen Zähler erfolgreicher Blockierungen.
- Synchronisiert bestätigte Bans asynchron mit dem lokalen JAR-Dashboard.
- Lädt beliebte Schlüsselwörter nur zur Prüfung; gespeichert werden sie erst nach Bestätigung.
- Prüft neue Versionen direkt über GitHub Releases.
- Zeigt anonyme Online- und Gesamtnutzer in einem Dashboard mit neun Sprachen.
- Funktioniert auf `twitter.com` und `x.com`.

### Installation und Hinweis

Repository herunterladen oder klonen, den Entwicklermodus auf der Erweiterungsseite aktivieren und „Entpackte Erweiterung laden“ wählen. Die Erweiterung führt über die angemeldete Sitzung echte Kontoblockierungen aus. Zu allgemeine Schlüsselwörter können Fehlblockierungen verursachen. Dieses Projekt steht in keiner Verbindung zu X Corp.

Der Safari-Download enthält den angepassten Quellcode und ein Xcode-Projekt, keine direkt installierbare App. Safari-Nutzer müssen die macOS-App selbst mit Xcode erstellen, signieren und paketieren.

## Français

AutoBanRobot est une extension pour navigateurs Chromium qui bloque automatiquement les comptes indésirables sur Twitter/X.

### Fonctionnalités

- Analyse les publications et réponses visibles ou chargées dynamiquement.
- Bloque un compte lorsque son identifiant, son nom affiché ou son contenu correspond à un mot-clé.
- Bloque un compte lorsque le contenu, une fois les espaces retirés, contient exactement un seul emoji complet.
- Accepte des mots-clés personnalisés et réanalyse immédiatement la page après leur enregistrement.
- Inclut des mots-clés antispam et un compteur de blocages réussis.
- Synchronise de façon asynchrone les blocages confirmés avec le tableau de bord JAR local.
- Charge les mots-clés populaires pour vérification sans les appliquer automatiquement.
- Recherche les nouvelles versions directement dans GitHub Releases.
- Affiche les utilisateurs anonymes en ligne et cumulés dans un tableau de bord en neuf langues.
- Fonctionne sur `twitter.com` et `x.com`.

### Installation et avertissement

Téléchargez ou clonez le dépôt, activez le mode développeur sur la page des extensions puis choisissez « Charger l’extension non empaquetée ». L’extension effectue de véritables blocages avec votre session connectée. Vérifiez soigneusement les mots-clés afin d’éviter les faux positifs. Ce projet n’est pas affilié à X Corp.

Le téléchargement Safari contient le code source adapté et un projet Xcode, et non une application directement installable. Les utilisateurs Safari doivent compiler, signer et empaqueter eux-mêmes l’application macOS avec Xcode.

## Русский

AutoBanRobot — расширение для браузеров на базе Chromium, автоматически блокирующее спам-аккаунты в Twitter/X.

### Возможности

- Проверяет видимые и динамически загружаемые публикации и ответы.
- Блокирует аккаунт, если имя пользователя, отображаемое имя или текст публикации совпадает с ключевым словом.
- Блокирует аккаунт, если после удаления пробелов публикация состоит ровно из одного полноценного эмодзи.
- Поддерживает пользовательские ключевые слова и сразу повторно проверяет текущую страницу после сохранения.
- Содержит встроенные антиспам-слова и счётчик успешных блокировок.
- Асинхронно синхронизирует подтверждённые блокировки с локальной JAR-панелью.
- Загружает популярные ключевые слова для проверки, не применяя их автоматически.
- Проверяет новые версии напрямую через GitHub Releases.
- Показывает анонимных онлайн- и суммарных пользователей на панели с девятью языками.
- Работает на `twitter.com` и `x.com`.

### Установка и предупреждение

Скачайте или клонируйте репозиторий, включите режим разработчика на странице расширений и выберите «Загрузить распакованное расширение». Расширение выполняет реальные блокировки через активный сеанс Twitter/X. Тщательно проверяйте ключевые слова, чтобы избежать ложных срабатываний. Проект не связан с X Corp.

Загрузка для Safari содержит адаптированный исходный код и проект Xcode, а не готовое приложение. Пользователь Safari должен самостоятельно собрать, подписать и упаковать приложение macOS с помощью Xcode.

## Italiano

AutoBanRobot è un’estensione per browser Chromium che blocca automaticamente gli account spam su Twitter/X.

### Funzionalità

- Analizza post e risposte visibili o caricati dinamicamente.
- Blocca un account quando nome utente, nome visualizzato o contenuto corrispondono a una parola chiave.
- Blocca un account quando il contenuto, rimossi gli spazi, è composto esattamente da una sola emoji completa.
- Supporta parole chiave personalizzate e riesamina subito la pagina corrente dopo il salvataggio.
- Include parole chiave antispam integrate e un contatore dei blocchi riusciti.
- Sincronizza in modo asincrono i Ban confermati con il pannello JAR locale.
- Carica le parole chiave popolari per la revisione senza applicarle automaticamente.
- Controlla le nuove versioni direttamente tramite GitHub Releases.
- Mostra utenti anonimi online e cumulativi in un pannello disponibile in nove lingue.
- Funziona su `twitter.com` e `x.com`.

### Installazione e avvertenza

Scarica o clona il repository, abilita la modalità sviluppatore nella pagina delle estensioni e scegli “Carica estensione non pacchettizzata”. L’estensione esegue blocchi reali tramite la sessione Twitter/X attiva. Controlla attentamente le parole chiave per evitare falsi positivi. Il progetto non è affiliato a X Corp.

Il download per Safari contiene il codice sorgente adattato e un progetto Xcode, non un’app pronta da installare. Gli utenti Safari devono compilare, firmare e pacchettizzare autonomamente l’app macOS con Xcode.

## License

[PolyForm Noncommercial License 1.0.0](LICENSE)

Source is publicly available for permitted noncommercial use. Commercial use
requires a separate license from the project owner.

本项目源码公开，可依照协议用于非商业目的。任何商业使用均须另行取得项目所有者授权。
