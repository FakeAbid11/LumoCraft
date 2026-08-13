package com.lumocraft.app.data.version

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.domain.version.AssetIndexRef
import com.lumocraft.app.domain.version.LibraryRef
import com.lumocraft.app.domain.version.LoggingConfigRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the fields of a downloaded version JSON that the installer needs:
 * libraries (with rules + natives classifiers resolved for this device),
 * the asset index reference and the logging configuration reference.
 */
object VersionJson {

    private val osName: String = run {
        val name = System.getProperty("os.name").lowercase()
        when {
            name.contains("win") -> "windows"
            name.contains("mac") -> "osx"
            else -> "linux"
        }
    }

    private val osArch: String = run {
        val arch = System.getProperty("os.arch").lowercase()
        when {
            arch.contains("arm64") || arch.contains("aarch64") -> "arm64"
            arch.contains("amd64") || arch.contains("x86_64") -> "amd64"
            arch.contains("arm") -> "arm"
            arch.contains("86") -> "x86"
            else -> "unknown"
        }
    }

    /** The libraries this device needs, with rules and natives applied. */
    fun libraries(json: JSONObject): List<LibraryRef> {
        val result = mutableListOf<LibraryRef>()
        val array = json.optJSONArray("libraries") ?: return result
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            if (!rulesAllow(entry.optJSONArray("rules"))) continue

            val downloads = entry.optJSONObject("downloads")
            var artifact = downloads?.optJSONObject("artifact")
            entry.optJSONObject("natives")?.optString(osName)?.let { classifier ->
                artifact = downloads?.optJSONObject("classifiers")?.optJSONObject(classifier)
            }

            val path = artifact?.optString("path")?.takeIf { it.isNotEmpty() }
                ?: entry.optString("path").takeIf { it.isNotEmpty() }
                ?: continue
            val url = artifact?.optString("url")?.takeIf { it.isNotEmpty() }
                ?: AppConfig.LIBRARIES_BASE_URL + path

            result += LibraryRef(
                path = path,
                sha1 = artifact?.optString("sha1")?.takeIf { it.isNotEmpty() },
                size = artifact?.optLong("size", -1L)?.takeIf { it >= 0 },
                url = url
            )
        }
        return result
    }

    /** Asset index reference, or null when the version has no assets. */
    fun assetIndex(json: JSONObject): AssetIndexRef? =
        json.optJSONObject("assetIndex")?.let { obj ->
            val id = obj.optString("id")
            if (id.isEmpty()) null
            else AssetIndexRef(
                id = id,
                url = obj.optString("url"),
                sha1 = sha1Of(obj),
                size = sizeOf(obj)
            )
        }

    /** Logging configuration reference, or null when not present. */
    fun loggingConfig(json: JSONObject): LoggingConfigRef? =
        json.optJSONObject("logging")
            ?.optJSONObject("client")
            ?.optJSONObject("file")
            ?.let { obj ->
                val id = obj.optString("id")
                if (id.isEmpty()) null
                else LoggingConfigRef(
                    id = id,
                    url = obj.optString("url"),
                    sha1 = sha1Of(obj),
                    size = sizeOf(obj)
                )
            }

    /**
     * Rule evaluation per Mojang's semantics: iterate rules, apply only those
     * whose OS matches this device; the last applied action wins.
     * Feature-based rules are ignored.
     */
    private fun rulesAllow(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (rule.has("features")) continue
            if (!osApplies(rule.optJSONObject("os"))) continue
            allowed = rule.optString("action", "allow") == "allow"
        }
        return allowed
    }

    private fun osApplies(os: JSONObject?): Boolean {
        if (os == null) return true
        val name = os.optString("name").lowercase()
        if (name.isNotEmpty() && name != osName) return false
        val arch = os.optString("arch").lowercase()
        return arch.isEmpty() || arch == osArch
    }

    private fun sha1Of(obj: JSONObject): String? =
        obj.optString("sha1").takeIf { it.isNotEmpty() }

    private fun sizeOf(obj: JSONObject): Long? =
        obj.optLong("size", -1L).takeIf { it >= 0 }
}