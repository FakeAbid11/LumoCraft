package com.lumocraft.app.data.version

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.domain.version.MinecraftVersion
import com.lumocraft.app.domain.version.VersionManifest
import com.lumocraft.app.domain.version.VersionType
import java.time.Instant
import org.json.JSONObject

/**
 * Downloads and parses Mojang's official version manifest.
 * Parsing is isolated here so a future endpoint/mirror swap only touches
 * this class (or the URL in [AppConfig]).
 */
class ManifestService(private val client: HttpClient) {

    suspend fun fetchManifest(): Result<VersionManifest> {
        val body = client.get(AppConfig.MANIFEST_URL)
            .getOrElse { return Result.failure(it) }
        return runCatching { parse(body) }
    }

    private fun parse(text: String): VersionManifest {
        val root = JSONObject(text)
        val latest = root.optJSONObject("latest")

        val versions = buildList {
            val array = root.optJSONArray("versions") ?: return@buildList
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (id.isEmpty()) continue
                add(
                    MinecraftVersion(
                        id = id,
                        type = VersionType.fromManifestType(entry.optString("type")),
                        url = entry.optString("url"),
                        releaseTime = parseInstant(entry.optString("releaseTime")),
                        time = parseInstant(entry.optString("time")),
                        sha1 = entry.optString("sha1").takeIf { it.isNotEmpty() },
                        size = entry.optLong("size", -1L).takeIf { it >= 0 }
                    )
                )
            }
        }

        return VersionManifest(
            latestRelease = latest?.optString("release"),
            latestSnapshot = latest?.optString("snapshot"),
            versions = versions
        )
    }

    private fun parseInstant(iso: String?): Long = iso?.let { value ->
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    } ?: 0L
}