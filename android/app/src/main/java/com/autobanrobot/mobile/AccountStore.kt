package com.autobanrobot.mobile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

data class AutoBanSession(val token: String, val username: String, val expiresAt: String)

class AccountStore(private val context: Context, private val rules: RuleStore) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "autoban_account", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val base = "https://ban.richccy.com/api"
    private val streamActive = AtomicBoolean(false)
    @Volatile private var streamConnection: HttpURLConnection? = null
    fun session(): AutoBanSession? = prefs.getString("session", null)?.let { raw -> runCatching { JSONObject(raw) }.getOrNull()?.let { AutoBanSession(it.optString("accessToken"), it.optString("username"), it.optString("expiresAt")) } }?.takeIf { it.token.isNotBlank() }
    /** Last known public total keeps the account page informative while the device is offline. */
    fun cachedGlobalBanTotal(): Long? = if (prefs.contains("global_ban_total")) prefs.getLong("global_ban_total", 0L) else null

    fun refreshGlobalBanTotal(): Result<Long> = runCatching {
        val result = request("/bans/stats", "GET", null, null)
        check(result.first in 200..299) { result.second.optString("code", "BAN_STATS_FAILED") }
        check(result.second.has("total")) { "BAN_STATS_FAILED" }
        result.second.optLong("total").coerceAtLeast(0L).also { total ->
            prefs.edit().putLong("global_ban_total", total).apply()
        }
    }

    fun logout() { session()?.let { request("/auth/logout", "POST", null, it.token) }; prefs.edit().remove("session").apply() }
    fun authenticate(mode: String, payload: JSONObject): Result<AutoBanSession> = runCatching {
        val result = request("/auth/$mode", "POST", payload, null)
        check(result.first in 200..299) { result.second.optString("code", "AUTH_FAILED") }
        val next = AutoBanSession(result.second.getString("accessToken"), result.second.getString("username"), result.second.getString("expiresAt"))
        prefs.edit().putString("session", result.second.toString()).apply()
        bindAndMerge(next)
        next
    }
    fun recoveryQuestion(username: String): Result<String> = runCatching {
        val result = request("/auth/recovery/question", "POST", JSONObject().put("username", username), null)
        check(result.first in 200..299) { result.second.optString("code", "AUTH_RECOVERY_INVALID") }
        result.second.getString("securityQuestionKey")
    }
    fun bindAndMerge(current: AutoBanSession = session() ?: error("AUTH_REQUIRED")): Result<Unit> = runCatching {
        val installationId = contextInstallationId()
        val bind = request("/auth/devices/bind", "POST", JSONObject().put("installationId", installationId), current.token)
        check(bind.first in 200..299) { bind.second.optString("code", "AUTH_BIND_FAILED") }
        sync(true, current).getOrThrow()
    }
    fun sync(merge: Boolean = false): Result<Unit> {
        val current = session() ?: return Result.failure(IllegalStateException("AUTH_REQUIRED"))
        return sync(merge, current)
    }

    /** Pull only: foreground recovery must never overwrite another device's newer settings. */
    fun pull(): Result<Unit> {
        val current = session() ?: return Result.failure(IllegalStateException("AUTH_REQUIRED"))
        return runCatching {
            val result = request("/account/settings", "GET", null, current.token)
            check(result.first in 200..299) { result.second.optString("code", "ACCOUNT_SYNC_FAILED") }
            rules.setKeywords(result.second.optJSONArray("keywords")?.toStringList() ?: emptyList())
            rules.replaceAccountWhitelist(result.second.optJSONArray("whitelist")?.toStringList() ?: emptyList())
        }
    }

    /** Account-scoped SSE is foreground-only; polling remains the recovery path. */
    fun startSettingsStream(onSettings: () -> Unit) {
        if (!streamActive.compareAndSet(false, true)) return
        Thread {
            while (streamActive.get()) {
                val current = session() ?: break
                try {
                    val connection = (URL(base + "/account/settings/stream").openConnection() as HttpURLConnection).also {
                        streamConnection = it
                        it.requestMethod = "GET"; it.connectTimeout = 8_000; it.readTimeout = 0
                        it.setRequestProperty("authorization", "Bearer ${current.token}")
                    }
                    if (connection.responseCode !in 200..299) break
                    connection.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (!streamActive.get()) return@forEach
                            if (!line.startsWith("data:")) return@forEach
                            val body = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: return@forEach
                            rules.setKeywords(body.optJSONArray("keywords")?.toStringList() ?: emptyList())
                            rules.replaceAccountWhitelist(body.optJSONArray("whitelist")?.toStringList() ?: emptyList())
                            onSettings()
                        }
                    }
                } catch (_: Exception) {
                    if (streamActive.get()) Thread.sleep(2_000)
                } finally {
                    streamConnection?.disconnect(); streamConnection = null
                }
            }
            streamActive.set(false)
        }.start()
    }

    fun stopSettingsStream() {
        streamActive.set(false)
        streamConnection?.disconnect()
        streamConnection = null
    }
    private fun sync(merge: Boolean, current: AutoBanSession): Result<Unit> = runCatching {
        val payload = JSONObject().put("keywords", JSONArray(rules.keywords())).put("whitelist", JSONArray(rules.accountWhitelist().toList()))
        val result = request("/account/settings${if (merge) "/merge" else ""}", if (merge) "POST" else "PUT", payload, current.token)
        check(result.first in 200..299) { result.second.optString("code", "ACCOUNT_SYNC_FAILED") }
        rules.setKeywords(result.second.optJSONArray("keywords")?.toStringList() ?: emptyList())
        rules.replaceAccountWhitelist(result.second.optJSONArray("whitelist")?.toStringList() ?: emptyList())
    }
    private fun contextInstallationId(): String {
        val client = context.getSharedPreferences("autoban_app_client", Context.MODE_PRIVATE)
        return client.getString("installation_id", null) ?: java.util.UUID.randomUUID().toString().also { client.edit().putString("installation_id", it).apply() }
    }
    private fun request(path: String, method: String, body: JSONObject?, token: String?): Pair<Int, JSONObject> {
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.requestMethod = method; connection.connectTimeout = 8_000; connection.readTimeout = 8_000
        connection.setRequestProperty("content-type", "application/json"); token?.let { connection.setRequestProperty("authorization", "Bearer $it") }
        if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
        return code to runCatching { JSONObject(text) }.getOrElse { JSONObject() }
    }
    private fun JSONArray.toStringList(): List<String> = buildList { for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add) }
}
