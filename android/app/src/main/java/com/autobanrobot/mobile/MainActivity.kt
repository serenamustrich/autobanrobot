package com.autobanrobot.mobile

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var screenshotButton: Button
    private var currentPage = PAGE_HOME
    private var topInsetPx = 0
    private lateinit var ruleStore: RuleStore
    private lateinit var pluginManager: PluginManager
    private lateinit var queue: BlockQueue
    private lateinit var auth: AuthState
    private lateinit var api: XApiClient
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var whitelistInput: EditText? = null
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatActive = false
    private val appInstallationId by lazy {
        val preferences = getSharedPreferences("autoban_app_client", MODE_PRIVATE)
        preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).apply()
        }
    }
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            Thread { api.sendHeartbeat(appInstallationId, APP_VERSION) }.start()
            if (heartbeatActive) heartbeatHandler.postDelayed(this, APP_HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleStore = RuleStore(this)
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
            setBackgroundColor(INK)
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
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
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
        }
        screenshotButton = styledButton("截图", BLUE, Color.WHITE, null) {
            saveWatermarkedScreenshot()
        }.apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(32))
        }
        val statusRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(2), dp(6), dp(2))
            background = rounded(SURFACE_MUTED, 14f)
            addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(screenshotButton)
        }
        homeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(statusRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        bottomNav.addView(navButton("关键词") { showKeywordsPage() })
        bottomNav.addView(navButton("规则") { showRulesPage() })
        bottomNav.addView(navButton("Ban记录") { showHistoryPage() })
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
            bottomNav.setPadding(dp(8), dp(2), dp(8), bottomInset + dp(2))
            if (toolbar.visibility == View.GONE) {
                homeContainer.setPadding(dp(10), topInset + dp(8), dp(10), dp(8))
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
        applySystemBars(light = false)
        toolbar.visibility = View.VISIBLE
        toolbarBack.visibility = View.VISIBLE
        toolbarTitle.text = title
        toolbarAction.isClickable = false
        if (title == "规则") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "更新"
            toolbarAction.setTextColor(Color.WHITE)
            toolbarAction.background = rounded(Color.rgb(38, 45, 51), 12f)
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener { refreshRulesFromToolbar() }
        } else if (title == "Ban记录") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "白名单"
            toolbarAction.setTextColor(Color.WHITE)
            toolbarAction.background = rounded(Color.rgb(38, 45, 51), 12f)
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener { showWhitelistPage() }
        } else if (title == "白名单") {
            toolbarAction.visibility = View.VISIBLE
            toolbarAction.text = "添加"
            toolbarAction.setTextColor(Color.WHITE)
            toolbarAction.background = rounded(Color.rgb(38, 45, 51), 12f)
            toolbarAction.isClickable = true
            toolbarAction.setOnClickListener {
                whitelistInput?.requestFocus()
            }
        } else {
            toolbarAction.visibility = View.GONE
        }
        screenFrame.removeAllViews()
        screenFrame.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bottomNav.visibility = View.GONE
    }

    private fun showKeywordsPage() {
        val content = pageContent()
        content.addView(pageIntro("屏蔽关键词", "命中后会在当前 X 页面实时标记，并交给自动 Ban 队列处理。", "${ruleStore.keywords().size} 个关键词"))
        val card = card().apply {
            addView(sectionLabel("关键词列表"))
            addView(TextView(context).apply {
                text = "每行填写一个关键词"
                textSize = 12f
                setTextColor(MUTED)
            })
            val input = EditText(context).apply {
                hint = "例如：同城\n上门\n兼职"
                setText(ruleStore.keywords().joinToString("\n"))
                textSize = 14f
                gravity = Gravity.TOP
                minLines = 12
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(SURFACE_MUTED, 14f, BORDER)
            }
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)).apply {
                topMargin = dp(10)
            })
            val popularStatus = TextView(context).apply {
                text = "仅在点击时读取，不会强制同步"
                textSize = 11f
                setTextColor(MUTED)
                setPadding(0, dp(8), 0, 0)
            }
            addView(outlineButton("加载热门关键词") { button ->
                button.isEnabled = false
                button.alpha = 0.55f
                popularStatus.text = "正在从线上服务读取热门关键词…"
                ruleStore.loadPopularKeywords { popular, _ ->
                    runOnUiThread {
                        button.isEnabled = true
                        button.alpha = 1f
                        if (popular == null) {
                            popularStatus.text = "无法连接热门关键词服务，请稍后重试"
                        } else {
                            val current = input.text.toString().split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            val merged = LinkedHashSet<String>()
                            merged.addAll(current)
                            merged.addAll(popular)
                            val added = merged.size - current.size
                            input.setText(merged.joinToString("\n"))
                            popularStatus.text = "已加载 ${popular.size} 个热门词，新增 $added 个；请确认后点击保存"
                        }
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(12)
            })
            addView(popularStatus)
            addView(primaryButton("保存关键词") {
                ruleStore.setKeywords(input.text.toString().split("\n"))
                injectContent()
                status.text = "关键词已保存，立即生效"
                showHome()
                Toast.makeText(this@MainActivity, "关键词已保存", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(14)
            })
        }
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        showPage("关键词", scrollPage(content))
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
            rule.optBoolean("enabled", true) && states.optBoolean(rule.optString("id"), true)
        }
        content.addView(pageIntro("屏蔽规则", "把复杂的屏蔽逻辑拆成清晰开关，规则修改后会立即重新扫描当前页面。", "$enabledCount/${remoteRules.length()} 条已启用"))

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
        content.addView(autoCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

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
                isChecked = rule.optBoolean("enabled", true) && states.optBoolean(id, true)
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
            content.addView(ruleCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
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
        content.addView(pageIntro("Ban记录", "这里只展示已经被 X 接口确认成功的记录。", "${history.length()} 条已确认"))
        if (pending.length() > 0) {
            content.addView(sectionLabel("处理队列（${pending.length()}）"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            })
            for (index in 0 until pending.length()) {
                val item = pending.optJSONObject(index) ?: continue
                val username = item.optString("username")
                val queueCard = card()
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(TextView(this).apply {
                    text = "@${ruleStore.displayAccount(username)}"
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(INK)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(this).apply {
                    text = item.optString("operationState", "等待处理")
                    textSize = 12f
                    setTextColor(BLUE)
                })
                queueCard.addView(row)
                queueCard.addView(TextView(this).apply {
                    text = "尝试次数：${item.optInt("attempts", 0)}"
                    textSize = 12f
                    setTextColor(MUTED)
                    setPadding(0, dp(6), 0, 0)
                })
                val queueActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                queueActions.addView(outlineButton("立即重试") {
                    queue.retryQueued(username) { runOnUiThread { showHistoryPage() } }
                }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { topMargin = dp(10) })
                queueActions.addView(dangerOutlineButton("移出队列") {
                    queue.removeQueued(username) { runOnUiThread { showHistoryPage() } }
                }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    topMargin = dp(10)
                    marginStart = dp(8)
                })
                queueCard.addView(queueActions)
                content.addView(queueCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            }
        }
        if (history.length() == 0) {
            val empty = card().apply {
                gravity = Gravity.CENTER
                addView(TextView(context).apply {
                    text = "还没有已确认的 Ban记录\n命中规则后，记录会显示在这里"
                    textSize = 14f
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(28), dp(8), dp(28))
                })
            }
            content.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        } else {
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                val record = card()
                record.addView(TextView(this).apply {
                    val displayName = item.optString("displayName").trim()
                    text = if (displayName.isBlank()) "@${item.optString("username")}" else "$displayName  (@${item.optString("username")})"
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(INK)
                })
                record.addView(TextView(this).apply {
                    text = "${formatLocalTime(item.optString("blockedAt"))} · ${item.optString("reason").ifBlank { "规则命中" }}"
                    textSize = 12f
                    setTextColor(MUTED)
                    setPadding(0, dp(6), 0, 0)
                })
                if (item.optString("content").isNotBlank()) record.addView(TextView(this).apply {
                    text = item.optString("content")
                    textSize = 12f
                    setTextColor(INK)
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    background = rounded(SURFACE_MUTED, 12f)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    setPaddingRelative(dp(10), dp(9), dp(10), dp(9))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(9)
                    }
                })
                val username = item.optString("username").trim()
                val whitelistAction = if (ruleStore.isWhitelisted(username)) {
                    outlineButton("移出白名单") {
                        ruleStore.removeAccount(username)
                        injectContent()
                        showHistoryPage()
                    }
                } else {
                    outlineButton("加入白名单") { button ->
                        ruleStore.rememberAccount(username)
                        injectContent()
                        button.text = "移出白名单"
                        button.isEnabled = true
                        button.setOnClickListener {
                            ruleStore.removeAccount(username)
                            injectContent()
                            showHistoryPage()
                        }
                        queue.unblock(item) { outcome ->
                            runOnUiThread {
                                if (outcome.state == "success" || outcome.state == "already-unblocked") {
                                    showHistoryPage()
                                }
                            }
                        }
                    }
                }
                record.addView(whitelistAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(10)
                })
                val unblockedAt = item.optString("unblockedAt")
                val action = if (unblockedAt.isBlank()) {
                    dangerOutlineButton("取消屏蔽和隐藏") { button ->
                        button.isEnabled = false
                        button.text = "处理中…"
                        queue.unblock(item) { outcome ->
                            runOnUiThread {
                                if (outcome.state == "success" || outcome.state == "already-unblocked") {
                                    showHistoryPage()
                                } else {
                                    button.isEnabled = true
                                    button.text = "重试取消屏蔽和隐藏"
                                }
                            }
                        }
                    }
                } else {
                    outlineButton("重新屏蔽和隐藏") { button ->
                        button.isEnabled = false
                        button.text = "处理中…"
                        queue.reblock(item) { outcome ->
                            runOnUiThread {
                                if (outcome.state == "success" || outcome.state == "already-blocked") {
                                    showHistoryPage()
                                } else {
                                    button.isEnabled = true
                                    button.text = "重试重新屏蔽和隐藏"
                                }
                            }
                        }
                    }
                }
                record.addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(10)
                })
                content.addView(record, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            }
        }
        content.addView(outlineButton("清空本地记录") {
            queue.clearHistory()
            Toast.makeText(this, "已清空本地记录，不会解除 X 上的 Ban", Toast.LENGTH_SHORT).show()
            showHistoryPage()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        showPage("Ban记录", scrollPage(content))
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

    private fun saveWatermarkedScreenshot() {
        val view = window.decorView.rootView
        if (view.width <= 0 || view.height <= 0) {
            Toast.makeText(this, "当前页面还未准备好", Toast.LENGTH_SHORT).show()
            return
        }
        screenshotButton.isEnabled = false
        Thread {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dp(14).toFloat()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val label = "AutoBanRobot"
            val centerX = bitmap.width / 2f
            val baseline = dp(28).toFloat()
            val halfWidth = paint.measureText(label) / 2f + dp(14)
            val top = dp(6).toFloat()
            val bottom = dp(38).toFloat()
            val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 15, 20, 25) }
            canvas.drawRoundRect(centerX - halfWidth, top, centerX + halfWidth, bottom, dp(16).toFloat(), dp(16).toFloat(), pill)
            canvas.drawText(label, centerX, baseline, paint)

            val fileName = "AutoBanRobot-${System.currentTimeMillis()}.png"
            val saved = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AutoBanRobot")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val uri = contentResolver.insert(collection, values) ?: error("无法创建图片文件")
                    try {
                        contentResolver.openOutputStream(uri).use { output ->
                            requireNotNull(output) { "无法写入图片文件" }
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    } catch (error: Exception) {
                        contentResolver.delete(uri, null, null)
                        throw error
                    }
                    true
                } else {
                    val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
                    directory.mkdirs()
                    java.io.FileOutputStream(java.io.File(directory, fileName)).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    true
                }
            } catch (error: Exception) {
                Log.e("AutoBanMainActivity", "截图保存失败", error)
                false
            } finally {
                bitmap.recycle()
            }
            runOnUiThread {
                screenshotButton.isEnabled = true
                Toast.makeText(this, if (saved) "截图已保存到图片/AutoBanRobot" else "截图保存失败", Toast.LENGTH_SHORT).show()
            }
        }.start()
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
        webView.evaluateJavascript(PAGE_BACK_BUTTON_SCRIPT) { clicked ->
            if (clicked != "true") webView.reload()
        }
    }

    override fun onPause() {
        stopAppHeartbeat()
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        startAppHeartbeat()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        CookieManager.getInstance().flush()
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopAppHeartbeat()
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

    private fun navButton(label: String, action: () -> Unit): View = TextView(this).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.WHITE, 14f)
        setPadding(dp(6), 0, dp(6), 0)
        text = label
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(INK)
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }
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
        private const val APP_HEARTBEAT_INTERVAL_MS = 60_000L
        private const val APP_VERSION = "1.0.38"
        private const val PAGE_HOME = "home"
        private const val PAGE_BACK_BUTTON_SCRIPT = """
            (() => {
              const selectors = [
                '[data-testid="app-bar-back"]',
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
              if (!target) return false;
              target.click();
              return true;
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
