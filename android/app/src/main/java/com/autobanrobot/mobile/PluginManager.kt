package com.autobanrobot.mobile

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val builtIn: Boolean
)

class PluginManager(private val context: Context) {
    private companion object { const val TAG = "AutoBanPluginManager" }
    private val root = File(context.filesDir, "plugins")
    private val prefs = context.getSharedPreferences("autoban_plugins", Context.MODE_PRIVATE)

    init { root.mkdirs() }

    fun list(): List<PluginInfo> {
        val result = mutableListOf(
            PluginInfo("autoban-x", "X 自动 Ban 核心", "1.0.0", true, true)
        )
        root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
            val manifest = File(directory, "plugin.json")
            if (!manifest.isFile) return@forEach
            try {
                val json = JSONObject(manifest.readText())
                val id = json.getString("id")
                result += PluginInfo(
                    id = id,
                    name = json.optString("name", id),
                    version = json.optString("version", "1.0.0"),
                    enabled = prefs.getBoolean("enabled:$id", true),
                    builtIn = false
                )
            } catch (error: Exception) {
                Log.w(TAG, "忽略无法解析的插件目录: ${directory.name}", error)
            }
        }
        return result
    }

    fun setEnabled(id: String, enabled: Boolean) {
        if (id != "autoban-x") prefs.edit().putBoolean("enabled:$id", enabled).apply()
    }

    fun install(uri: Uri): PluginInfo {
        val staging = File(context.cacheDir, "plugin-staging-${System.nanoTime()}").apply { mkdirs() }
        var total = 0L
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取插件文件" }
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/')
                        require(!entry.isDirectory && !name.startsWith("/") && !name.contains("..")) { "插件包含非法路径" }
                        require(name in setOf("plugin.json", "content.js", "styles.css", "rules.json")) { "插件文件不在允许清单内" }
                        val target = File(staging, name)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 2_000_000) { "插件大小超过 2MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
            val manifestFile = File(staging, "plugin.json")
            require(manifestFile.isFile) { "缺少 plugin.json" }
            val manifest = JSONObject(manifestFile.readText())
            val id = manifest.optString("id")
            require(Regex("[a-z0-9][a-z0-9._-]{1,63}").matches(id)) { "插件 id 无效" }
            require(manifest.optString("name").isNotBlank()) { "插件名称不能为空" }
            val allowedPermissions = setOf("read_page", "hide_content", "match_account", "request_block", "local_storage")
            val permissions = manifest.optJSONArray("permissions") ?: JSONArray()
            for (index in 0 until permissions.length()) {
                require(permissions.optString(index) in allowedPermissions) { "插件申请了不支持的权限" }
            }
            val destination = File(root, id)
            destination.deleteRecursively()
            staging.renameTo(destination)
            prefs.edit().putBoolean("enabled:$id", true).apply()
            return PluginInfo(id, manifest.getString("name"), manifest.optString("version", "1.0.0"), true, false)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun scriptBundle(): String {
        return root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.mapNotNull { directory ->
            val manifestFile = File(directory, "plugin.json")
            val scriptFile = File(directory, "content.js")
            if (!manifestFile.isFile || !scriptFile.isFile) return@mapNotNull null
            try {
                val manifest = JSONObject(manifestFile.readText())
                val id = manifest.getString("id")
                if (!prefs.getBoolean("enabled:$id", true)) return@mapNotNull null
                "/* plugin:$id */\ntry {\n${scriptFile.readText()}\n} catch (error) { console.warn('AutoBan plugin failed', error); }"
            } catch (error: Exception) {
                Log.w(TAG, "忽略无法加载的插件脚本: ${directory.name}", error)
                null
            }
        }?.joinToString("\n") ?: ""
    }

    fun styleBundle(): String {
        return root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.mapNotNull { directory ->
            val manifestFile = File(directory, "plugin.json")
            val styleFile = File(directory, "styles.css")
            if (!manifestFile.isFile || !styleFile.isFile) return@mapNotNull null
            try {
                val id = JSONObject(manifestFile.readText()).getString("id")
                if (!prefs.getBoolean("enabled:$id", true)) return@mapNotNull null
                "/* plugin:$id */\n${styleFile.readText()}"
            } catch (error: Exception) {
                Log.w(TAG, "忽略无法加载的插件样式: ${directory.name}", error)
                null
            }
        }?.joinToString("\n") ?: ""
    }

    fun mergeRules(baseJson: String): String {
        return try {
            val config = JSONObject(baseJson)
            val merged = JSONArray()
            val baseRules = config.optJSONArray("rules") ?: JSONArray()
            for (index in 0 until baseRules.length()) merged.put(baseRules.opt(index))
            root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
                val manifestFile = File(directory, "plugin.json")
                val rulesFile = File(directory, "rules.json")
                if (!manifestFile.isFile || !rulesFile.isFile) return@forEach
                val manifest = JSONObject(manifestFile.readText())
                val id = manifest.getString("id")
                if (!prefs.getBoolean("enabled:$id", true)) return@forEach
                val pluginRules = JSONArray(rulesFile.readText())
                require(pluginRules.length() <= 100) { "插件规则数量超过 100 条" }
                for (index in 0 until pluginRules.length()) merged.put(pluginRules.opt(index))
            }
            config.put("rules", merged)
            config.toString()
        } catch (error: Exception) {
            Log.w(TAG, "插件规则合并失败，继续使用主规则", error)
            baseJson
        }
    }
}
