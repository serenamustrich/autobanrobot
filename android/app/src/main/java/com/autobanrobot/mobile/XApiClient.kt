package com.autobanrobot.mobile

import android.webkit.CookieManager
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class ApiOutcome(val state: String, val message: String = "")

class AuthState {
    @Volatile var bearer: String? = null
    @Volatile var csrf: String? = null
    @Volatile var viewerUsername: String? = null
    @Volatile var viewerLookupPending = false
}

class XApiClient(private val auth: AuthState) {
    private companion object { const val TAG = "AutoBanXApiClient" }
    fun block(job: BlockJob): ApiOutcome {
        if (job.username.equals("AAAGodofWealth", ignoreCase = true)) {
            return ApiOutcome("skipped", "忽略作者账号")
        }
        val bearer = auth.bearer
        // A queued job can contain a stale ct0. Prefer the current token
        // captured from the WebView, then fall back to the job snapshot.
        val csrf = auth.csrf?.takeIf { it.isNotBlank() }
            ?: job.csrf.takeIf { it.isNotBlank() }
        if (bearer.isNullOrBlank() || csrf.isNullOrBlank()) return ApiOutcome("retry", "尚未捕获 X 登录会话")
        if (auth.viewerUsername?.equals(job.username, ignoreCase = true) == true) {
            return ApiOutcome("skipped", "忽略当前登录账号")
        }

        val hostname = normalizeHostname(job.hostname)
        val relationship = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(job.username)}", "GET", bearer, csrf)
        val source = relationship.body?.optJSONObject("relationship")?.optJSONObject("source")
        if (relationship.code !in 200..299 || source == null) return retryable(relationship.code, "无法确认关注关系")
        if (source.optBoolean("following")) return ApiOutcome("skipped", "你正在关注该账号")

        var muted = booleanValue(source, "muting") == true
        var blocking = booleanValue(source, "blocking") == true
        if (booleanValue(source, "muting") != true) {
            val created = request(hostname, "/i/api/1.1/mutes/users/create.json", "POST", bearer, csrf, "screen_name=${encode(job.username)}")
            if (!isSuccessfulAction(created)) return retryable(created.code, "隐藏请求失败（HTTP ${created.code}）")
            muted = true
        }
        if (booleanValue(source, "blocking") != true) {
            val created = request(hostname, "/i/api/1.1/blocks/create.json", "POST", bearer, csrf, "screen_name=${encode(job.username)}")
            if (!isSuccessfulAction(created)) return retryable(created.code, "屏蔽请求失败（HTTP ${created.code}）")
            blocking = true
        }

