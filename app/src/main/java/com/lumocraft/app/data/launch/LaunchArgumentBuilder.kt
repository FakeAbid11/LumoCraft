package com.lumocraft.app.data.launch

import android.os.Build
import com.lumocraft.app.BuildConfig
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.launch.OfflineUuid
import com.lumocraft.app.domain.native.RendererProfile
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
 * JVM flags mirror what the reference Android launchers pass. The JNI
 * environment (java.library.path, org.lwjgl.librarypath), the renderer
 * profile flags and the scaled resolution are injected on top without
 * touching the caller-visible pipeline API.
 */
class LaunchArgumentBuilder(private val storage: StorageManager) {

    suspend fun build(
        context: LaunchContext,
        classpath: String,
        environment: LaunchEnvironment,
        nativesDirectory: File,
        jniEnvironment: Map<String, String> = emptyMap(),
        rendererProfile: RendererProfile? = null,
    ): Result<LaunchArguments> = withContext(Dispatchers.IO) {
        runCatching {
            val chain = loadChain(context.versionId)
                ?: throw LaunchException("Version JSON missing for '${context.versionId}'")
            val tokens = tokenMap(context, chain, classpath, nativesDirectory, rendererProfile)

            val jvmArguments = buildList {
                for ((_, json) in chain) {
                    val resolved = VersionJson.resolveArguments(
                        json.optJSONObject("arguments")?.optJSONArray("jvm")
                    )
                    addAll(resolved.filter { it !in this })
                }
                chain.firstNotNullOfOrNull { (_, json) ->
                    json.optJSONObject("logging")
                        ?.optJSONObject("client")
                        ?.optString("argument")
                        ?.takeIf { it.isNotEmpty() }
                }?.let { add(it) }
                addAll(context.jvmConfiguration.buildArguments())
                addAll(androidArguments(environment, nativesDirectory))
                jniEnvironment.forEach { (key, value) -> add("-D$key=$value") }
                rendererProfile?.let { addAll(rendererArguments(it)) }
            }.map { resolveTokens(it, tokens) }

            val gameArguments = buildList {
                for ((_, json) in chain) {
                    val args = json.optJSONObject("arguments")?.optJSONArray("game")
                    if (args != null) {
                        addAll(VersionJson.resolveArguments(args).filter { it !in this })
                    } else {
                        json.optString("minecraftArguments")
                            .takeIf { it.isNotEmpty() }
                            ?.let { legacy -> addAll(tokenizeLegacy(legacy).filter { it !in this }) }
                    }
                }
            }.map { resolveTokens(it, tokens) }

            val mainClass = chain.firstNotNullOfOrNull { (_, json) ->
                json.optString("mainClass").takeIf { it.isNotEmpty() }
            } ?: throw LaunchException("No mainClass declared for '${context.versionId}'")

            LaunchArguments(
                jvmArguments = jvmArguments,
                gameArguments = gameArguments,
                mainClass = mainClass
            )
        }
    }

    /**
     * Leaf-first inheritsFrom chain of (version id, parsed JSON), or null.
     * Ids let tokens reference the file layout of the version that actually
     * declared each resource (e.g. the logging config, which is stored under
     * the base version's directory for inherited loader profiles).
     */
    private fun loadChain(versionId: String): List<Pair<String, JSONObject>>? {
        val result = mutableListOf<Pair<String, JSONObject>>()
        var current = versionId
        val seen = mutableSetOf<String>()
        while (true) {
            if (!seen.add(current)) return null
            val file = storage.versionJsonFile(current)
            if (!file.isFile) return null
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
            result.add(current to json)
            val parent = json.optString("inheritsFrom").takeIf { it.isNotEmpty() } ?: break
            current = parent
        }
        return result
    }

    private fun tokenMap(
        context: LaunchContext,
        chain: List<Pair<String, JSONObject>>,
        classpath: String,
        nativesDirectory: File,
        rendererProfile: RendererProfile?,
    ): Map<String, String> {
        val assetIndexId = chain.firstNotNullOfOrNull { (_, json) ->
            json.optJSONObject("assetIndex")?.optString("id")?.takeIf { it.isNotEmpty() }
        } ?: chain.firstNotNullOfOrNull { (_, json) ->
            json.optString("assets").takeIf { it.isNotEmpty() }
        } ?: ""
        val logging = chain.firstNotNullOfOrNull { (versionId, json) ->
            json.optJSONObject("logging")
                ?.optJSONObject("client")
                ?.optJSONObject("file")
                ?.optString("id")
                ?.takeIf { it.isNotEmpty() }
                ?.let { id -> versionId to id }
        }
        val loggingId = logging?.second.orEmpty()
        // The logging file is stored under the version that declared it, so
        // inherited loader profiles resolve the path against the base version.
        val loggingVersionId = logging?.first ?: context.versionId
        val versionType = chain.firstNotNullOfOrNull { (_, json) ->
            json.optString("type").takeIf { it.isNotEmpty() }
        } ?: "release"
        val resolution = rendererProfile
            ?.effectiveResolution()
            ?: RendererProfile.DEFAULT_WINDOW
        return mapOf(
            "auth_player_name" to context.account.username,
            "auth_uuid" to OfflineUuid.forUsername(context.account.username),
            "auth_access_token" to ACCESS_TOKEN_PLACEHOLDER,
            "auth_xuid" to "",
            "clientid" to "client",
            "user_type" to "legacy",
            "version_name" to context.versionId,
            "version_type" to versionType,
            "assets_root" to storage.assetsDirectory().absolutePath,
            "assets_index_name" to assetIndexId,
            "game_assets" to File(context.gameDirectory, "resources").absolutePath,
            "game_directory" to context.gameDirectory.absolutePath,
            "natives_directory" to nativesDirectory.absolutePath,
            "library_directory" to storage.librariesDirectory().absolutePath,
            "launcher_name" to LAUNCHER_NAME,
            "launcher_version" to BuildConfig.VERSION_NAME,
            "classpath" to classpath,
            "path" to storage.loggingConfigFile(loggingVersionId, loggingId).absolutePath,
            "resolution_width" to resolution.width.toString(),
            "resolution_height" to resolution.height.toString()
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
    private fun androidArguments(
        environment: LaunchEnvironment,
        nativesDirectory: File,
    ): List<String> =
        listOf(
            "-Duser.home=${environment.homeDirectory().absolutePath}",
            "-Djava.io.tmpdir=${environment.tempDirectory().absolutePath}",
            "-Djna.boot.library.path=${nativesDirectory.absolutePath}",
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

    /**
     * Launcher-consumed renderer flags for the renderer glue (a later
     * phase). Harmless to the vanilla JVM; they follow the process and
     * let the glue configure the window without touching the launcher.
     */
    private fun rendererArguments(profile: RendererProfile): List<String> =
        listOf(
            "-Dlumocraft.renderer=${profile.renderer.name.lowercase()}",
            "-Dlumocraft.resolutionScale=${profile.resolutionScale.percent}",
            "-Dlumocraft.fpsLimit=${profile.fpsLimit ?: "unlimited"}",
            "-Dlumocraft.vsync=${profile.vsync}",
            "-Dlumocraft.mipmaps=${profile.mipmaps}"
        )

    private companion object {
        const val LAUNCHER_NAME = "LumoCraft"
        const val ACCESS_TOKEN_PLACEHOLDER = "0"
    }
}