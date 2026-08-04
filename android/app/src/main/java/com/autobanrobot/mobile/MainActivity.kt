package com.autobanrobot.mobile

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedDispatcher
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private class SelectionWebView(
    context: Context,
    private val onAddKeyword: (ActionMode) -> Unit
) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return super.startActionMode(decorate(callback))
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        return super.startActionMode(decorate(callback), type)
    }

    private fun decorate(callback: ActionMode.Callback): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val created = callback.onCreateActionMode(mode, menu)
                if (created) addKeywordItem(menu)
                return created
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val prepared = callback.onPrepareActionMode(mode, menu)
                addKeywordItem(menu)
                return prepared
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == MENU_ADD_BLOCK_KEYWORD) {
                    onAddKeyword(mode)
                    return true
                }
                return callback.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                callback.onDestroyActionMode(mode)
            }
        }
    }

    private fun addKeywordItem(menu: Menu) {
        if (menu.findItem(MENU_ADD_BLOCK_KEYWORD) == null) {
            menu.add(Menu.NONE, MENU_ADD_BLOCK_KEYWORD, Menu.NONE, "添加屏蔽")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
    }

    private companion object {
        const val MENU_ADD_BLOCK_KEYWORD = 9002
    }
}

internal enum class BackNavigationAction {
    SHOW_HOME,
    CLICK_PAGE_BACK
}

