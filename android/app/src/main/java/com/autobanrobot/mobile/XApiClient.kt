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
}

class XApiClient(private val auth: AuthState) {
    private companion object { const val TAG = "AutoBanXApiClient" }
    fun block(job: BlockJob): ApiOutcome {
        val bearer = auth.bearer
        val csrf = job.csrf.takeIf { it.isNotBlank() } ?: auth.csrf
        if (bearer.isNullOrBlank() || csrf.isNullOrBlank()) return ApiOutcome("retry", "尚未捕获 X 登录会话")

        val relationship = request(job.hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(job.username)}", "GET", bearer, csrf)
        val source = relationship.body?.optJSONObject("relationship")?.optJSONObject("source")
        if (relationship.code !in 200..299 || source == null) return retryable(relationship.code, "无法确认关注关系")
        if (source.optBoolean("following")) return ApiOutcome("skipped", "你正在关注该账号")
        if (source.optBoolean("blocking")) return ApiOutcome("already-blocked", "该账号已经处于屏蔽状态")

        val created = request(job.hostname, "/i/api/1.1/blocks/create.json", "POST", bearer, csrf, "screen_name=${encode(job.username)}")
        if (created.code !in 200..299) return retryable(created.code, "屏蔽请求失败（HTTP ${created.code}）")
        if (confirmsBlock(created.body, job.username)) return ApiOutcome("success")

        val verified = request(job.hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(job.username)}", "GET", bearer, csrf)
        return if (verified.code in 200..299 && confirmsBlock(verified.body, job.username)) {
            ApiOutcome("success")
        } else {
            ApiOutcome("retry", "X 未确认屏蔽成功")
        }
    }

    fun unblock(record: JSONObject): ApiOutcome {
        val username = record.optString("username").trim()
        if (!BlockJob.isValidUsername(username)) return ApiOutcome("failed", "账号用户名无效")
        val bearer = auth.bearer
        val csrf = record.optString("csrf").takeIf { it.isNotBlank() } ?: auth.csrf
        if (bearer.isNullOrBlank() || csrf.isNullOrBlank()) return ApiOutcome("retry", "尚未捕获 X 登录会话")

        val hostname = when {
            record.optString("hostname") == "twitter.com" -> "twitter.com"
            record.optString("pageUrl").contains("twitter.com", ignoreCase = true) -> "twitter.com"
            else -> "x.com"
        }
        val relationship = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(username)}", "GET", bearer, csrf)
        val source = relationship.body?.optJSONObject("relationship")?.optJSONObject("source")
        if (relationship.code !in 200..299 || source == null) return retryable(relationship.code, "无法确认当前屏蔽状态")
        if (!source.optBoolean("blocking")) return ApiOutcome("already-unblocked", "该账号已经取消屏蔽")

        val destroyed = request(hostname, "/i/api/1.1/blocks/destroy.json", "POST", bearer, csrf, "screen_name=${encode(username)}")
        if (destroyed.code !in 200..299 || destroyed.body?.optJSONArray("errors")?.length() ?: 0 > 0) {
            return retryable(destroyed.code, "取消屏蔽失败")
        }
        val verified = request(hostname, "/i/api/1.1/friendships/show.json?target_screen_name=${encode(username)}", "GET", bearer, csrf)
        return if (verified.code in 200..299 && confirmsUnblocked(verified.body)) {
            ApiOutcome("success", "已确认取消屏蔽")
        } else {
            ApiOutcome("retry", "X 未确认取消屏蔽成功")
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
            connection.outputStream.use { it.write(record.toString().toByteArray()) }
            connection.responseCode in 200..299
        } catch (error: Exception) {
            Log.e(TAG, "Ban 记录上传失败，将由本地队列重试", error)
            false
        }
    }

    private fun request(hostname: String, path: String, method: String, bearer: String, csrf: String, body: String? = null): HttpResult {
        return try {
            val host = if (hostname == "twitter.com") "twitter.com" else "x.com"
            val url = URL("https://$host$path")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("authorization", bearer)
            connection.setRequestProperty("x-csrf-token", csrf)
            connection.setRequestProperty("x-twitter-active-user", "yes")
            connection.setRequestProperty("x-twitter-auth-type", "OAuth2Session")
            connection.setRequestProperty("content-type", "application/x-www-form-urlencoded")
            CookieManager.getInstance().getCookie("https://$host")?.let { connection.setRequestProperty("cookie", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResult(code, parse(text))
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

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

private data class HttpResult(val code: Int, val body: JSONObject?)