        val verified = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(job.username)}", "GET", bearer, csrf)
        val verifiedSource = verified.body?.optJSONObject("relationship")?.optJSONObject("source")
        return if (verified.code in 200..299 && verifiedSource != null &&
            (booleanValue(verifiedSource, "blocking") ?: blocking) &&
            (booleanValue(verifiedSource, "muting") ?: muted)
        ) {
            ApiOutcome("success", "已确认屏蔽和隐藏")
        } else {
            ApiOutcome("retry", "X 未同时确认屏蔽和隐藏成功")
        }
    }

    fun mute(record: JSONObject): ApiOutcome {
        val username = record.optString("username").trim()
        if (!BlockJob.isValidUsername(username)) return ApiOutcome("failed", "账号用户名无效")
        val bearer = auth.bearer
        val csrf = auth.csrf?.takeIf { it.isNotBlank() }
            ?: record.optString("csrf").takeIf { it.isNotBlank() }
        if (bearer.isNullOrBlank() || csrf.isNullOrBlank()) return ApiOutcome("retry", "尚未捕获 X 登录会话")
        val hostname = hostnameFor(record)
        val relationship = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(username)}", "GET", bearer, csrf)
        val source = relationship.body?.optJSONObject("relationship")?.optJSONObject("source")
        if (relationship.code !in 200..299 || source == null) return retryable(relationship.code, "无法确认当前隐藏状态")
        if (booleanValue(source, "muting") == true) return ApiOutcome("already-muted", "该账号已经处于隐藏状态")

        val created = request(hostname, "/i/api/1.1/mutes/users/create.json", "POST", bearer, csrf, "screen_name=${encode(username)}")
        if (!isSuccessfulAction(created)) return retryable(created.code, "隐藏请求失败")
        val verified = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(username)}", "GET", bearer, csrf)
        val verifiedSource = verified.body?.optJSONObject("relationship")?.optJSONObject("source")
        return if (verified.code in 200..299 && verifiedSource != null && booleanValue(verifiedSource, "muting") != false) {
            ApiOutcome("success", "已确认隐藏")
        } else {
            ApiOutcome("retry", "X 未确认隐藏成功")
        }
    }

    fun unblock(record: JSONObject): ApiOutcome {
        val username = record.optString("username").trim()
        if (!BlockJob.isValidUsername(username)) return ApiOutcome("failed", "账号用户名无效")
        val bearer = auth.bearer
        val csrf = record.optString("csrf").takeIf { it.isNotBlank() } ?: auth.csrf
        if (bearer.isNullOrBlank() || csrf.isNullOrBlank()) return ApiOutcome("retry", "尚未捕获 X 登录会话")

        val hostname = hostnameFor(record)
        val relationship = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(username)}", "GET", bearer, csrf)
        val source = relationship.body?.optJSONObject("relationship")?.optJSONObject("source")
        if (relationship.code !in 200..299 || source == null) return retryable(relationship.code, "无法确认当前屏蔽状态")

        var unmuted = booleanValue(source, "muting") == false
        var unblocked = booleanValue(source, "blocking") == false
        if (booleanValue(source, "muting") != false) {
            val destroyed = request(hostname, "/i/api/1.1/mutes/users/destroy.json", "POST", bearer, csrf, "screen_name=${encode(username)}")
            if (!isSuccessfulAction(destroyed)) return retryable(destroyed.code, "取消隐藏失败")
            unmuted = true
        }
        if (booleanValue(source, "blocking") != false) {
            val destroyed = request(hostname, "/i/api/1.1/blocks/destroy.json", "POST", bearer, csrf, "screen_name=${encode(username)}")
            if (!isSuccessfulAction(destroyed)) return retryable(destroyed.code, "取消屏蔽失败")
            unblocked = true
        }

        val verified = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(username)}", "GET", bearer, csrf)
        val verifiedSource = verified.body?.optJSONObject("relationship")?.optJSONObject("source")
        return if (verified.code in 200..299 && verifiedSource != null &&
            !(booleanValue(verifiedSource, "blocking") ?: !unblocked) &&
            !(booleanValue(verifiedSource, "muting") ?: !unmuted)
        ) {
            ApiOutcome("success", "已确认取消屏蔽和隐藏")
        } else {
            ApiOutcome("retry", "X 未同时确认取消屏蔽和隐藏成功")
        }
    }

    fun upload(record: JSONObject): Boolean {
        return try {
            val connection = URL("https://ban.richccy.com/api/bans").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("x-autoban-client", "android-webview")
            val payload = JSONObject(record.toString()).put("clientType", "app")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            connection.responseCode in 200..299
        } catch (error: Exception) {
            Log.e(TAG, "Ban 记录上传失败，将由本地队列重试", error)
            false
        }
    }

    fun currentUsername(): String? {
        val bearer = auth.bearer ?: return null
        val csrf = auth.csrf ?: return null
        val result = request(
            "api.x.com",
            "/1.1/account/verify_credentials.json?skip_status=true&include_entities=false",
            "GET",
            bearer,
            csrf
        )
        // Never recursively search arbitrary nested JSON: a response may
        // contain other users and that was the source of the old false match.
        val username = if (result.code in 200..299) {
            result.body?.optString("screen_name")?.trim()
                ?.takeIf { BlockJob.isValidUsername(it) }
                ?: result.body?.optJSONObject("user")?.optString("screen_name")?.trim()
                    ?.takeIf { BlockJob.isValidUsername(it) }
        } else null
        Log.i(TAG, "当前账号验证 code=${result.code} username=${username ?: "未确认"}")
        if (username != null) return username

        // Keep one exact-shape fallback for X deployments that expose the
        // account settings endpoint but do not expose verify_credentials.
        val fallback = request("x.com", "/i/api/1.1/account/settings.json", "GET", bearer, csrf)
        val fallbackUsername = if (fallback.code in 200..299) {
            fallback.body?.optString("screen_name")?.trim()
                ?.takeIf { BlockJob.isValidUsername(it) }
        } else null
        Log.i(TAG, "当前账号备用接口验证 code=${fallback.code} username=${fallbackUsername ?: "未确认"}")
        if (fallbackUsername != null) return fallbackUsername
        return null
    }

    fun sendHeartbeat(installationId: String, version: String): Boolean {
        return try {
            val connection = URL("https://ban.richccy.com/api/clients/heartbeat")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("x-autoban-client", "android-webview")
            val payload = JSONObject()
                .put("installationId", installationId)
                .put("platform", "android-webview")
                .put("version", version)
                .put("clientType", "app")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            connection.responseCode in 200..299
        } catch (error: Exception) {
            Log.w(TAG, "App 在线心跳发送失败", error)
            false
        }
    }

    private fun request(hostname: String, path: String, method: String, bearer: String, csrf: String, body: String? = null): HttpResult {
        return try {
            val host = when (hostname) {
                "twitter.com" -> "twitter.com"
                "api.x.com" -> "api.x.com"
                else -> "x.com"
            }
            val url = URL("https://$host$path")
            CookieManager.getInstance().flush()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("authorization", bearer)
            connection.setRequestProperty("x-csrf-token", csrf)
            connection.setRequestProperty("x-twitter-active-user", "yes")
            connection.setRequestProperty("x-twitter-auth-type", "OAuth2Session")
            connection.setRequestProperty("content-type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("accept", "application/json, text/plain, */*")
            connection.setRequestProperty("origin", "https://$host")
            connection.setRequestProperty("referer", "https://$host/")
            val cookie = CookieManager.getInstance().getCookie("https://$host")
                ?: CookieManager.getInstance().getCookie("https://x.com")
            cookie?.let { connection.setRequestProperty("cookie", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val parsed = parse(text)
            Log.i(TAG, "X API $method ${path.substringBefore('?')} code=$code cookie=${cookie != null} body=${responseSummary(parsed)}")
            HttpResult(code, parsed)
        } catch (error: Exception) {
            Log.w(TAG, "X API 请求失败，等待后续重试", error)
            HttpResult(0, null)
        }
    }

    private fun retryable(code: Int, message: String): ApiOutcome {
        return if (code == 0 || code == 408 || code == 425 || code == 429 || code >= 500) {
            ApiOutcome("retry", if (code == 0) message else "$message（HTTP $code）")
        } else ApiOutcome("failed", "$message（HTTP $code）")
    }

    private fun isSuccessfulAction(result: HttpResult): Boolean {
        return result.code in 200..299 && (result.body?.optJSONArray("errors")?.length() ?: 0) == 0
    }

    private fun responseSummary(body: JSONObject?): String {
        if (body == null) return "invalid-json"
        val errors = body.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val first = errors.optJSONObject(0)
            return "error=${first?.optInt("code", 0)}:${first?.optString("message", "")?.take(80)}"
        }
        val source = body.optJSONObject("relationship")?.optJSONObject("source")
        return if (source != null) {
            "following=${source.optBoolean("following", false)},blocking=${source.optBoolean("blocking", false)},muting=${source.optBoolean("muting", false)}"
        } else "ok"
    }

    private fun parse(body: String): JSONObject? = try {
        JSONObject(body)
    } catch (error: Exception) {
        Log.w(TAG, "X API 响应不是有效 JSON", error)
        null
    }

    private fun confirmsBlock(body: JSONObject?, username: String): Boolean {
        if (body == null || body.optJSONArray("errors")?.length() ?: 0 > 0) return false
        val sameUser = body.optString("screen_name").isBlank() || body.optString("screen_name").equals(username, true)
        val relationship = body.optJSONObject("relationship")
        return sameUser && (body.optBoolean("blocking") || relationship?.optJSONObject("source")?.optBoolean("blocking") == true)
    }

    private fun confirmsUnblocked(body: JSONObject?): Boolean {
        val relationship = body?.optJSONObject("relationship") ?: return false
        return relationship.optJSONObject("source")?.optBoolean("blocking") == false
    }

    private fun booleanValue(value: JSONObject, key: String): Boolean? {
        return if (value.has(key) && !value.isNull(key)) value.optBoolean(key) else null
    }

    private fun normalizeHostname(value: String): String = if (value == "twitter.com") "twitter.com" else "x.com"

    private fun hostnameFor(record: JSONObject): String {
        return when {
            record.optString("hostname") == "twitter.com" -> "twitter.com"
            record.optString("pageUrl").contains("twitter.com", ignoreCase = true) -> "twitter.com"
            else -> "x.com"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

private data class HttpResult(val code: Int, val body: JSONObject?)
