# AutoBanRobot

<img src="icon.png" alt="AutoBanRobot cat assistant icon" width="160">

Twitter/X spam-account blocker for Chrome, Microsoft Edge, and Safari.

[中文](#中文) · [English](#english) · [Español](#español) · [日本語](#日本語) · [한국어](#한국어) · [Deutsch](#deutsch) · [Français](#français) · [Русский](#русский) · [Italiano](#italiano)

> This extension performs real account blocks through the logged-in Twitter/X session. Review your keyword list before enabling it.

## Repository branches / 仓库分支

- [`main`](https://github.com/serenamustrich/autobanrobot/tree/main): the single shared Chromium codebase for both Chrome and Microsoft Edge. Chrome and Edge use the same source files, manifest, features, and release package; they are not separate implementations.
- [`safari`](https://github.com/serenamustrich/autobanrobot/tree/safari): Safari adaptation and packaging source. Safari-specific code is maintained in the `safari/` directory on that branch.

- [`main`](https://github.com/serenamustrich/autobanrobot/tree/main)：Chrome 与 Microsoft Edge 共用的同一套 Chromium 源码。两者使用完全相同的代码、Manifest、功能和发布包，不是两个独立实现。
- [`safari`](https://github.com/serenamustrich/autobanrobot/tree/safari)：Safari 适配与打包源码；Safari 专用代码统一维护在该分支的 `safari/` 目录。

## Release notes / 更新说明

### v1.6.11 — 2026-08-01

- Expanded the existing vlog short-link rule to recognize the Chinese template
  `是这个吗…之前好像看过` and the English template `This is the vlog`.
- The Chinese variant requires both characteristic fragments, and every
  template still requires a valid `t.cn/<code>` link. Emoji, punctuation,
  capitalization, and whitespace do not affect matching.
- 扩展现有 vlog 短链规则，新增识别“是这个吗…之前好像看过”
  中文模板和 `This is the vlog` 英文模板。
- 中文模板必须同时包含两段特征语，所有模板仍必须同时出现
  有效的 `t.cn/<code>` 短链；Emoji、标点、大小写和空白不影响匹配。

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

## License

[PolyForm Noncommercial License 1.0.0](LICENSE)

Source is publicly available for permitted noncommercial use. Commercial use
requires a separate license from the project owner.

本项目源码公开，可依照协议用于非商业目的。任何商业使用均须另行取得项目所有者授权。
