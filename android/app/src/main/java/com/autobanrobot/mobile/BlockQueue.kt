package com.autobanrobot.mobile

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class BlockJob(
    val username: String,
    val displayName: String,
    val reason: String,
    val matchedKeywords: List<String>,
    val configuredKeywords: List<String>,
    val content: String,
    val pageUrl: String,
    val pageKey: String,
    val hostname: String,
    val csrf: String,
    var attempts: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("username", username)
        put("displayName", displayName)
        put("reason", reason)
        put("matchedKeywords", JSONArray(matchedKeywords))
        put("configuredKeywords", JSONArray(configuredKeywords))
        put("content", content)
        put("pageUrl", pageUrl)
        put("pageKey", pageKey)
        put("hostname", hostname)
        put("csrf", csrf)
        put("attempts", attempts)
    }

    companion object {
        fun fromJson(json: JSONObject): BlockJob? {
            val username = json.optString("username").trim()
            if (!isValidUsername(username)) return null
            fun array(name: String): List<String> {
                val value = json.optJSONArray(name) ?: return emptyList()
                return List(value.length()) { value.optString(it) }.filter { it.isNotBlank() }
            }
            return BlockJob(
                username = username,
                displayName = json.optString("displayName").take(160),
                reason = json.optString("reason").take(500),
                matchedKeywords = array("matchedKeywords").take(30),
                configuredKeywords = array("configuredKeywords").take(1000),
                content = json.optString("content").take(1000),
                pageUrl = json.optString("pageUrl").take(1000),
                pageKey = json.optString("pageKey").take(300),
                hostname = if (json.optString("hostname") == "twitter.com") "twitter.com" else "x.com",
                csrf = json.optString("csrf").take(300),
                attempts = json.optInt("attempts", 0)
            )
        }

        fun isValidUsername(value: String): Boolean = Regex("[A-Za-z0-9_]{1,15}").matches(value.trim())
    }
}

