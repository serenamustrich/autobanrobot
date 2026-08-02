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
    private val onResult: (BlockJob, ApiOutcome) -> Unit
) {
    private companion object { const val TAG = "AutoBanBlockQueue" }
    private val prefs = context.getSharedPreferences("autoban_queue", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private val jobs = mutableListOf<BlockJob>()
    private var pageReady = false
    private var pageHost = "x.com"

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
        if (!bearer.isNullOrBlank()) auth.bearer = bearer
        if (!csrf.isNullOrBlank()) auth.csrf = csrf
        processSoon()
    }

    fun setPageReady(hostname: String?, ready: Boolean) {
        pageHost = if (hostname == "twitter.com") "twitter.com" else "x.com"
        pageReady = ready
        if (ready) processSoon()
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
            synchronized(jobs) {
                val existing = jobs.indexOfFirst { it.username.equals(job.username, true) }
                if (existing >= 0) jobs[existing] = job else jobs += job
                persist()
            }
            processInternal()
        }
    }

    fun queueSize(): Int = synchronized(jobs) { jobs.size }

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

    fun unblock(record: JSONObject, onComplete: (ApiOutcome) -> Unit) {
        executor.execute {
            val username = record.optString("username").trim()
            synchronized(jobs) {
                if (jobs.removeAll { it.username.equals(username, true) }) persist()
            }
            val outcome = api.unblock(record)
            if (outcome.state == "success" || outcome.state == "already-unblocked") {
                markUnblocked(record)
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
        if (!pageReady || processing.getAndSet(true)) return
        try {
            flushUploads()
            while (true) {
                val job = synchronized(jobs) { jobs.firstOrNull() } ?: break
                if (auth.bearer.isNullOrBlank()) break
                val outcome = api.block(job.copy(hostname = pageHost))
                when {
                    outcome.state == "retry" && job.attempts < 2 -> {
                        synchronized(jobs) {
                            jobs.removeAt(0)
                            job.attempts += 1
                            jobs += job
                            persist()
                        }
                        break
                    }
                    else -> {
                        synchronized(jobs) {
                            jobs.removeAt(0)
                            persist()
                        }
                        if (outcome.state == "success" || outcome.state == "already-blocked") recordSuccess(job, outcome)
                        onResult(job, outcome)
                        Thread.sleep(500)
                    }
                }
            }
        } finally {
            processing.set(false)
        }
    }

    private fun recordSuccess(job: BlockJob, outcome: ApiOutcome) {
        val record = job.toJson().apply {
            put("clientEventId", UUID.randomUUID().toString())
            put("blockedAt", java.time.Instant.now().toString())
            put("confirmedState", outcome.state)
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
