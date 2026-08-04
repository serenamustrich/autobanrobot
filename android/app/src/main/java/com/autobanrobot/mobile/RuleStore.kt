package com.autobanrobot.mobile

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RuleStore(private val context: Context) {
    private companion object {
        const val TAG = "AutoBanRuleStore"
        const val ACCOUNT_WHITELIST_KEY = "account_whitelist"
        val DEFAULT_ACCOUNT_WHITELIST = setOf("aagodofwealth")
    }
    private val prefs = context.getSharedPreferences("autoban_settings", Context.MODE_PRIVATE)

    var autoBanEnabled: Boolean
        get() = prefs.getBoolean("auto_ban_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_ban_enabled", value).apply()

    fun keywords(): List<String> {
        val saved = prefs.getString("keywords", null)
        if (saved != null) return parseStringArray(saved)
        return parseStringArray(readAsset("content/default-keywords.json"))
    }

    fun setKeywords(values: List<String>) {
        val normalized = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(1000)
        prefs.edit().putString("keywords", JSONArray(normalized).toString()).apply()
    }

    fun addKeyword(value: String): Boolean {
        val keyword = value.trim()
        if (keyword.isEmpty()) return false
        val current = keywords()
        if (current.any { it == keyword }) return false
        setKeywords(current + keyword)
        return true
    }

    fun accountWhitelist(): Set<String> {
        val saved = prefs.getString(ACCOUNT_WHITELIST_KEY, null) ?: return DEFAULT_ACCOUNT_WHITELIST
        return try {
            val array = JSONArray(saved)
            DEFAULT_ACCOUNT_WHITELIST + buildSet {
                for (index in 0 until array.length()) {
                    val username = array.optString(index).trim()
                    if (BlockJob.isValidUsername(username)) add(username.lowercase())
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "账号白名单解析失败，使用空白名单", error)
            emptySet()
        }
    }

    fun rememberAccount(username: String): Boolean {
        val normalized = username.trim().takeIf { BlockJob.isValidUsername(it) }?.lowercase() ?: return false
        val current = accountWhitelist().toMutableSet()
        if (!current.add(normalized)) return false
        prefs.edit().putString(ACCOUNT_WHITELIST_KEY, JSONArray(current.toList().sorted()).toString()).apply()
        return true
    }

    fun replaceAccountWhitelist(values: List<String>) {
        val normalized = (DEFAULT_ACCOUNT_WHITELIST + values.mapNotNull { value -> value.trim().takeIf(BlockJob::isValidUsername)?.lowercase() }).toSortedSet()
        prefs.edit().putString(ACCOUNT_WHITELIST_KEY, JSONArray(normalized.toList()).toString()).apply()
    }

    fun isWhitelisted(username: String): Boolean = accountWhitelist().contains(username.trim().lowercase())

    fun displayAccount(username: String): String {
        return if (username.trim().equals("aagodofwealth", ignoreCase = true)) {
            "AAAGodofWealth"
        } else {
            username.trim()
        }
    }

    fun removeAccount(username: String): Boolean {
        val normalized = username.trim().lowercase()
        if (normalized.isBlank() || normalized in DEFAULT_ACCOUNT_WHITELIST) return false
        val current = accountWhitelist().toMutableSet()
        if (!current.remove(normalized)) return false
        prefs.edit().putString(ACCOUNT_WHITELIST_KEY, JSONArray(current.toList().sorted()).toString()).apply()
        return true
    }

    fun rulesJson(): String {
        val bundled = readAsset("content/default-rules.json")
        val cached = prefs.getString("rules_json", null) ?: return bundled
        return mergeMissingBundledRules(cached, bundled)
    }

    fun ruleStatesJson(): String = prefs.getString("rule_states", "{}") ?: "{}"

    fun setRuleEnabled(id: String, enabled: Boolean) {
        try {
            val states = JSONObject(ruleStatesJson())
            states.put(id, enabled)
            prefs.edit().putString("rule_states", states.toString()).apply()
        } catch (error: Exception) {
            Log.w(TAG, "规则状态保存失败: $id", error)
        }
    }

    fun lastXUrl(): String? = prefs.getString("last_x_url", null)

    fun setLastXUrl(url: String) {
        try {
            val parsed = URL(url)
            if (parsed.protocol == "https" && parsed.host in setOf("x.com", "twitter.com")) {
                prefs.edit().putString("last_x_url", url.take(2000)).apply()
            }
        } catch (error: Exception) {
            Log.w(TAG, "忽略无效的 X 页面地址", error)
        }
    }

    fun refreshRules(onComplete: (Boolean) -> Unit) {
        Thread {
            val success = try {
                val connection = URL("https://ban.richccy.com/api/rules").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("accept", "application/json")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (connection.responseCode in 200..299 && body.trim().startsWith("{")) {
                    val merged = mergeMissingBundledRules(body, readAsset("content/default-rules.json"))
                    prefs.edit().putString("rules_json", merged).apply()
                    true
                } else false
            } catch (error: Exception) {
                Log.w(TAG, "在线规则刷新失败，继续使用本地缓存", error)
                false
            }
            onComplete(success)
        }.start()
    }

    fun loadPopularKeywords(onComplete: (List<String>?, String?) -> Unit) {
        Thread {
            try {
                val connection = URL("https://ban.richccy.com/api/popular-terms").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("accept", "application/json")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) {
                    onComplete(null, "HTTP ${connection.responseCode}")
                    return@Thread
                }
                val ranking = JSONArray(body)
                val terms = LinkedHashSet<String>()
                for (index in 0 until ranking.length()) {
                    val term = ranking.optJSONObject(index)?.optString("term")?.trim().orEmpty()
                    if (term.isNotEmpty()) terms += term
                }
                onComplete(terms.toList(), null)
            } catch (error: Exception) {
                Log.w(TAG, "热门关键词加载失败", error)
                onComplete(null, error.message ?: "网络请求失败")
            }
        }.start()
    }

    private fun readAsset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    /**
     * A user can already have a server-cached rule configuration when an APK
     * ships a new declarative matcher. Keep the cached rules and their states,
     * but append only newly bundled rule IDs and engine metadata so a client
     * update is effective before the server JAR is redeployed.
     */
    private fun mergeMissingBundledRules(cached: String, bundled: String): String {
        return try {
            val cachedConfig = JSONObject(cached)
            val bundledConfig = JSONObject(bundled)
            val cachedRules = cachedConfig.optJSONArray("rules") ?: return bundled
            val bundledRules = bundledConfig.optJSONArray("rules") ?: return cached
            val knownIds = buildSet {
                for (index in 0 until cachedRules.length()) {
                    cachedRules.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
            var changed = false
            for (index in 0 until bundledRules.length()) {
                val rule = bundledRules.optJSONObject(index) ?: continue
                val id = rule.optString("id")
                if (id.isNotBlank() && id !in knownIds) {
                    cachedRules.put(rule)
                    changed = true
                }
            }
            if (!cachedConfig.has("engine") && bundledConfig.has("engine")) {
                cachedConfig.put("engine", bundledConfig.getJSONObject("engine"))
                changed = true
            }
            if (!cachedConfig.has("keywordSets") && bundledConfig.has("keywordSets")) {
                cachedConfig.put("keywordSets", bundledConfig.getJSONArray("keywordSets"))
                changed = true
            }
            if (!cachedConfig.has("keywordPolicies") && bundledConfig.has("keywordPolicies")) {
                cachedConfig.put("keywordPolicies", bundledConfig.getJSONArray("keywordPolicies"))
                changed = true
            }
            if ((!cachedConfig.has("accountPolicies") ||
                    cachedConfig.optJSONArray("accountPolicies")?.length() == 0) &&
                bundledConfig.has("accountPolicies")) {
                cachedConfig.put("accountPolicies", bundledConfig.getJSONArray("accountPolicies"))
                changed = true
            }
            if (!changed) return cached
            cachedConfig.put("version", maxOf(cachedConfig.optLong("version", 0), bundledConfig.optLong("version", 0)))
            cachedConfig.put("rules", cachedRules)
            cachedConfig.toString()
        } catch (error: Exception) {
            Log.w(TAG, "规则缓存解析失败，使用内置规则", error)
            bundled
        }
    }

    private fun parseStringArray(raw: String): List<String> {
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index).trim() }.filter { it.isNotEmpty() }
        } catch (error: Exception) {
            Log.w(TAG, "关键词配置解析失败，使用空配置", error)
            emptyList()
        }
    }
}