class BlockQueue(
    context: Context,
    private val rules: RuleStore,
    private val auth: AuthState,
    private val api: XApiClient,
    private val onProgress: (String) -> Unit = {},
    private val onResult: (BlockJob, ApiOutcome) -> Unit
) {
    private companion object {
        const val BLOCK_INTERVAL_MS = 500L
        const val OWNER_USERNAME = "aagodofwealth"
        const val HISTORY_HIDE_MIGRATION_KEY = "history_hide_migration_v1"
        const val PROCESSED_ACCOUNTS_KEY = "processed_accounts"
        const val TAG = "AutoBanBlockQueue"
    }
    private val prefs = context.getSharedPreferences("autoban_queue", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private val jobs = mutableListOf<BlockJob>()
    private val cancelledUsers = mutableSetOf<String>()
    private var activeUsername: String? = null
    private var activeState = "等待处理"
    private var viewerLookupAt = 0L

    init {
        val saved = try {
            JSONArray(prefs.getString("queue", "[]"))
        } catch (error: Exception) {
            Log.w(TAG, "本地 Ban 队列损坏，使用空队列", error)
            JSONArray()
        }
        for (index in 0 until saved.length()) BlockJob.fromJson(saved.optJSONObject(index) ?: JSONObject())?.let(jobs::add)
    }

    fun updateAuth(bearer: String?, csrf: String?) {
        if (!bearer.isNullOrBlank() && bearer != auth.bearer) {
            auth.bearer = bearer
            auth.viewerUsername = null
            auth.viewerLookupPending = true
            val capturedBearer = bearer
            Log.i(TAG, "开始验证当前登录账号")
            Thread {
                val detected = api.currentUsername()
                Log.i(TAG, "当前登录账号验证完成 username=${detected ?: "未确认"}")
                if (auth.bearer == capturedBearer) {
                    auth.viewerUsername = detected
                    auth.viewerLookupPending = false
                    processSoon()
                }
            }.start()
        }
        if (!csrf.isNullOrBlank()) auth.csrf = csrf
        processSoon()
    }

    fun setPageReady(hostname: String?, ready: Boolean) {
        if (ready) processSoon()
    }

    fun updateViewerUsername(username: String?) {
        val normalized = username?.trim()?.takeIf { BlockJob.isValidUsername(it) } ?: return
        // Do not infer the logged-in account from feed/profile DOM. That can
        // match another tweet author and must never create a whitelist entry.
        if (normalized.equals(OWNER_USERNAME, ignoreCase = true)) {
            auth.viewerUsername = OWNER_USERNAME
        }
    }

    fun enqueue(payload: String) {
        executor.execute {
            if (!rules.autoBanEnabled) return@execute
            val job = try {
                BlockJob.fromJson(JSONObject(payload))
            } catch (error: Exception) {
                Log.w(TAG, "忽略无法解析的 Ban 入队消息", error)
                null
            } ?: return@execute
            if (isWhitelisted(job.username)) {
                onResult(job, ApiOutcome("skipped", "忽略白名单账号"))
                return@execute
            }
            if (isProcessed(job.username)) return@execute
            synchronized(jobs) {
                val existing = jobs.indexOfFirst { it.username.equals(job.username, true) }
                if (existing >= 0) jobs[existing] = job else jobs += job
                persist()
            }
            onProgress("处理中 @${job.username}")
            processInternal()
        }
    }

    fun queueSize(): Int = synchronized(jobs) { jobs.size }

    fun queueSnapshot(): JSONArray = synchronized(jobs) {
        JSONArray().also { snapshot ->
            jobs.forEach { job ->
                snapshot.put(job.toJson().apply {
                    put("operationState", if (job.username.equals(activeUsername, true)) activeState else "等待处理")
                })
            }
        }
    }

    fun removeQueued(username: String, onComplete: () -> Unit = {}) {
        executor.execute {
            synchronized(jobs) {
                cancelledUsers += username.trim().lowercase()
                jobs.removeAll { it.username.equals(username, true) }
                persist()
                if (activeUsername.equals(username, true)) activeState = "已移出队列"
            }
            onComplete()
        }
    }

    fun retryQueued(username: String, onComplete: () -> Unit = {}) {
        executor.execute {
            synchronized(jobs) {
                cancelledUsers.remove(username.trim().lowercase())
                jobs.firstOrNull { it.username.equals(username, true) }?.attempts = 0
                persist()
            }
            onComplete()
            processInternal()
        }
    }

    fun history(): JSONArray {
        return try {
            JSONArray(prefs.getString("history", "[]"))
        } catch (error: Exception) {
            Log.w(TAG, "本地 Ban 历史损坏，使用空历史", error)
            JSONArray()
        }
    }

    fun clearHistory() {
        prefs.edit().putString("history", "[]").apply()
    }

    fun restoreHistoryVisuals() {
        executor.execute {
            val history = history()
            var restored = 0
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                if (item.optString("unblockedAt").isNotBlank() || !item.optBoolean("hidden")) continue
                BlockJob.fromJson(item)?.let { job ->
                    restored += 1
                    onResult(job, ApiOutcome("success", "历史记录同步：已恢复"))
                }
            }
            onProgress(if (restored == 0) "历史屏蔽和隐藏标记已同步" else "已恢复 $restored 条历史屏蔽和隐藏标记")
        }
    }

    fun unblock(record: JSONObject, onComplete: (ApiOutcome) -> Unit) {
        executor.execute {
            val username = record.optString("username").trim()
            synchronized(jobs) {
                if (jobs.removeAll { it.username.equals(username, true) }) persist()
            }
            val outcome = api.unblock(record)
            if (outcome.state == "success" || outcome.state == "already-unblocked") {
                markUnblocked(record)
                markProcessed(username, false)
            }
            onComplete(outcome)
        }
    }

    fun reblock(record: JSONObject, onComplete: (ApiOutcome) -> Unit) {
        executor.execute {
            val job = BlockJob.fromJson(record)
            if (job == null) {
                onComplete(ApiOutcome("failed", "记录中的账号用户名无效"))
                return@execute
            }
            val outcome = api.block(job)
            if (outcome.state == "success" || outcome.state == "already-blocked") {
                recordSuccess(job, outcome)
            }
            onComplete(outcome)
        }
    }

    fun close() = executor.shutdownNow()

    private fun processSoon() {
        executor.execute { processInternal() }
    }

    private fun processInternal() {
        if (processing.getAndSet(true)) return
        try {
            while (true) {
                val job = synchronized(jobs) { jobs.firstOrNull() } ?: break
                synchronized(jobs) {
                    activeUsername = job.username
                    activeState = "处理中"
                }
                if (cancelledUsers.remove(job.username.trim().lowercase())) {
                    synchronized(jobs) {
                        jobs.removeAll { it.username.equals(job.username, true) }
                        persist()
                    }
                    onResult(job, ApiOutcome("skipped", "已移出处理队列"))
                    continue
                }
                if (isWhitelisted(job.username)) {
                    activeState = "已跳过：白名单账号"
                    synchronized(jobs) {
                        jobs.removeAt(0)
                        persist()
                    }
                    onResult(job, ApiOutcome("skipped", "忽略白名单账号"))
                    continue
                }
                if (isProcessed(job.username)) {
                    activeState = "已跳过：本机已有处理记录"
                    synchronized(jobs) {
                        jobs.removeAt(0)
                        persist()
                    }
                    onResult(job, ApiOutcome("skipped", "本机已有处理记录"))
                    continue
                }
                if (auth.bearer.isNullOrBlank()) {
                    activeState = "等待登录会话"
                    onProgress("等待登录会话 @${job.username}")
                    break
                }
                activeState = "请求屏蔽和隐藏"
                val outcome = api.block(job)
                when {
                    outcome.state == "success" -> {
                        synchronized(jobs) {
                            jobs.removeAt(0)
                            persist()
                        }
                        recordSuccess(job, outcome)
                        onResult(job, outcome)
                        Thread.sleep(BLOCK_INTERVAL_MS)
                    }
                    outcome.state == "skipped" -> {
                        synchronized(jobs) {
                            jobs.removeAt(0)
                            persist()
                        }
                        onResult(job, outcome)
                        Thread.sleep(BLOCK_INTERVAL_MS)
                    }
                    outcome.state == "retry" -> {
                        activeState = "等待重试"
                        synchronized(jobs) {
                            jobs.removeAt(0)
                            job.attempts += 1
                            jobs += job
                            persist()
                        }
                        onProgress("处理中 @${job.username}")
                        Thread.sleep(BLOCK_INTERVAL_MS)
                    }
                    else -> {
                        synchronized(jobs) {
                            jobs.removeAt(0)
                            persist()
                        }
                        onResult(job, outcome)
                        Thread.sleep(BLOCK_INTERVAL_MS)
                    }
                }
            }
            synchronized(jobs) {
                if (jobs.isEmpty()) {
                    activeUsername = null
                    activeState = "等待处理"
                }
            }
        } finally {
            processing.set(false)
        }
    }

    private fun migrateHistoryHides() {
        if (prefs.getBoolean(HISTORY_HIDE_MIGRATION_KEY, false)) return
        if (auth.bearer.isNullOrBlank() || auth.csrf.isNullOrBlank()) return
        val history = history()
        if (history.length() == 0) {
            prefs.edit().putBoolean(HISTORY_HIDE_MIGRATION_KEY, true).apply()
            return
        }
        val updated = JSONArray()
        var complete = true
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optString("unblockedAt").isNotBlank() || item.optBoolean("hidden")) {
                updated.put(item)
                continue
            }
            val username = item.optString("username")
            onProgress("正在同步历史隐藏：@$username")
            val outcome = api.mute(item)
            if (outcome.state == "success" || outcome.state == "already-muted") {
                item.put("hidden", true)
                item.put("hiddenAt", Instant.now().toString())
                BlockJob.fromJson(item)?.let { job ->
                    onResult(job, ApiOutcome("success", "历史记录同步：已确认隐藏"))
                }
            } else {
                complete = false
                onProgress("历史隐藏失败：@$username，稍后重试")
            }
            updated.put(item)
            Thread.sleep(BLOCK_INTERVAL_MS)
        }
        prefs.edit().putString("history", updated.toString()).apply()
        if (complete) {
            prefs.edit().putBoolean(HISTORY_HIDE_MIGRATION_KEY, true).apply()
            onProgress("历史记录隐藏同步完成")
        }
    }

    private fun isWhitelisted(username: String): Boolean {
        return username.trim().equals(OWNER_USERNAME, ignoreCase = true) || rules.isWhitelisted(username)
    }

    private fun recordSuccess(job: BlockJob, outcome: ApiOutcome) {
        val record = job.toJson().apply {
            put("clientEventId", UUID.randomUUID().toString())
            put("blockedAt", java.time.Instant.now().toString())
            put("confirmedState", outcome.state)
            put("action", "block+mute")
            put("hidden", true)
        }
        val history = history()
        val filtered = JSONArray()
        filtered.put(record)
        for (index in 0 until history.length()) {
            val old = history.optJSONObject(index) ?: continue
            if (!old.optString("username").equals(job.username, true)) filtered.put(old)
            if (filtered.length() >= 500) break
        }
        prefs.edit().putString("history", filtered.toString()).apply()
        markProcessed(job.username, true)
        if (!api.upload(record)) {
            val pending = readUploadQueue()
            pending.put(record)
            prefs.edit().putString("upload_queue", pending.toString()).apply()
        }
    }

    private fun markUnblocked(record: JSONObject) {
        val clientEventId = record.optString("clientEventId")
        val username = record.optString("username")
        val updatedAt = Instant.now().toString()
        val history = history()
        val updated = JSONArray()
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            val sameRecord = if (clientEventId.isNotBlank()) {
                item.optString("clientEventId") == clientEventId
            } else {
                item.optString("username").equals(username, true)
            }
            if (sameRecord) item.put("unblockedAt", updatedAt)
            updated.put(item)
        }
        prefs.edit().putString("history", updated.toString()).apply()
    }

    private fun isProcessed(username: String): Boolean {
        val normalized = username.trim().lowercase()
        if (normalized.isBlank()) return false
        val stored = try {
            JSONObject(prefs.getString(PROCESSED_ACCOUNTS_KEY, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        if (stored.optBoolean(normalized, false)) return true
        val history = history()
        for (index in 0 until history.length()) {
            val item = history.optJSONObject(index) ?: continue
            if (item.optString("unblockedAt").isBlank() &&
                item.optString("username").trim().equals(username.trim(), true)
            ) return true
        }
        return false
    }

    private fun markProcessed(username: String, processed: Boolean) {
        val normalized = username.trim().lowercase()
        if (normalized.isBlank()) return
        val stored = try {
            JSONObject(prefs.getString(PROCESSED_ACCOUNTS_KEY, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        if (processed) stored.put(normalized, true) else stored.remove(normalized)
        prefs.edit().putString(PROCESSED_ACCOUNTS_KEY, stored.toString()).apply()
    }

    private fun flushUploads() {
        val pending = readUploadQueue()
        if (pending.length() == 0) return
        val remaining = JSONArray()
        for (index in 0 until pending.length()) {
            val record = pending.optJSONObject(index) ?: continue
            if (!api.upload(record)) remaining.put(record)
        }
        prefs.edit().putString("upload_queue", remaining.toString()).apply()
    }

    private fun persist() {
        val array = JSONArray()
        synchronized(jobs) { jobs.forEach { array.put(it.toJson()) } }
        prefs.edit().putString("queue", array.toString()).apply()
    }

    private fun readUploadQueue(): JSONArray {
        return try {
            JSONArray(prefs.getString("upload_queue", "[]"))
        } catch (error: Exception) {
            Log.w(TAG, "本地上传队列损坏，使用空队列", error)
            JSONArray()
        }
    }
}