internal object BackNavigationPolicy {
    fun decide(isHomePage: Boolean): BackNavigationAction {
        return if (isHomePage) {
            BackNavigationAction.CLICK_PAGE_BACK
        } else {
            BackNavigationAction.SHOW_HOME
        }
    }
}

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var screenFrame: FrameLayout
    private lateinit var homeContainer: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarBack: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarAction: TextView
    private lateinit var bottomNav: LinearLayout
    private var currentPage = PAGE_HOME
    private var topInsetPx = 0
    private lateinit var ruleStore: RuleStore
    private lateinit var accountStore: AccountStore
    private lateinit var pluginManager: PluginManager
    private lateinit var queue: BlockQueue
    private lateinit var auth: AuthState
    private lateinit var api: XApiClient
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var whitelistInput: EditText? = null
    private val pendingPopularKeywords = mutableListOf<String>()
    private val newlyAddedKeywords = mutableSetOf<String>()
    private var keywordStatus = ""
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatActive = false
    private val appInstallationId by lazy {
        val preferences = getSharedPreferences("autoban_app_client", MODE_PRIVATE)
        preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).apply()
        }
    }
    private val appDeviceName by lazy {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(128)
    }
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            Thread {
                val token = accountStore.session()?.token
                api.sendHeartbeat(appInstallationId, APP_VERSION, appDeviceName, token)
                if (token != null) accountStore.pull()
            }.start()
            if (heartbeatActive) heartbeatHandler.postDelayed(this, APP_HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleStore = RuleStore(this)
        accountStore = AccountStore(this, ruleStore)
        pluginManager = PluginManager(this)
        auth = AuthState()
        api = XApiClient(auth)
        queue = BlockQueue(this, ruleStore, auth, api, onProgress = { message ->
            runOnUiThread {
                if (!::status.isInitialized) return@runOnUiThread
                if (message.equals("处理中 @AAAGodofWealth", ignoreCase = true)) {
                    status.text = "已跳过 @AAAGodofWealth"
                } else if (message.startsWith("处理中 @") ||
                    message.startsWith("等待登录会话 @") ||
                    message.startsWith("正在确认当前登录账号") ||
                    message.startsWith("正在确认登录账号 @")
                ) {
                    status.text = message
                }
            }
        }) { job, outcome ->
            runOnUiThread {
                if (outcome.state == "success" && !outcome.message.contains("历史记录同步")) {
                    status.text = "已处理 @${job.username}"
                    if (currentPage == "Ban记录") showHistoryPage()
                } else if (outcome.state == "skipped") {
                    status.text = "已跳过 @${job.username}"
                }
                dispatchResult(job, outcome)
            }
        }

        buildUi()
        registerSystemBackGesture()
        ruleStore.refreshRules { refreshed ->
            if (!refreshed) return@refreshRules
        }
        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored == null) {
            val lastUrl = ruleStore.lastXUrl()
                ?.takeUnless { it.contains("/i/flow/login", ignoreCase = true) }
            webView.loadUrl(lastUrl ?: "https://x.com/home")
        }
    }

    private fun buildUi() {
        applySystemBars(light = true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
        }

        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
        }
        toolbarBack = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            scaleType = ImageView.ScaleType.CENTER
            background = null
            contentDescription = "返回 X"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { showHome() }
        }
        toolbarTitle = TextView(this).apply {
            text = "AutoBanRobot"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(INK)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        toolbar.addView(toolbarBack)
        toolbar.addView(toolbarTitle)
        toolbarAction = TextView(this).apply {
            text = "X"
            textSize = 12f
            setTextColor(Color.rgb(174, 184, 190))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(34))
            visibility = View.GONE
        }
        toolbar.addView(toolbarAction)
        root.addView(toolbar)

        webView = SelectionWebView(this, ::addSelectedKeyword)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.userAgentString = webView.settings.userAgentString + " AutoBanRobotAndroid/1.0"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.addJavascriptInterface(AndroidBridge(), "AutoBanBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host?.lowercase(Locale.ROOT)
                return host != "x.com" && host != "twitter.com" && host != "mobile.twitter.com" && host != "accounts.x.com"
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                val host = request.url.host?.lowercase(Locale.ROOT) ?: ""
                // X now sends authenticated requests through both x.com/i/api/* and
                // api.x.com/1.1/*; capture the headers from either route.
                if (isXApiHost(host)) {
                    val bearer = header(request, "authorization")
                    val csrf = header(request, "x-csrf-token")
                    if (!bearer.isNullOrBlank() || !csrf.isNullOrBlank()) {
                        Log.i("AutoBanAuth", "WebView X request auth bearer=${!bearer.isNullOrBlank()} csrf=${!csrf.isNullOrBlank()} host=$host path=${request.url.path}")
                    }
                    if (!bearer.isNullOrBlank() || !csrf.isNullOrBlank()) queue.updateAuth(bearer, csrf)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                queue.setPageReady(Uri.parse(url).host, false)
                view.post { installAuthCapture(view) }
            }

            override fun onPageFinished(view: WebView, url: String) {
                val host = Uri.parse(url).host?.lowercase(Locale.ROOT)
                if (host == "x.com" || host == "twitter.com" || host == "mobile.twitter.com") {
                    ruleStore.setLastXUrl(url)
                    CookieManager.getInstance().flush()
                    installAuthCapture(view)
                    injectContent { queue.restoreHistoryVisuals() }
                    queue.setPageReady(host, true)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                pendingCameraUri?.let(::deletePendingCameraUri)
                pendingCameraUri = null
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val galleryIntent = createGalleryIntent(fileChooserParams)
                val initialIntents = mutableListOf<Intent>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    createCameraIntent()?.let(initialIntents::add)
                }
                val chooser = Intent.createChooser(galleryIntent, "选择照片来源").apply {
                    if (initialIntents.isNotEmpty()) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
                    }
                }
                return try {
                    startActivityForResult(chooser, REQUEST_FILE_CHOOSER)
                    true
                } catch (error: Exception) {
                    Log.w("AutoBanMainActivity", "无法打开系统文件选择器", error)
                    pendingCameraUri?.let(::deletePendingCameraUri)
                    pendingCameraUri = null
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    false
                }
            }
        }

        status = TextView(this).apply {
            text = "已处理 0 条"
            textSize = 12f
            setTextColor(MUTED)
            setPadding(0, dp(8), dp(8), dp(8))
            setSingleLine(true)
            visibility = View.GONE
        }
        homeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setPadding(dp(10), dp(8), dp(10), 0)
        }
        screenFrame = FrameLayout(this).apply {
            setBackgroundColor(SURFACE)
        }
        screenFrame.addView(homeContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(screenFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomNav = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        bottomNav.addView(navButton("浏览", R.drawable.ic_tab_home) { showHome() })
        bottomNav.addView(navButton("关键词", R.drawable.ic_tab_keywords) { showKeywordsPage() })
        bottomNav.addView(navButton("规则", R.drawable.ic_tab_rules) { showRulesPage() })
        bottomNav.addView(navButton("Ban记录", R.drawable.ic_tab_history) { showHistoryPage() })
        bottomNav.addView(navButton("账号", R.drawable.ic_tab_account) { showAccountPage() })
        root.addView(bottomNav)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                topInset = bars.top
                bottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                val top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                val bottom = insets.systemWindowInsetBottom
                topInset = top
                bottomInset = bottom
            }
            topInsetPx = topInset
            toolbar.setPadding(dp(16), topInset + dp(8), dp(16), dp(8))
            // The navigation is edge-to-edge. Keep only the physical gesture inset;
            // any extra padding here wastes browser height.
            bottomNav.setPadding(dp(6), 0, dp(6), bottomInset)
            if (toolbar.visibility == View.GONE) {
                homeContainer.setPadding(dp(10), topInset + dp(8), dp(10), 0)
            }
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun injectContent(onComplete: (() -> Unit)? = null) {
        val injected = try { assets.open("content/injected.js").bufferedReader().use { it.readText() } } catch (_: Exception) { return }
        val keywords = JSONObject.quote(JSONArray(ruleStore.keywords()).toString())
        val rules = JSONObject.quote(pluginManager.mergeRules(ruleStore.rulesJson()))
        val states = JSONObject.quote(ruleStore.ruleStatesJson())
        val plugins = pluginManager.scriptBundle()
        val styles = JSONObject.quote(pluginManager.styleBundle())
        val bridgeScript = """
            (() => {
              window.__AUTOBANROBOT_MOBILE__ = true;
              if (!window.__AUTOBANROBOT_BRIDGE__) {
                window.__AUTOBANROBOT_BRIDGE__ = true;
                window.addEventListener('__twblocker_enqueue__', event => {
                  try { window.AutoBanBridge && window.AutoBanBridge.enqueueBlock(JSON.stringify(event.detail || {})); } catch (error) { console.warn('AutoBan bridge enqueue failed'); }
                });
              }
            })();
        """.trimIndent()
        val script = """
            $bridgeScript
            $injected
            $plugins
            try {
              const pluginStyle = JSON.parse($styles);
              if (pluginStyle) {
                const styleElement = document.createElement('style');
                styleElement.dataset.autobanPluginStyles = 'true';
                styleElement.textContent = pluginStyle;
                document.documentElement.appendChild(styleElement);
              }
            } catch (error) { console.warn('AutoBan plugin styles failed', error); }
            try {
              window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws: JSON.parse($keywords) } }));
              window.dispatchEvent(new CustomEvent('__twblocker_rules__', { detail: { config: JSON.parse($rules), states: JSON.parse($states) } }));
            } catch (error) { console.warn('AutoBanRobot configuration failed', error); }
        """.trimIndent()
        webView.evaluateJavascript(script) { onComplete?.invoke() }
    }

    private fun installAuthCapture(view: WebView = webView) {
        view.evaluateJavascript(AUTH_CAPTURE_SCRIPT, null)
    }

    private fun captureViewerUsername(view: WebView = webView) {
        view.evaluateJavascript(VIEWER_USERNAME_SCRIPT, null)
    }

    private fun dispatchResult(job: BlockJob, outcome: ApiOutcome) {
        val result = job.toJson().apply {
            put("state", outcome.state)
            put("message", outcome.message)
            put("historical", outcome.message.contains("历史记录同步"))
        }
        webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('__twblocker_block_result__',{detail:${result}}));",
            null
        )
    }

    private fun showHome() {
        currentPage = PAGE_HOME
        applySystemBars(light = true)
        toolbar.visibility = View.GONE
        homeContainer.setPadding(dp(10), topInsetPx + dp(8), dp(10), dp(8))
        toolbarBack.visibility = View.GONE
        toolbarTitle.text = "AutoBanRobot"
        toolbarAction.visibility = View.GONE
        toolbarAction.isClickable = false
        screenFrame.removeAllViews()
        screenFrame.addView(homeContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bottomNav.visibility = View.VISIBLE
    }

    private fun showPage(title: String, page: View) {
        currentPage = title
        applySystemBars(light = true)
        toolbar.visibility = View.VISIBLE
        toolbarBack.visibility = if (title == "白名单") View.VISIBLE else View.GONE
        toolbarTitle.text = title
        toolbarAction.isClickable = false
        if (title == "关键词") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "加载热门"
            toolbarAction.setTextColor(BLUE)
            toolbarAction.background = null
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener { loadPopularKeywordsForEditor() }
        } else if (title == "规则") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "更新"
            toolbarAction.setTextColor(BLUE)
            toolbarAction.background = null
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener { refreshRulesFromToolbar() }
        } else if (title == "Ban记录") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "白名单"
            toolbarAction.setTextColor(BLUE)
            toolbarAction.background = null
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener { showWhitelistPage() }
        } else if (title == "白名单") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "添加"
            toolbarAction.setTextColor(BLUE)
            toolbarAction.background = null
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener {
                whitelistInput?.requestFocus()
            }
        } else {
            toolbarAction.visibility = View.GONE
        }
        screenFrame.removeAllViews()
        screenFrame.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bottomNav.visibility = View.VISIBLE
    }

    private fun showKeywordsPage() {
        val content = pageContent()
        val input = EditText(this).apply {
            hint = "输入关键词"
            textSize = 16f
            isSingleLine = true
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(SURFACE_MUTED, 13f, BORDER)
        }
        val editor = card().apply {
            addView(sectionLabel("新增关键词"))
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })
            addView(TextView(context).apply {
                text = "保存后会立即重新扫描当前 X 页面"
                textSize = 12f
                setTextColor(MUTED)
                setPadding(0, dp(8), 0, 0)
            })
            addView(primaryButton("保存并立即生效") {
                val typed = input.text.toString().trim()
                val additions = (pendingPopularKeywords + listOfNotNull(typed.takeIf { it.isNotBlank() }))
                    .distinct()
                if (additions.isNotEmpty()) {
                    ruleStore.setKeywords(additions + ruleStore.keywords())
                    newlyAddedKeywords.clear()
                    newlyAddedKeywords.addAll(additions)
                    pendingPopularKeywords.clear()
                    keywordStatus = "已保存，立即生效"
                    if (accountStore.session() != null) Thread { accountStore.sync() }.start()
                    injectContent()
                    showKeywordsPage()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })
        }
        content.addView(editor)

        val displayed = (pendingPopularKeywords + ruleStore.keywords()).distinct()
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply { text = "当前关键词（${displayed.size}）"; textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(INK) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (keywordStatus.isNotBlank()) addView(TextView(context).apply { text = keywordStatus; textSize = 12f; setTextColor(if (keywordStatus.startsWith("无法")) RED else Color.rgb(52, 143, 88)); maxLines = 1 })
        }
        content.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })
        displayed.forEach { keyword ->
            val isPending = pendingPopularKeywords.contains(keyword)
            val row = card().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(10), dp(9))
                addView(TextView(context).apply { text = keyword; textSize = 16f; setTextColor(INK) }, LinearLayout.LayoutParams(0, dp(34), 1f))
                if (newlyAddedKeywords.contains(keyword)) addView(TextView(context).apply {
                    text = "NEW"; textSize = 10f; setTypeface(typeface, Typeface.BOLD); setTextColor(BLUE); gravity = Gravity.CENTER
                    background = rounded(Color.rgb(232, 244, 255), 10f); setPadding(dp(6), dp(3), dp(6), dp(3))
                })
                addView(TextView(context).apply {
                    text = "删除"; textSize = 13f; setTextColor(RED); gravity = Gravity.CENTER; setPadding(dp(12), 0, 0, 0)
                    setOnClickListener {
                        if (isPending) pendingPopularKeywords.remove(keyword) else ruleStore.setKeywords(ruleStore.keywords().filter { it != keyword })
                        newlyAddedKeywords.remove(keyword)
                        keywordStatus = "已删除“$keyword”，立即生效"
                        if (!isPending && accountStore.session() != null) Thread { accountStore.sync() }.start()
                        injectContent()
                        showKeywordsPage()
                    }
                }, LinearLayout.LayoutParams(dp(58), dp(34)))
            }
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
        }
        showPage("关键词", scrollPage(content))
    }

    private fun loadPopularKeywordsForEditor() {
        toolbarAction.isEnabled = false
        toolbarAction.alpha = 0.55f
        toolbarAction.text = "加载中"
        keywordStatus = ""
        ruleStore.loadPopularKeywords { popular, _ ->
            runOnUiThread {
                toolbarAction.isEnabled = true
                toolbarAction.alpha = 1f
                if (popular == null) {
                    keywordStatus = "无法连接热门关键词服务，请稍后重试"
                } else {
                    val known = (ruleStore.keywords() + pendingPopularKeywords).toSet()
                    val additions = popular.map { it.trim() }.filter { it.isNotBlank() && it !in known }.distinct()
                    pendingPopularKeywords.clear()
                    pendingPopularKeywords.addAll(additions)
                    newlyAddedKeywords.clear()
                    newlyAddedKeywords.addAll(additions)
                    keywordStatus = "新增 ${additions.size} 个，点击保存立即生效"
                }
                showKeywordsPage()
            }
        }
    }

    /** Account is a root tab, matching the iPhone TabView rather than opening a separate screen. */
    private fun showAccountPage() {
        val session = accountStore.session()
        if (session == null) {
            startActivity(Intent(this, AccountActivity::class.java))
            return
        }
        val content = pageContent()
        val profile = card()
        profile.setPadding(dp(18), dp(18), dp(18), dp(18))
        profile.background = rounded(Color.rgb(244, 248, 255), 20f, Color.rgb(208, 223, 242))
        profile.addView(TextView(this).apply {
            text = session.username
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(INK)
        })
        profile.addView(TextView(this).apply {
            text = "关键词和白名单会在此设备自动同步"
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(5), 0, dp(15))
        })
        profile.addView(primaryButton("立即同步") { button ->
            button.isEnabled = false
            button.text = "正在同步…"
            Thread {
                val result = accountStore.bindAndMerge()
                runOnUiThread {
                    button.isEnabled = true
                    button.text = if (result.isSuccess) "同步完成" else "立即同步"
                    if (result.isSuccess) injectContent()
                }
            }.start()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(profile)

        val contribution = queue.confirmedBanTotal()
        val contributionCard = card()
        contributionCard.setPadding(dp(14), dp(14), dp(14), dp(14))
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        var globalTotalValue: TextView? = null
        metrics.addView(metric("全网累计 Ban", accountStore.cachedGlobalBanTotal()?.toString() ?: "--", BLUE) { globalTotalValue = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metrics.addView(View(this).apply { setBackgroundColor(Color.rgb(222, 228, 235)) }, LinearLayout.LayoutParams(dp(1), dp(42)).apply { marginStart = dp(16); marginEnd = dp(16) })
        metrics.addView(metric("本机贡献", contribution.toString(), RED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        contributionCard.addView(metrics)
        val currentLevel = achievementLevel(contribution)
        val currentAchievement = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(16), 0, dp(14)) }
        currentAchievement.addView(ImageView(this).apply {
            setImageResource(achievementDrawable(contribution)); adjustViewBounds = true; scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { showAchievementOverlay(currentLevel, contribution) }
        }, LinearLayout.LayoutParams(dp(92), dp(92)))
        currentAchievement.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            addView(TextView(this@MainActivity).apply { text = "当前成就"; textSize = 12f; setTextColor(MUTED) })
            addView(TextView(this@MainActivity).apply {
                text = if (contribution >= 10) achievementTitle(contribution) else "首枚徽章待解锁"
                textSize = 19f; setTypeface(typeface, Typeface.BOLD); setTextColor(INK); setPadding(0, dp(4), 0, 0)
            })
            val nextLevel = if (contribution >= achievementThreshold(10)) null else (1..10).first { contribution < achievementThreshold(it) }
            addView(TextView(this@MainActivity).apply {
                text = nextLevel?.let { "再处理 ${achievementThreshold(it) - contribution} 条，解锁 Lv.$it ${achievementTitle(achievementThreshold(it)).removePrefix("Lv.$it ")}" } ?: "已解锁全部 10 枚成就徽章"
                textSize = 12f; setTextColor(MUTED); setPadding(0, dp(5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        contributionCard.addView(currentAchievement)
        val badgeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (level in 1..10) {
            val unlocked = contribution >= achievementThreshold(level)
            badgeRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = rounded(if (unlocked) Color.rgb(232, 241, 255) else Color.rgb(241, 243, 245), 13f)
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(achievementDrawableForLevel(level)); adjustViewBounds = true; scaleType = ImageView.ScaleType.CENTER_INSIDE
                    alpha = if (unlocked) 1f else 0.32f
                    setOnClickListener { showAchievementOverlay(level, contribution) }
                }, LinearLayout.LayoutParams(dp(62), dp(62)))
                addView(TextView(this@MainActivity).apply {
                    text = "Lv.$level"; textSize = 10f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
                    setTextColor(if (unlocked) BLUE else MUTED)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
                setOnClickListener { showAchievementOverlay(level, contribution) }
            }, LinearLayout.LayoutParams(dp(74), dp(92)).apply { marginEnd = dp(10) })
        }
        contributionCard.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(badgeRow) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)))
        content.addView(sectionLabel("贡献与成就"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20); bottomMargin = dp(2) })
        content.addView(contributionCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        Thread {
            accountStore.refreshGlobalBanTotal().onSuccess { total ->
                runOnUiThread { globalTotalValue?.takeIf { it.isAttachedToWindow }?.text = total.toString() }
            }
        }.start()
        content.addView(dangerOutlineButton("退出账号") {
            Thread { accountStore.logout(); runOnUiThread { showAccountPage() } }.start()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(18) })
        showPage("账号", scrollPage(content))
    }

    private fun metric(label: String, value: String, color: Int, onValueReady: ((TextView) -> Unit)? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(MUTED) })
        addView(TextView(this@MainActivity).apply {
            text = value; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(color); setPadding(0, dp(3), 0, 0)
            onValueReady?.invoke(this)
        })
    }

    private fun achievementTitle(contribution: Long): String = when {
        contribution >= 300_000 -> "Lv.10 终局守护"
        contribution >= 100_000 -> "Lv.9 裁决官"
        contribution >= 30_000 -> "Lv.8 破障者"
        contribution >= 10_000 -> "Lv.7 万级猎人"
        contribution >= 3_000 -> "Lv.6 净域使"
        contribution >= 1_000 -> "Lv.5 守望者"
        contribution >= 300 -> "Lv.4 先锋"
        contribution >= 100 -> "Lv.3 猎手"
        contribution >= 30 -> "Lv.2 清道夫"
        contribution >= 10 -> "Lv.1 侦察员"
        else -> "待解锁"
    }

    private fun achievementLevel(contribution: Long): Int = when {
        contribution >= 300_000 -> 10; contribution >= 100_000 -> 9; contribution >= 30_000 -> 8; contribution >= 10_000 -> 7; contribution >= 3_000 -> 6; contribution >= 1_000 -> 5; contribution >= 300 -> 4; contribution >= 100 -> 3; contribution >= 30 -> 2; contribution >= 10 -> 1; else -> 1
    }
    private fun achievementThreshold(level: Int): Long = listOf(10L, 30L, 100L, 300L, 1_000L, 3_000L, 10_000L, 30_000L, 100_000L, 300_000L)[level - 1]
    private fun achievementDrawable(contribution: Long) = achievementDrawableForLevel(achievementLevel(contribution))
    private fun achievementDrawableForLevel(level: Int): Int = resources.getIdentifier("contribution_badge_%02d".format(level), "drawable", packageName)
    private fun showAchievementOverlay(level: Int, contribution: Long) {
        val dialog = Dialog(this)
        val threshold = achievementThreshold(level)
        val unlocked = contribution >= threshold
        dialog.setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(32), dp(32), dp(32), dp(32)); background = rounded(Color.TRANSPARENT, 24f)
            isClickable = true
            setOnClickListener { dialog.dismiss() }
            addView(ImageView(context).apply { setImageResource(achievementDrawableForLevel(level)); adjustViewBounds = true; alpha = if (unlocked) 1f else .32f }, LinearLayout.LayoutParams(dp(236), dp(236)))
            addView(TextView(context).apply { text = "Lv.$level ${achievementTitle(threshold)}"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
            addView(TextView(context).apply { text = "达成条件：累计贡献 $threshold 条"; textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(13), 0, dp(6))
                addView(TextView(context).apply { text = "贡献进度"; textSize = 12f; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply { text = "$contribution / $threshold"; textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = ((contribution.coerceAtMost(threshold).toDouble() / threshold) * 1000).toInt()
                progressDrawable.setTint(if (unlocked) Color.rgb(236, 184, 43) else BLUE)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        })
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); dialog.window?.attributes = dialog.window?.attributes?.apply { dimAmount = .72f }; dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showRulesPage() {
        val content = pageContent()
        val config = try {
            JSONObject(pluginManager.mergeRules(ruleStore.rulesJson()))
        } catch (error: Exception) {
            Log.w("AutoBanMainActivity", "规则配置解析失败，使用空规则", error)
            JSONObject()
        }
        val states = try {
            JSONObject(ruleStore.ruleStatesJson())
        } catch (error: Exception) {
            Log.w("AutoBanMainActivity", "规则状态解析失败，使用默认状态", error)
            JSONObject()
        }
        val remoteRules = config.optJSONArray("rules") ?: JSONArray()
        val enabledCount = (0 until remoteRules.length()).count { index ->
            val rule = remoteRules.optJSONObject(index) ?: return@count false
            if (states.has(rule.optString("id"))) states.optBoolean(rule.optString("id"))
            else rule.optBoolean("enabled", true)
        }
        content.addView(sectionLabel("自动处理"))

        val autoCard = card()
        val autoRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        autoRow.addView(TextView(this).apply {
            text = "自动 Ban 命中账号\n仅在 X 接口确认成功后记入记录"
            textSize = 14f
            setTextColor(INK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        autoRow.addView(Switch(this).apply {
            isChecked = ruleStore.autoBanEnabled
            setOnCheckedChangeListener { _, enabled ->
                ruleStore.autoBanEnabled = enabled
                injectContent()
            }
        })
        autoCard.addView(autoRow)
        content.addView(autoCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        content.addView(sectionLabel("匹配规则（$enabledCount/${remoteRules.length()} 已启用）"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })

        for (index in 0 until remoteRules.length()) {
            val rule = remoteRules.optJSONObject(index) ?: continue
            val id = rule.optString("id")
            if (id.isBlank()) continue
            val ruleCard = card()
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = rule.optString("name", id)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(INK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Switch(this).apply {
                isChecked = if (states.has(id)) states.optBoolean(id) else rule.optBoolean("enabled", true)
                setOnCheckedChangeListener { _, enabled ->
                    ruleStore.setRuleEnabled(id, enabled)
                    injectContent()
                }
            })
            ruleCard.addView(row)
            ruleCard.addView(TextView(this).apply {
                text = rule.optString("description", "由在线规则服务管理")
                textSize = 12f
                setTextColor(MUTED)
                setPadding(0, dp(6), 0, 0)
            })
            content.addView(ruleCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
        }

        showPage("规则", scrollPage(content))
    }

    private fun refreshRulesFromToolbar() {
        toolbarAction.isEnabled = false
        toolbarAction.alpha = 0.55f
        ruleStore.refreshRules { updated ->
            runOnUiThread {
                toolbarAction.isEnabled = true
                toolbarAction.alpha = 1f
                showRulesPage()
            }
        }
    }

    private fun showHistoryPage() {
        val history = queue.history()
        val pending = queue.queueSnapshot()
        val content = pageContent()
        var loadNextHistoryPage: (() -> Unit)? = null

        if (pending.length() > 0) {
            content.addView(sectionLabel("处理队列（${pending.length()}）"))
            for (index in 0 until pending.length()) {
                val item = pending.optJSONObject(index) ?: continue
                val username = item.optString("username")
                val row = card().apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(10), dp(10))
                    addView(TextView(context).apply {
                        text = "@${ruleStore.displayAccount(username)}\n${item.optString("operationState", "等待处理")}"
                        textSize = 14f; setTextColor(INK); setTypeface(typeface, Typeface.BOLD)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = "⋯"; textSize = 24f; setTextColor(MUTED); gravity = Gravity.CENTER
                        setOnClickListener { anchor ->
                            android.widget.PopupMenu(this@MainActivity, anchor).apply {
                                menu.add(Menu.NONE, 1, 1, "立即重试")
                                menu.add(Menu.NONE, 2, 2, "移出队列")
                                setOnMenuItemClickListener { selected ->
                                    if (selected.itemId == 1) queue.retryQueued(username) { runOnUiThread { showHistoryPage() } }
                                    else queue.removeQueued(username) { runOnUiThread { showHistoryPage() } }
                                    true
                                }
                                show()
                            }
                        }
                    }, LinearLayout.LayoutParams(dp(38), dp(38)))
                }
                content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
            }
        }

        content.addView(sectionLabel("记录"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = if (pending.length() > 0) dp(18) else 0 })
        if (history.length() == 0) {
            content.addView(card().apply {
                gravity = Gravity.CENTER
                addView(TextView(context).apply {
                    text = "还没有已确认的 Ban记录\n命中规则后，记录会显示在这里"
                    textSize = 14f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(dp(8), dp(26), dp(8), dp(26))
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        } else {
            val records = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(records)
            var nextHistoryIndex = 0
            var loadingHistoryPage = false

            fun appendHistoryRecord(item: JSONObject) {
                val username = item.optString("username").trim()
                val isWhitelisted = ruleStore.isWhitelisted(username)
                val isBlocked = item.optString("unblockedAt").isBlank()
                val record = card().apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
                val header = LinearLayout(this).apply { gravity = Gravity.TOP or Gravity.CENTER_VERTICAL }
                header.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val displayName = item.optString("displayName").trim()
                    addView(TextView(this@MainActivity).apply { text = if (displayName.isBlank()) "@$username" else displayName; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(INK); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    if (displayName.isNotBlank()) addView(TextView(this@MainActivity).apply { text = "@$username"; textSize = 12f; setTextColor(MUTED) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                header.addView(TextView(this@MainActivity).apply {
                    text = "⋯"; textSize = 24f; setTextColor(INK); gravity = Gravity.CENTER
                    setOnClickListener { anchor ->
                        android.widget.PopupMenu(this@MainActivity, anchor).apply {
                            menu.add(Menu.NONE, 1, 1, if (isWhitelisted) "移出白名单" else "加入白名单")
                            menu.add(Menu.NONE, 2, 2, if (isBlocked) "取消屏蔽和隐藏" else "重新屏蔽和隐藏")
                            setOnMenuItemClickListener { selected ->
                                when (selected.itemId) {
                                    1 -> {
                                        if (isWhitelisted) {
                                            ruleStore.removeAccount(username)
                                            if (accountStore.session() != null) Thread { accountStore.sync() }.start()
                                            injectContent(); showHistoryPage()
                                        } else {
                                            ruleStore.rememberAccount(username)
                                            if (accountStore.session() != null) Thread { accountStore.sync() }.start()
                                            injectContent()
                                            queue.unblock(item) { runOnUiThread { showHistoryPage() } }
                                        }
                                    }
                                    2 -> {
                                        if (isBlocked) queue.unblock(item) { runOnUiThread { showHistoryPage() } }
                                        else queue.reblock(item) { runOnUiThread { showHistoryPage() } }
                                    }
                                }
                                true
                            }
                            show()
                        }
                    }
                }, LinearLayout.LayoutParams(dp(32), dp(32)))
                record.addView(header)
                if (item.optString("content").isNotBlank()) record.addView(TextView(this).apply {
                    text = item.optString("content"); textSize = 12f; setTextColor(INK); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, 0)
                })
                val matchedKeywords = item.optJSONArray("matchedKeywords")?.let { values -> List(values.length()) { values.optString(it).trim() }.filter { it.isNotBlank() }.distinct() }.orEmpty()
                val evidence = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(SURFACE_MUTED, 9f); setPadding(dp(8), dp(6), dp(8), dp(6)) }
                if (matchedKeywords.isNotEmpty()) evidence.addView(TextView(this).apply { text = "关键词：${matchedKeywords.joinToString("、")}"; textSize = 11f; setTextColor(BLUE); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                evidence.addView(TextView(this).apply { text = "规则依据：${item.optString("reason").ifBlank { "规则命中（旧记录未保存详情）" }}"; textSize = 11f; setTextColor(MUTED); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                record.addView(evidence, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
                val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
                footer.addView(TextView(this).apply {
                    text = if (isBlocked) "已屏蔽 + 隐藏" else "已取消"; textSize = 10f; setTextColor(if (isBlocked) RED else MUTED)
                    background = rounded(if (isBlocked) Color.rgb(254, 236, 238) else SURFACE_MUTED, 12f); setPadding(dp(7), dp(4), dp(7), dp(4))
                })
                if (isWhitelisted) footer.addView(TextView(this).apply { text = "白名单"; textSize = 10f; setTextColor(Color.rgb(52, 143, 88)); background = rounded(Color.rgb(232, 247, 237), 12f); setPadding(dp(7), dp(4), dp(7), dp(4)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
                footer.addView(TextView(this).apply { text = formatLocalTime(item.optString("blockedAt")); textSize = 10f; setTextColor(MUTED); gravity = Gravity.END }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                record.addView(footer)
                records.addView(record, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
            fun appendHistoryPage() {
                if (loadingHistoryPage || nextHistoryIndex >= history.length()) return
                loadingHistoryPage = true
                val endExclusive = minOf(nextHistoryIndex + HISTORY_PAGE_SIZE, history.length())
                for (index in nextHistoryIndex until endExclusive) history.optJSONObject(index)?.let(::appendHistoryRecord)
                nextHistoryIndex = endExclusive
                loadingHistoryPage = false
            }
            loadNextHistoryPage = ::appendHistoryPage
            appendHistoryPage()
        }
        val page = scrollPage(content)
        page.setOnScrollChangeListener { _, _, scrollY, _, _ -> if (scrollY + page.height >= content.height - dp(320)) loadNextHistoryPage?.invoke() }
        showPage("Ban记录", page)
    }

    private fun showWhitelistPage() {
        val accounts = ruleStore.accountWhitelist().toList().sorted()
        val content = pageContent()
        content.addView(pageIntro("本机白名单", "仅在此设备生效，不会上传服务端。白名单账号不会进入自动 Ban 队列。", "${accounts.size} 个账号"))

        val addCard = card()
        whitelistInput = EditText(this).apply {
            hint = "输入 X 用户名，例如 AAAGodofWealth"
            textSize = 14f
            isSingleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(Color.WHITE, 12f, BORDER)
        }
        addCard.addView(whitelistInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        addCard.addView(primaryButton("加入本机白名单") {
            val username = whitelistInput?.text?.toString().orEmpty().trim()
            if (BlockJob.isValidUsername(username)) {
                ruleStore.rememberAccount(username)
                if (accountStore.session() != null) Thread { accountStore.sync() }.start()
                injectContent()
                showWhitelistPage()
            } else {
                whitelistInput?.error = "请输入有效的 X 用户名"
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })
        content.addView(addCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        accounts.forEach { username ->
            val row = card()
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            line.addView(TextView(this).apply {
                text = "@${ruleStore.displayAccount(username)}"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(INK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (username.lowercase() == "aagodofwealth") {
                line.addView(TextView(this).apply {
                    text = "默认"
                    textSize = 12f
                    setTextColor(BLUE)
                })
            } else {
                line.addView(outlineButton("移除") {
                    ruleStore.removeAccount(username)
                    if (accountStore.session() != null) Thread { accountStore.sync() }.start()
                    injectContent()
                    showWhitelistPage()
                }, LinearLayout.LayoutParams(dp(72), dp(38)))
            }
            row.addView(line)
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        showPage("白名单", scrollPage(content))
    }

    private fun formatLocalTime(value: String): String {
        return try {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(value))
        } catch (_: Exception) {
            "时间未知"
        }
    }

    private fun showPluginsPage() {
        val content = pageContent()
        val plugins = pluginManager.list()
        content.addView(pageIntro("插件管理", "安装 .xplugin 后，插件脚本会和当前规则一起注入 X。", "${plugins.size} 个插件"))
        plugins.forEach { plugin ->
            val pluginCard = card()
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = "${plugin.name}\n${plugin.id}  ·  v${plugin.version}"
                textSize = 14f
                setTextColor(INK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!plugin.builtIn) {
                row.addView(Switch(this).apply {
                    isChecked = plugin.enabled
                    setOnCheckedChangeListener { _, enabled -> pluginManager.setEnabled(plugin.id, enabled) }
                })
            } else {
                row.addView(TextView(this).apply {
                    text = "内置"
                    textSize = 11f
                    setTextColor(GREEN)
                    background = rounded(Color.rgb(232, 250, 242), 999f)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                })
            }
            pluginCard.addView(row)
            content.addView(pluginCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        content.addView(primaryButton("导入 .xplugin") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/zip"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, REQUEST_PLUGIN)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        content.addView(outlineButton("应用插件并刷新 X") { webView.reload() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })
        showPage("插件管理", scrollPage(content))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            val callback = fileChooserCallback
            fileChooserCallback = null
            val cameraUri = pendingCameraUri
            pendingCameraUri = null
            val cameraResult = resultCode == RESULT_OK && data?.data == null && data?.clipData == null
            if (cameraUri != null && cameraResult) {
                finalizeCameraUri(cameraUri)
                callback?.onReceiveValue(arrayOf(cameraUri))
                return
            }
            cameraUri?.let(::deletePendingCameraUri)
            if (resultCode != RESULT_OK || data == null) {
                callback?.onReceiveValue(null)
                return
            }
            val uris = buildList {
                data.clipData?.let { clips ->
                    for (index in 0 until clips.itemCount) add(clips.getItemAt(index).uri)
                }
                if (isEmpty()) data.data?.let(::add)
            }
            callback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
            return
        }
        if (requestCode != REQUEST_PLUGIN || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            try {
                val plugin = pluginManager.install(uri)
                runOnUiThread {
                    Toast.makeText(this, "已安装插件：${plugin.name}", Toast.LENGTH_SHORT).show()
                    webView.reload()
                }
            } catch (error: Exception) {
                runOnUiThread { Toast.makeText(this, "插件安装失败：${error.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun registerSystemBackGesture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                handleBackNavigation()
            }
        }
    }

    private fun handleBackNavigation() {
        when (BackNavigationPolicy.decide(currentPage == PAGE_HOME)) {
            BackNavigationAction.SHOW_HOME -> toolbarBack.performClick()
            BackNavigationAction.CLICK_PAGE_BACK -> clickPageBackOrReload()
        }
    }

    private fun clickPageBackOrReload() {
        webView.evaluateJavascript(PAGE_BACK_BUTTON_SCRIPT) { backAction ->
            when (backAction) {
                "1", "2" -> Unit
                else -> if (webView.canGoBack()) webView.goBack() else webView.reload()
            }
        }
    }

    override fun onPause() {
        stopAppHeartbeat()
        accountStore.stopSettingsStream()
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        startAppHeartbeat()
        accountStore.startSettingsStream { runOnUiThread { injectContent() } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        CookieManager.getInstance().flush()
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopAppHeartbeat()
        accountStore.stopSettingsStream()
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingCameraUri?.let(::deletePendingCameraUri)
        pendingCameraUri = null
        queue.close()
        webView.removeJavascriptInterface("AutoBanBridge")
        webView.destroy()
        super.onDestroy()
    }

    private fun startAppHeartbeat() {
        if (heartbeatActive) return
        heartbeatActive = true
        heartbeatHandler.post(heartbeatRunnable)
    }

    private fun stopAppHeartbeat() {
        heartbeatActive = false
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun header(request: WebResourceRequest, name: String): String? = request.requestHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value

    private fun isXApiHost(host: String): Boolean {
        return host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com")
    }

    private fun createGalleryIntent(params: WebChromeClient.FileChooserParams?): Intent {
        val allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                if (allowMultiple) {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MAX_POST_MEDIA)
                }
            }
        }
        return Intent(Intent.ACTION_PICK).apply {
            data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun createCameraIntent(): Intent? {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) == null) return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AutoBanRobot-camera-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AutoBanRobot")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: return null
        pendingCameraUri = uri
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cameraIntent.clipData = ClipData.newRawUri("AutoBanRobot", uri)
        return cameraIntent
    }

    private fun finalizeCameraUri(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        }
    }

    private fun deletePendingCameraUri(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
        } catch (error: Exception) {
            Log.w("AutoBanMainActivity", "临时照片清理失败", error)
        }
    }

    private fun applySystemBars(light: Boolean) {
        window.statusBarColor = if (light) SURFACE else INK
        window.navigationBarColor = if (light) Color.WHITE else INK
        var flags = if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && light) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun decodeJavascriptString(encoded: String): String? {
        return try {
            if (encoded == "null") null else JSONArray("[$encoded]").getString(0)
        } catch (error: Exception) {
            Log.w("AutoBanMainActivity", "选中文字解析失败", error)
            null
        }
    }

    private fun addSelectedKeyword(mode: ActionMode) {
        webView.evaluateJavascript(
            "JSON.stringify(String(window.getSelection ? window.getSelection() : '').trim())"
        ) { encodedSelection ->
            val selectedText = decodeJavascriptString(encodedSelection)
            runOnUiThread {
                val keyword = selectedText?.trim().orEmpty()
                if (keyword.isEmpty()) {
                    Toast.makeText(this, "没有读取到选中的文字", Toast.LENGTH_SHORT).show()
                } else if (ruleStore.addKeyword(keyword)) {
                    injectContent()
                    status.text = "已添加屏蔽词 · 立即生效"
                    Toast.makeText(this, "已添加屏蔽词：${keyword.take(40)}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "该文字已经在屏蔽关键词中", Toast.LENGTH_SHORT).show()
                }
                mode.finish()
            }
        }
    }

    private fun pageContent(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(18), dp(16), dp(26))
    }

    private fun scrollPage(content: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(SURFACE)
        clipToPadding = false
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun pageIntro(title: String, subtitle: String, badge: String): LinearLayout = card().apply {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = title
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(INK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(context).apply {
            text = badge
            textSize = 11f
            setTextColor(BLUE)
            background = rounded(Color.rgb(232, 245, 253), 999f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        })
        addView(row)
        addView(TextView(context).apply {
            text = subtitle
            textSize = 12f
            setTextColor(MUTED)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(INK)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 18f, BORDER)
    }

    private fun primaryButton(label: String, action: (Button) -> Unit): Button = styledButton(label, BLUE, Color.WHITE, null, action)

    private fun outlineButton(label: String, action: (Button) -> Unit): Button = styledButton(label, Color.WHITE, BLUE, BLUE, action)

    private fun dangerOutlineButton(label: String, action: (Button) -> Unit): Button = styledButton(label, Color.WHITE, RED, RED, action)

    private fun disabledButton(label: String): Button = styledButton(label, SURFACE_MUTED, MUTED, BORDER) { }.apply {
        isEnabled = false
    }

    private fun styledButton(label: String, fill: Int, textColor: Int, stroke: Int?, action: (Button) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(textColor)
        minHeight = 0
        minimumHeight = 0
        stateListAnimator = null
        background = rounded(fill, 14f, stroke)
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { action(this) }
    }

    private fun navButton(label: String, icon: Int, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(0, dp(1), 0, dp(1))
        addView(ImageView(context).apply { setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(INK) }, LinearLayout.LayoutParams(dp(20), dp(20)))
        addView(TextView(context).apply { text = label; textSize = 10f; setTextColor(INK); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(15)))
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PLUGIN = 9001
        private const val REQUEST_FILE_CHOOSER = 9003
        private const val MAX_POST_MEDIA = 4
        private const val APP_HEARTBEAT_INTERVAL_MS = 30_000L
        private const val APP_VERSION = "1.0.42"
        private const val HISTORY_PAGE_SIZE = 10
        private const val PAGE_HOME = "home"
        private const val PAGE_BACK_BUTTON_SCRIPT = """
            (() => {
              const selectors = [
                '[data-testid="app-bar-back"]',
                '[data-testid*="back" i]',
                '[aria-label="Back"]',
                '[aria-label="返回"]',
                '[aria-label="上一页"]'
              ];
              const candidates = selectors.flatMap(selector =>
                Array.from(document.querySelectorAll(selector))
              );
              const target = candidates.find(element => {
                const rect = element.getBoundingClientRect();
                const style = window.getComputedStyle(element);
                return rect.width > 0 && rect.height > 0 &&
                  rect.left < window.innerWidth * 0.45 &&
                  rect.top < Math.min(window.innerHeight * 0.3, 220) &&
                  style.visibility !== 'hidden' && style.display !== 'none';
              });
              if (target) {
                target.click();
                return 1;
              }
              // X uses SPA history for routes such as /messages. A messages
              // inbox has no visible arrow but should still return to the
              // preceding X route rather than reload the current inbox.
              if (window.history.length > 1) {
                window.history.back();
                return 2;
              }
              return 0;
            })()
        """
        private val INK = Color.rgb(15, 20, 25)
        private val MUTED = Color.rgb(113, 118, 123)
        private val SURFACE = Color.rgb(247, 249, 250)
        private val SURFACE_MUTED = Color.rgb(247, 249, 249)
        private val BORDER = Color.rgb(239, 243, 244)
        private val BLUE = Color.rgb(29, 155, 240)
        private val RED = Color.rgb(224, 36, 94)
        private val GREEN = Color.rgb(0, 186, 124)
        private val AUTH_CAPTURE_SCRIPT = """
            (() => {
              if (window.__AUTOBANROBOT_AUTH_CAPTURE__) return;
              window.__AUTOBANROBOT_AUTH_CAPTURE__ = true;
              let bearer = '';
              let csrf = '';
              const emit = (name, value) => {
                const key = String(name || '').toLowerCase();
                if (key === 'authorization') bearer = String(value || '');
                if (key === 'x-csrf-token') csrf = String(value || '');
                if ((bearer || csrf) && window.AutoBanBridge) {
                  try { window.AutoBanBridge.updateAuth(bearer, csrf); } catch (_) {}
                }
              };
              const readHeaders = headers => {
                if (!headers) return;
                if (typeof headers.forEach === 'function') {
                  headers.forEach((value, name) => emit(name, value));
                  return;
                }
                if (Array.isArray(headers)) {
                  headers.forEach(pair => emit(pair && pair[0], pair && pair[1]));
                  return;
                }
                Object.entries(headers).forEach(([name, value]) => emit(name, value));
              };
              const originalFetch = window.fetch;
              window.fetch = function(input, init) {
                try {
                  if (input instanceof Request) readHeaders(input.headers);
                  readHeaders(init && init.headers);
                } catch (_) {}
                return originalFetch.apply(this, arguments);
              };
              const originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
              XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                emit(name, value);
                return originalSetRequestHeader.apply(this, arguments);
              };
            })();
        """.trimIndent()
        private val VIEWER_USERNAME_SCRIPT = """
            (() => {
              const send = () => {
                const links = [...document.querySelectorAll('a[href]')];
                const pathOf = item => {
                  try { return new URL(item.getAttribute('href') || '', location.origin).pathname; }
                  catch (_) { return ''; }
                };
                const preferred = links.find(item =>
                  item.matches('a[data-testid="AppTabBar_Profile_Link"], a[aria-label*="Profile" i], a[aria-label*="个人资料"]')
                );
                const reserved = new Set([
                  'home', 'explore', 'notifications', 'messages', 'bookmarks', 'lists',
                  'communities', 'i', 'settings', 'compose', 'search'
                ]);
                const topProfile = links.find(item => {
                  const rect = item.getBoundingClientRect();
                  const path = pathOf(item).replace(/^\//u, '').replace(/\/$/u, '');
                  return rect.top >= 0 && rect.top < 220 && rect.left >= 0 && rect.left < 220 &&
                    /^[A-Za-z0-9_]{1,15}$/u.test(path) && !reserved.has(path.toLowerCase());
                });
                const link = preferred || topProfile || links.find(item => {
                  const testId = String(item.getAttribute('data-testid') || '').toLowerCase();
                  const label = String(item.getAttribute('aria-label') || '').toLowerCase();
                  const path = pathOf(item).replace(/^\//u, '').replace(/\/$/u, '');
                  return (testId.includes('profile') || label.includes('profile') || label.includes('个人资料')) &&
                    /^[A-Za-z0-9_]{1,15}$/u.test(path);
                });
                const match = /^\/([A-Za-z0-9_]{1,15})\/?$/u.exec(pathOf(link));
                if (match && window.AutoBanBridge) {
                  try { window.AutoBanBridge.updateViewerUsername(match[1]); } catch (_) {}
                }
              };
              send();
              setTimeout(send, 1000);
              setTimeout(send, 3000);
            })();
        """.trimIndent()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun reportScanDiagnostic(message: String) {
            Log.i("AutoBanScan", message.take(300))
        }

        @JavascriptInterface
        fun enqueueBlock(payload: String) {
            if (ruleStore.autoBanEnabled) queue.enqueue(payload)
        }

        @JavascriptInterface
        fun updateAuth(bearer: String?, csrf: String?) {
            queue.updateAuth(bearer, csrf)
        }

        @JavascriptInterface
        fun updateViewerUsername(username: String?) {
            Log.i("AutoBanAuth", "页面识别当前账号 username=${username ?: "空"}")
            queue.updateViewerUsername(username)
        }

        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message.take(160), Toast.LENGTH_SHORT).show() }
        }
    }

}
