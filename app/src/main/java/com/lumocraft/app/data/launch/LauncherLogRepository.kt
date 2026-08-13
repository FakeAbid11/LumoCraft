package com.lumocraft.app.data.launch

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.loader.LoaderLaunchConfiguration
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.native.RendererProfile
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Session logs under <launcherRoot>/logs/. Every launch writes a
 * timestamped file (launcher-<ts>.log) that also feeds a replaying live
 * stream for the console UI. Line writes are buffered and flushed per
 * line so the game output appears in real time.
 */
class LauncherLogRepository(private val storage: StorageManager) {

    private val _lines = MutableSharedFlow<String>(
        replay = REPLAY_LINES,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val lines: Flow<String> = _lines.asSharedFlow()

    private val recent = ArrayDeque<String>()
    private var sessionFile: File? = null
    private var writer: BufferedWriter? = null

    fun logsDirectory(): File = storage.logsDirectory()

    /** Opens a new timestamped session file and returns it. */
    fun startSession(prefix: String = SESSION_PREFIX): File {
        endSession()
        val file = File(logsDirectory().apply { mkdirs() }, "$prefix-${timestamp()}.log")
        sessionFile = file
        writer = file.bufferedWriter(Charsets.UTF_8, BUFFER_SIZE)
        return file
    }

    /** Appends one line to the session file and the live stream. */
    suspend fun writeLine(line: String) {
        withContext(Dispatchers.IO) {
            writer?.let {
                it.append(line)
                it.newLine()
                it.flush()
            }
        }
        recent.addLast(line)
        while (recent.size > MAX_RECENT) recent.removeFirst()
        _lines.emit(line)
    }

    /** Writes a section header (e.g. "── Native libraries ──"). */
    suspend fun writeSection(title: String) {
        writeLine("── $title ──")
    }

    /** Logs the native extraction outcome. */
    suspend fun logNativeExtraction(
        versionId: String,
        arch: String,
        directory: String,
        extracted: Int,
        skippedJars: Int,
        duplicatesRemoved: Int,
    ) {
        writeSection("Native libraries")
        writeLine("version=$versionId arch=$arch directory=$directory")
        writeLine("extracted=$extracted files, cached jars=$skippedJars, duplicates removed=$duplicatesRemoved")
    }

    /** Logs the renderer profile in use. */
    suspend fun logRendererSelection(profile: RendererProfile) {
        writeSection("Renderer")
        writeLine(
            "profile=${profile.renderer.name.lowercase()} " +
                "resolutionScale=${profile.resolutionScale.percent}% " +
                "fpsLimit=${profile.fpsLimit ?: "unlimited"} vsync=${profile.vsync} " +
                "mipmaps=${profile.mipmaps}"
        )
    }

    /** Logs the JNI paths injected into the Java process. */
    suspend fun logJniPaths(paths: Map<String, String>) {
        writeSection("JNI environment")
        for ((key, value) in paths) {
            writeLine("$key=$value")
        }
    }

    /** Logs the device architecture. */
    suspend fun logArchitecture(arch: String) {
        writeLine("Architecture: $arch")
    }

/** Logs the effective game resolution. */
    suspend fun logResolution(width: Int, height: Int, scalePercent: Int) {
        writeSection("Resolution")
        writeLine("window=${width}x${height} (scale $scalePercent%)")
    }

    /** Logs the input configuration a game session starts with. */
    suspend fun logInputConfiguration(config: com.lumocraft.app.domain.input.InputConfiguration) {
        writeSection("Input configuration")
        writeLine(
            "profile=${config.profileName} (${config.profileId}) " +
                "sensitivity=${config.sensitivity} invertY=${config.invertY} mouseMode=${config.mouseMode}"
        )
        writeLine(
            "cursorSpeed=${config.cursorSpeed} buttonOpacity=${config.buttonOpacity} " +
                "controls=${config.controlCount} controller=${config.controllerEnabled} keyboard=${config.keyboardEnabled}"
        )
    }

    /** Logs controller connection (detection). */
    suspend fun logControllerDetected(deviceName: String?) {
        writeLine("Controller detected: ${deviceName ?: "unknown device"}")
    }

    /** Logs controller disconnection. */
    suspend fun logControllerDisconnected() {
        writeLine("Controller disconnected")
    }

    /** Logs hardware keyboard connection. */
    suspend fun logKeyboardConnected() {
        writeLine("Hardware keyboard connected")
    }

    /** Logs hardware keyboard disconnection. */
    suspend fun logKeyboardDisconnected() {
        writeLine("Hardware keyboard disconnected")
    }

    /** Logs an input profile load. */
    suspend fun logProfileLoaded(profileId: String, name: String, controlCount: Int) {
        writeLine("Input profile loaded: $name ($profileId) — $controlCount controls")
    }

    /** Logs a control layout load. */
    suspend fun logLayoutLoaded(profileId: String, controlCount: Int) {
        writeLine("Control layout loaded for profile $profileId — $controlCount buttons")
    }

    /** Logs the device profile that drives all optimization decisions. */
    suspend fun logMemoryProfile(profile: com.lumocraft.app.domain.performance.DeviceProfile) {
        writeSection("Performance")
        writeLine(
            "Device: ${profile.tier.name.lowercase()} tier " +
                "(${profile.totalRamMB} MB RAM, ${profile.cpuCores} cores, " +
                "Android ${profile.androidRelease} (SDK ${profile.androidSdk}), " +
                "low-ram=${profile.lowRamDevice}, ${profile.architecture.abi})"
        )
        writeLine("Recommended RAM: ${profile.recommendedMaxRamMB()} MB")
    }

    /** Logs the JVM profile selection (automatic or overridden). */
    suspend fun logJvmProfileSelection(profile: com.lumocraft.app.domain.performance.JvmProfile, automatic: Boolean) {
        writeLine(
            "JVM profile: ${profile.displayName} " +
                "(${if (automatic) "automatic" else "manual override"})"
        )
    }

    /** Logs a cache hit/miss for one launch cache kind. */
    suspend fun logCacheEvent(kind: String, hit: Boolean, detail: String) {
        writeLine("Cache ${if (hit) "hit" else "miss"}: $kind — $detail")
    }

    /** Logs the launch phase timings once per session. */
    suspend fun logLaunchTiming(
        validationMs: Long,
        classpathMs: Long,
        jvmStartMs: Long,
        totalMs: Long,
        cacheHits: Int,
        cacheMisses: Int,
        cachedValidation: Boolean,
    ) {
        writeLine(
            "Launch timing: validation=${validationMs} ms" +
                "${if (cachedValidation) " (cached)" else ""}, " +
                "classpath=${classpathMs} ms, jvm start=${jvmStartMs} ms, " +
                "total=${totalMs} ms (cache hits=$cacheHits, misses=$cacheMisses)"
        )
    }

    /** Logs an optimization decision (e.g. adaptive concurrency changes). */
    suspend fun logOptimizationDecision(message: String) {
        writeLine("Optimization: $message")
    }

    /** Logs a loader installation. */
    suspend fun logLoaderInstallation(
        type: LoaderType,
        minecraftVersion: String,
        loaderVersion: String,
        instanceId: String,
    ) {
        writeSection("Loader installation")
        writeLine(
            "loader=${type.id} minecraft=$minecraftVersion " +
                "loaderVersion=$loaderVersion instance=$instanceId"
        )
    }

    /** Logs a loader repair (instance, redownloaded files, outcome). */
    suspend fun logLoaderRepair(instanceId: String, filesRedownloaded: Int, ok: Boolean) {
        writeSection("Loader repair")
        writeLine("instance=$instanceId filesRedownloaded=$filesRedownloaded ok=$ok")
    }

    /** Logs a loader removal. */
    suspend fun logLoaderRemoval(type: LoaderType, instanceId: String) {
        writeSection("Loader removal")
        writeLine("loader=${type.id} instance=$instanceId")
    }

    /** Logs a compatibility decision (loader version vs Minecraft version). */
    suspend fun logLoaderCompatibility(
        type: LoaderType,
        minecraftVersion: String,
        loaderVersions: Int,
        compatible: Boolean,
    ) {
        writeLine(
            "Loader compatibility: ${type.id} for $minecraftVersion " +
                "($loaderVersions published, compatible=$compatible)"
        )
    }

    /** Logs the loader configuration applied to a game session. */
    suspend fun logLoaderConfiguration(config: LoaderLaunchConfiguration) {
        if (config.type == LoaderType.VANILLA) return
        writeSection("Loader launch configuration")
        writeLine(
            "loader=${config.type.id} mainClass=${config.mainClass} " +
                "libraries=${config.libraries.size} clientJar=${config.clientJar?.name} " +
                "jvmArgs=${config.jvmArguments.size} gameArgs=${config.gameArguments.size}"
        )
    }

    /** Closes the session file; the file stays on disk. */
    fun endSession() {
        writer?.close()
        writer = null
    }

    fun currentSessionFile(): File? = sessionFile

    fun recentLines(): List<String> = recent.toList()

    fun listLogFiles(): List<File> =
        logsDirectory().listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Copies every session log into [destination] (file or directory). */
    suspend fun copyLogs(destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            for (file in listLogFiles()) {
                val target = if (destination.isDirectory) {
                    File(destination, file.name)
                } else {
                    destination
                }
                file.copyTo(target, overwrite = true)
            }
        }
    }

    /** Deletes all session logs. */
    suspend fun clearLogs(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { listLogFiles().forEach { it.delete() } }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private companion object {
        const val SESSION_PREFIX = "launcher"
        const val BUFFER_SIZE = 16 * 1024
        const val REPLAY_LINES = 1000
        const val EXTRA_BUFFER = 256
        const val MAX_RECENT = 500
    }
}