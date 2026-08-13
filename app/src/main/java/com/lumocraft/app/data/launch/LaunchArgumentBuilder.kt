package com.lumocraft.app.data.launch

import android.os.Build
import com.lumocraft.app.BuildConfig
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.launch.OfflineUuid
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Final JVM and game argument lists for one launch. */
data class LaunchArguments(
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val mainClass: String
)

/**
 * Builds the JVM and game argument lists for a version JSON, resolving
 * every `${token}` placeholder against the [LaunchContext] and the
 * Android environment. New-format `arguments.*` arrays and legacy
 * `minecraftArguments` strings are both supported; the Android-specific
 * JVM flags mirror what the reference Android launchers pass.
 */
class LaunchArgumentBuilder(private val storage: StorageManager) {

    suspend fun build(
        context: LaunchContext,
        classpath: String,
        environment: LaunchEnvironment,
    ): Result<LaunchArguments> = withContext(Dispatchers.IO) {
        runCatching {
            val json = readVersionJson(context.versionId)
            val nativesDirectory = environment.nativesDirectory(context.versionId)
            val tokens = tokenMap(context, json, classpath, nativesDirectory)

            val jvmArguments = buildList {
                addAll(VersionJson.resolveArguments(json.optJSONObject("arguments")?.optJSONArray("jvm")))
                json.optJSONObject("logging")
                    ?.optJSONObject("client")
                    ?.optString("argument")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add(resolveTokens(it, tokens)) }
                addAll(context.jvmConfiguration.buildArguments())
                addAll(androidArguments(environment, context.versionId))
            }.map { resolveTokens(it, tokens) }

            val gameArguments = buildList {
                val args = json.optJSONObject("arguments")?.optJSONArray("game")
                if (args != null) {
                    addAll(VersionJson.resolveArguments(args))
                } else {
                    json.optString("minecraftArguments")
                        .takeIf { it.isNotEmpty() }
                        ?.let { legacy -> addAll(tokenizeLegacy(legacy)) }
                }
            }.map { resolveTokens(it, tokens) }

            val mainClass = json.optString("mainClass").takeIf { it.isNotEmpty() }
                ?: throw LaunchException("No mainClass declared for '${context.versionId}'")

            LaunchArguments(
                jvmArguments = jvmArguments,
                gameArguments = gameArguments,
                mainClass = mainClass
            )
        }
    }

    private fun readVersionJson(versionId: String): JSONObject {
        val file = storage.versionJsonFile(versionId)
        return runCatching { JSONObject(file.readText()) }.getOrNull()
            ?: throw LaunchException("Version JSON missing for '$versionId'")
    }

    private fun tokenMap(
        context: LaunchContext,
        json: JSONObject,
        classpath: String,
        nativesDirectory: File,
    ): Map<String, String> {
        val assetIndexId = json.optJSONObject("assetIndex")?.optString("id")
            ?.takeIf { it.isNotEmpty() }
            ?: json.optString("assets")
        val loggingId = json.optJSONObject("logging")
            ?.optJSONObject("client")
            ?.optJSONObject("file")
            ?.optString("id")
            .orEmpty()
        return mapOf(
            "auth_player_name" to context.account.username,
            "auth_uuid" to OfflineUuid.forUsername(context.account.username),
            "auth_access_token" to ACCESS_TOKEN_PLACEHOLDER,
            "auth_xuid" to "",
            "clientid" to "client",
            "user_type" to "legacy",
            "version_name" to context.versionId,
            "version_type" to json.optString("type", "release"),
            "assets_root" to storage.assetsDirectory().absolutePath,
            "assets_index_name" to assetIndexId,
            "game_assets" to File(context.gameDirectory, "resources").absolutePath,
            "game_directory" to context.gameDirectory.absolutePath,
            "natives_directory" to nativesDirectory.absolutePath,
            "library_directory" to storage.librariesDirectory().absolutePath,
            "launcher_name" to LAUNCHER_NAME,
            "launcher_version" to BuildConfig.VERSION_NAME,
            "classpath" to classpath,
            "path" to storage.loggingConfigFile(context.versionId, loggingId).absolutePath,
            "resolution_width" to DEFAULT_RESOLUTION_WIDTH.toString(),
            "resolution_height" to DEFAULT_RESOLUTION_HEIGHT.toString()
        )
    }

    private fun resolveTokens(value: String, tokens: Map<String, String>): String {
        if (!value.contains('$')) return value
        var result = value
        for ((key, replacement) in tokens) {
            result = result.replace("\${$key}", replacement)
        }
        return result
    }

    /** Legacy space-separated argument strings, quote-aware. */
    private fun tokenizeLegacy(raw: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in raw) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        result += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    /**
     * Android-specific JVM flags: the game runs as a Linux JVM inside the
     * launcher's storage, so HOME/tmpdir/library paths are redirected and
     * LWJGL uses its system allocator instead of the jemalloc bindings.
     */
    private fun androidArguments(environment: LaunchEnvironment, versionId: String): List<String> =
        listOf(
            "-Duser.home=${environment.homeDirectory().absolutePath}",
            "-Djava.io.tmpdir=${environment.tempDirectory().absolutePath}",
            "-Djna.boot.library.path=${environment.nativesDirectory(versionId).absolutePath}",
            "-Duser.language=${Locale.getDefault().language}",
            "-Dos.name=Linux",
            "-Dos.version=Android-${Build.VERSION.RELEASE}",
            "-Duser.timezone=${TimeZone.getDefault().id}",
            "-Dorg.lwjgl.system.allocator=system",
            "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
            "-Dlog4j2.formatMsgNoLookups=true",
            "-Djdk.lang.Process.launchMechanism=FORK",
            "-XX:ActiveProcessorCount=${Runtime.getRuntime().availableProcessors()}"
        )

    private companion object {
        const val LAUNCHER_NAME = "LumoCraft"
        const val ACCESS_TOKEN_PLACEHOLDER = "0"
        const val DEFAULT_RESOLUTION_WIDTH = 854
        const val DEFAULT_RESOLUTION_HEIGHT = 480
    }
}