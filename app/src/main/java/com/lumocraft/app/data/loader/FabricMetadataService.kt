package com.lumocraft.app.data.loader

import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.loader.LoaderVersion
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One client library of a Fabric profile as published by the meta
 * service: maven coordinates, maven base URL and verification data.
 */
data class FabricLibrary(
    val name: String,
    val url: String,
    val sha1: String?,
    val size: Long?
)

/**
 * The resolved Fabric launch profile for one (Minecraft, Loader) pair:
 * the client libraries, the client main class and the loader game
 * arguments. Parsed from the Fabric meta `launcherMeta` block.
 */
data class FabricProfile(
    val loaderMaven: String,
    val intermediaryMaven: String,
    val loaderVersion: String,
    val minecraftVersion: String,
    val mainClass: String,
    val libraries: List<FabricLibrary>,
    val gameArguments: List<String>
) {
    /** Deterministic version id / directory name of this instance. */
    val instanceId: String get() = "fabric-loader-$loaderVersion-$minecraftVersion"
}

/**
 * Talks to the official Fabric metadata service
 * (https://meta.fabricmc.net).
 *
 * Two endpoints are used:
 *  - `versions/loader/<mc>` lists every loader version published for a
 *    Minecraft version (used for version pairing / compatibility);
 *  - `versions/loader/<mc>/<loader>` returns the full launch profile.
 *
 * Responses are cached on disk under `loader/fabric/cache/` with a TTL
 * so repeated screen visits and repairs do not re-download metadata.
 */
class FabricMetadataService(
    private val client: HttpClient,
    private val storage: StorageManager,
    private val cacheTtlMs: Long = METADATA_CACHE_TTL_MS,
) : LoaderMetadataSource {

    override val type: LoaderType = LoaderType.FABRIC

    override suspend fun loaderVersions(minecraftVersion: String): Result<List<LoaderVersion>> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(storage.loaderCacheDirectory(LoaderType.FABRIC), "$minecraftVersion.json")
            runCatching {
                val body = cachedOrFetch(cacheFile, versionsUrl(minecraftVersion))
                parseVersions(body)
            }
        }

    suspend fun profile(minecraftVersion: String, loaderVersion: String): Result<FabricProfile> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(
                storage.loaderCacheDirectory(LoaderType.FABRIC),
                "${minecraftVersion}_$loaderVersion.json"
            )
            runCatching {
                val body = cachedOrFetch(cacheFile, profileUrl(minecraftVersion, loaderVersion))
                parseProfile(body, minecraftVersion, loaderVersion)
            }
        }

    private suspend fun cachedOrFetch(cacheFile: File, url: String): String {
        if (cacheFile.isFile && isFresh(cacheFile)) {
            return cacheFile.readText()
        }
        val body = client.get(url).getOrElse { throw it }
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(body)
        return body
    }

    private fun isFresh(file: File): Boolean =
        System.currentTimeMillis() - file.lastModified() < cacheTtlMs

    private fun parseVersions(body: String): List<LoaderVersion> {
        val array = JSONArray(body)
        val result = mutableListOf<LoaderVersion>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val loader = entry.optJSONObject("loader") ?: continue
            val version = loader.optString("version")
            if (version.isEmpty()) continue
            result += LoaderVersion(
                loaderVersion = version,
                intermediaryVersion = entry.optJSONObject("intermediary")
                    ?.optString("version")
                    ?.takeIf { it.isNotEmpty() }
                    ?: "",
                stable = loader.optBoolean("stable", true),
                maven = loader.optString("maven")
            )
        }
        return result
    }

    private fun parseProfile(
        body: String,
        minecraftVersion: String,
        loaderVersion: String,
    ): FabricProfile {
        val root = JSONObject(body)
        val launcherMeta = root.optJSONObject("launcherMeta")
            ?: throw IOException("Fabric meta response has no launcherMeta")

        val mainClass = launcherMeta.optJSONObject("mainClass")
            ?.optString("client")
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_MAIN_CLASS

        val libraries = buildList {
            val client = launcherMeta.optJSONObject("libraries")
                ?.optJSONArray("client")
                ?: JSONArray()
            for (i in 0 until client.length()) {
                val entry = client.optJSONObject(i) ?: continue
                val name = entry.optString("name")
                if (name.isEmpty()) continue
                val url = entry.optString("url")
                    .takeIf { it.isNotEmpty() }
                    ?: DEFAULT_MAVEN_URL
                add(
                    FabricLibrary(
                        name = name,
                        url = url,
                        sha1 = entry.optString("sha1").takeIf { it.isNotEmpty() },
                        size = entry.optLong("size", -1L).takeIf { it >= 0 }
                    )
                )
            }
        }

        val gameArguments = buildList {
            val game = launcherMeta.optJSONObject("arguments")
                ?.optJSONArray("game")
                ?: JSONArray()
            for (i in 0 until game.length()) {
                val value = game.opt(i)
                if (value is String) add(value)
            }
        }

        return FabricProfile(
            loaderMaven = root.optJSONObject("loader")?.optString("maven").orEmpty(),
            intermediaryMaven = root.optJSONObject("intermediary")?.optString("maven").orEmpty(),
            loaderVersion = loaderVersion,
            minecraftVersion = minecraftVersion,
            mainClass = mainClass,
            libraries = libraries,
            gameArguments = gameArguments
        )
    }

    private fun versionsUrl(minecraftVersion: String): String =
        "$META_BASE_URL/v2/versions/loader/$minecraftVersion"

    private fun profileUrl(minecraftVersion: String, loaderVersion: String): String =
        "$META_BASE_URL/v2/versions/loader/$minecraftVersion/$loaderVersion"

    private companion object {
        const val META_BASE_URL = "https://meta.fabricmc.net"
        const val DEFAULT_MAVEN_URL = "https://maven.fabricmc.net/"
        const val DEFAULT_MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient"
        const val METADATA_CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}