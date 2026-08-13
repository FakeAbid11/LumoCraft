package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.launch.LaunchFailure
import com.lumocraft.app.domain.launch.LaunchPipeline
import com.lumocraft.app.domain.launch.LaunchProgress
import com.lumocraft.app.domain.launch.LaunchState
import com.lumocraft.app.domain.launch.LaunchValidationReport
import com.lumocraft.app.domain.loader.LoaderLaunchConfigurator
import com.lumocraft.app.domain.loader.LoaderLaunchConfiguration
import com.lumocraft.app.domain.native.NativeException
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.performance.LaunchCacheEntry
import com.lumocraft.app.domain.performance.PerformanceManager
import com.lumocraft.app.domain.performance.LaunchTimings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reference [LaunchPipeline]: validates, prepares (including native
 * libraries through [NativeRuntimeManager]), classpaths, builds
 * arguments with the JNI environment and renderer profile injected,
 * spawns the Java process and streams its output into the session log.
 * Any terminal state leaves the session log file on disk for the
 * "Open logs" action.
 *
 * Phase 9: validation is cached through [PerformanceManager.verifier],
 * classpath and launch arguments come from the launch cache when their
 * fingerprints match, every phase is measured by the launch profiler and
 * the memory optimizer pool is released after the session.
 */
class DefaultLaunchPipeline(
    private val environment: LaunchEnvironment,
    private val validator: LaunchValidator,
    private val classpathBuilder: ClasspathBuilder,
    private val argumentBuilder: LaunchArgumentBuilder,
    private val clientJarManager: ClientJarManager,
    private val nativeRuntimeManager: NativeRuntimeManager,
    private val launcher: JavaLauncher,
    private val crashAnalyzer: CrashAnalyzer,
    private val logs: LauncherLogRepository,
    private val performance: PerformanceManager,
    /**
     * Generic loader integration: the pipeline asks
     * `loader.configureLaunch(context)` without knowing which loader is
     * active. Vanilla launches receive an all-default configuration.
     */
    private val loader: LoaderLaunchConfigurator = NoopLoaderConfigurator,
    /**
     * Injectable input snapshot for the session. The [LaunchPipeline]
     * interface is unchanged; a provider keeps this decoupled until
     * the input system is fully wired in a later phase.
     */
    private val inputConfiguration: () -> com.lumocraft.app.domain.input.InputConfiguration? = { null },
) : LaunchPipeline {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(LaunchProgress())
    override val state: StateFlow<LaunchProgress> = _state.asStateFlow()

    override val logLines = logs.lines

    private var job: Job? = null

    override fun launch(context: LaunchContext) {
        if (job?.isActive == true) return
        job = scope.launch {
            runSession(context)
        }
    }

    override fun cancel() {
        job?.cancel()
    }

    private suspend fun runSession(context: LaunchContext) {
        _state.value = LaunchProgress(LaunchState.PREPARING, "Preparing launch environment")
        val sessionStartNanos = System.nanoTime()
        val sessionStartMillis = System.currentTimeMillis()
        val cache = performance.cache()
        val hitsBefore = cache.hits()
        val missesBefore = cache.misses()
        val sessionFile = logs.startSession()
        writeLauncherLine("Session started: ${context.account.username} on ${context.versionId}")
        writeLauncherLine("Session log: ${sessionFile.absolutePath}")

        var process: JavaProcessHandle? = null
        var failure: LaunchFailure? = null
        var exitCode: Int? = null
        var validationMs = 0L
        var classpathMs = 0L
        var jvmStartMs = 0L
        var cachedValidation = false
        try {
            environment.prepare()
            logs.logArchitecture(nativeRuntimeManager.architecture().abi)
            logs.logMemoryProfile(performance.deviceProfile())
            logs.logJvmProfileSelection(performance.effectiveJvmProfile(), performance.jvmProfileOverride() == null)

            _state.value = LaunchProgress(LaunchState.PREPARING, "Resolving loader")
            val loaderConfig = loader.configureLaunch(context).getOrElse { throw it }
            logs.logLoaderConfiguration(loaderConfig)

            _state.value = LaunchProgress(LaunchState.PREPARING, "Fetching client jar")
            if (loaderConfig.clientJar == null) {
                clientJarManager.ensure(context.versionId).getOrElse { throw it }
            }

            // Natives first: preparation self-heals corrupt extractions, so
            // validation below sees the real state.
            _state.value = LaunchProgress(LaunchState.BUILDING_CLASSPATH, "Preparing native libraries")
            prepareNatives(context.versionId)

            val validationStart = System.nanoTime()
            _state.value = LaunchProgress(LaunchState.VALIDATING, "Validating installation")
            val report = validator.validate(context)
            validationMs = elapsedMs(validationStart)
            if (!report.ok) throw LaunchException(validationFailureMessage(report))
            cachedValidation = report.fromCache
            logs.logCacheEvent("validation", cachedValidation, context.versionId)
            markRuntimeValidated(context.versionId)
            writeLauncherLine("Validation passed")

            inputConfiguration()?.let { config -> logs.logInputConfiguration(config) }

            val classpathStart = System.nanoTime()
            _state.value = LaunchProgress(LaunchState.BUILDING_CLASSPATH, "Resolving classpath")
            val built = classpathBuilder.build(context.versionId, loaderConfig).getOrElse { throw it }
            classpathMs = elapsedMs(classpathStart)
            logs.logCacheEvent("classpath", builtFromCache(built), "${built.libraryFiles.size} libraries")
            writeLauncherLine(
                "Classpath: ${built.libraryFiles.size} libraries, main class ${built.mainClass}"
            )

            _state.value = LaunchProgress(LaunchState.BUILDING_ARGUMENTS, "Building launch arguments")
            val nativesDirectory = nativeRuntimeManager.nativeDirectory(context.versionId)
            val jniEnvironment = nativeRuntimeManager.jniEnvironment(context.versionId)
            val rendererProfile = nativeRuntimeManager.rendererProfile()
            logs.logRendererSelection(rendererProfile)
            logs.logJniPaths(jniEnvironment)
            val resolution = rendererProfile.effectiveResolution()
            logs.logResolution(resolution.width, resolution.height, rendererProfile.resolutionScale.percent)

            val args = resolveArguments(
                context = context,
                classpath = built.classpath,
                environment = environment,
                nativesDirectory = nativesDirectory,
                jniEnvironment = jniEnvironment,
                rendererProfile = rendererProfile,
                loaderConfig = loaderConfig,
                logs = logs
            )
            val finalArgs = args.copy(
                mainClass = loaderConfig.mainClass ?: args.mainClass,
                jvmArguments = args.jvmArguments +
                    loaderConfig.jvmArguments.filter { it !in args.jvmArguments },
                gameArguments = args.gameArguments +
                    loaderConfig.gameArguments.filter { it !in args.gameArguments }
            )

            val javaHome = File(context.runtime.path)
            _state.value = LaunchProgress(LaunchState.STARTING_JAVA, "Starting Java")
            writeLauncherLine("JVM: $javaHome")
            writeLauncherLine("JVM args: ${finalArgs.jvmArguments.joinToString(" ")}")
            writeLauncherLine("Game args: ${finalArgs.gameArguments.joinToString(" ")}")

            val command = JavaCommand(
                executable = File(javaHome, "bin/java"),
                arguments = finalArgs.jvmArguments + finalArgs.mainClass + finalArgs.gameArguments,
                workingDirectory = context.gameDirectory,
                environment = environment.buildProcessEnvironment(javaHome)
            )

            val startNanos = System.nanoTime()
            process = launcher.start(command)
            jvmStartMs = elapsedMs(startNanos)
            _state.value = LaunchProgress(LaunchState.RUNNING, "Minecraft is running")
            exitCode = process.stream { line -> logs.writeLine(line) }

            if (exitCode == 0) {
                _state.value = LaunchProgress(LaunchState.FINISHED, "Game closed", exitCode = 0)
                writeLauncherLine("Process exited cleanly")
            } else {
                failure = crashAnalyzer.analyze(exitCode, logs.recentLines())
                writeLauncherLine("Process exited with code $exitCode")
            }
        } catch (cancelled: CancellationException) {
            failure = LaunchFailure(LaunchErrorType.CANCELLED, "Launch cancelled by user")
            writeLauncherLine("Launch cancelled")
            process?.cancel()
        } catch (error: Throwable) {
            failure = when (error) {
                is LaunchException -> LaunchFailure(error.type, error.message)
                is NativeException -> LaunchFailure(
                    LaunchErrorType.NATIVE_LIBRARY_MISSING,
                    error.message
                )
                else -> LaunchFailure(LaunchErrorType.UNKNOWN, error.message)
            }
            writeLauncherLine("Launch failed: ${error.message}")
            process?.cancel()
        } finally {
            val totalMs = elapsedMs(sessionStartNanos)
            val hits = (cache.hits() - hitsBefore).toInt().coerceAtLeast(0)
            val misses = (cache.misses() - missesBefore).toInt().coerceAtLeast(0)
            logs.logLaunchTiming(
                validationMs = validationMs,
                classpathMs = classpathMs,
                jvmStartMs = jvmStartMs,
                totalMs = totalMs,
                cacheHits = hits,
                cacheMisses = misses,
                cachedValidation = cachedValidation
            )
            performance.profiler().record(
                LaunchTimings(
                    validationMs = validationMs,
                    classpathMs = classpathMs,
                    jvmStartMs = jvmStartMs,
                    totalMs = totalMs,
                    cachedValidation = cachedValidation,
                    cacheHits = hits,
                    cacheMisses = misses,
                    success = failure == null,
                    startedAt = sessionStartMillis
                )
            )
            performance.memory().cleanupAfterLaunch()
            logs.endSession()
            if (failure != null) {
                _state.value = LaunchProgress(LaunchState.FAILED, null, exitCode, failure)
            }
            job = null
        }
    }

    /** Cached arguments when the fingerprint matches; otherwise rebuilt. */
    private suspend fun resolveArguments(
        context: LaunchContext,
        classpath: String,
        environment: LaunchEnvironment,
        nativesDirectory: File,
        jniEnvironment: Map<String, String>,
        rendererProfile: com.lumocraft.app.domain.native.RendererProfile,
        loaderConfig: LoaderLaunchConfiguration,
        logs: LauncherLogRepository,
    ): LaunchArguments {
        val resolvedConfig = performance.resolveJvmConfiguration(context.jvmConfiguration)
        val fingerprint = argumentFingerprint(
            context = context,
            classpath = classpath,
            jniEnvironment = jniEnvironment,
            rendererProfile = rendererProfile,
            resolvedConfig = resolvedConfig,
            loaderConfig = loaderConfig
        )
        val cache = performance.cache()
        val cached = cache.getEntry(context.versionId)
            ?.takeIf { it.launchArgumentsFingerprint == fingerprint && it.launchArgumentsJson != null }
        val decoded = cached?.let { decodeArguments(it.launchArgumentsJson!!) }
        if (decoded != null) {
            cache.recordHit()
            logs.logCacheEvent("arguments", true, context.versionId)
            return decoded
        }
        if (cached != null) {
            // Corrupt payload: drop the row so the next launch rebuilds it.
            cache.removeEntry(context.versionId)
        }
        cache.recordMiss()
        logs.logCacheEvent("arguments", false, context.versionId)
        val args = argumentBuilder.build(
            context = context.copy(jvmConfiguration = resolvedConfig),
            classpath = classpath,
            environment = environment,
            nativesDirectory = nativesDirectory,
            jniEnvironment = jniEnvironment,
            rendererProfile = rendererProfile
        ).getOrElse { throw it }
        val base = cache.getEntry(context.versionId) ?: LaunchCacheEntry(context.versionId)
        cache.putEntry(
            base.copy(
                launchArgumentsFingerprint = fingerprint,
                launchArgumentsJson = encodeArguments(args)
            )
        )
        return args
    }

    private fun argumentFingerprint(
        context: LaunchContext,
        classpath: String,
        jniEnvironment: Map<String, String>,
        rendererProfile: com.lumocraft.app.domain.native.RendererProfile,
        resolvedConfig: com.lumocraft.app.domain.runtime.JvmConfiguration,
        loaderConfig: LoaderLaunchConfiguration,
    ): String = listOf(
        context.versionId,
        context.account.username,
        context.runtime.id,
        context.runtime.path,
        context.gameDirectory.absolutePath,
        resolvedConfig.maxMemoryMB,
        resolvedConfig.minMemoryMB,
        resolvedConfig.gcMode.name,
        resolvedConfig.extraArguments.joinToString(","),
        classpath,
        jniEnvironment.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" },
        rendererProfile.renderer.name,
        rendererProfile.resolutionScale.percent,
        rendererProfile.fpsLimit ?: "unlimited",
        rendererProfile.vsync,
        rendererProfile.mipmaps,
        loaderConfig.type.id,
        loaderConfig.jvmArguments.joinToString(","),
        loaderConfig.gameArguments.joinToString(","),
        loaderConfig.mainClass ?: ""
    ).joinToString("|")

    private fun encodeArguments(args: LaunchArguments): String =
        JSONObject()
            .put("jvm", JSONArray(args.jvmArguments))
            .put("game", JSONArray(args.gameArguments))
            .put("mainClass", args.mainClass)
            .toString()

    private fun decodeArguments(json: String): LaunchArguments? = runCatching {
        val obj = JSONObject(json)
        val jvm = obj.optJSONArray("jvm")?.toStringList() ?: emptyList()
        val game = obj.optJSONArray("game")?.toStringList() ?: emptyList()
        val mainClass = obj.optString("mainClass")
        if (jvm.isEmpty() && game.isEmpty() && mainClass.isEmpty()) return null
        LaunchArguments(jvmArguments = jvm, gameArguments = game, mainClass = mainClass)
    }.getOrNull()

    private fun org.json.JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(optString(i))
    }

    /** Marks the cached runtime validation for this version. */
    private suspend fun markRuntimeValidated(versionId: String) {
        val cache = performance.cache()
        val base = cache.getEntry(versionId) ?: LaunchCacheEntry(versionId)
        cache.putEntry(
            base.copy(
                runtimeValidated = true,
                lastVerifiedAt = System.currentTimeMillis()
            )
        )
    }

    private fun builtFromCache(built: BuiltClasspath): Boolean =
        built.fromCache

    /**
     * Safety gate: natives must be extracted, verified and match the
     * device architecture. Each failure maps to an actionable error.
     */
    private suspend fun prepareNatives(versionId: String) {
        val report = nativeRuntimeManager.prepare(versionId).getOrElse { throw it }
        when {
            report.archMismatch -> throw LaunchException(
                type = LaunchErrorType.NATIVE_ARCH_MISMATCH,
                message = "Natives were extracted for ${report.arch.abi}, but this device " +
                    "uses ${nativeRuntimeManager.architecture().abi}"
            )
            report.status == NativeStatus.CORRUPTED -> throw LaunchException(
                type = LaunchErrorType.NATIVE_CORRUPTED,
                message = "Native extraction is corrupted. Missing: " +
                    "${report.missingFiles.joinToString(", ").take(200)}; " +
                    "corrupt: ${report.corruptFiles.joinToString(", ").take(200)}"
            )
            report.status != NativeStatus.READY -> throw LaunchException(
                type = LaunchErrorType.NATIVE_LIBRARY_MISSING,
                message = "Native libraries are not ready for ${nativeRuntimeManager.architecture().abi}"
            )
        }
        logs.logNativeExtraction(
            versionId = versionId,
            arch = report.arch.abi,
            directory = report.nativeDirectory.absolutePath,
            extracted = report.extractedFiles,
            skippedJars = report.skippedJars,
            duplicatesRemoved = report.duplicatesRemoved
        )
    }

    private suspend fun writeLauncherLine(line: String) {
        logs.writeLine("[LumoCraft] $line")
    }

    private fun validationFailureMessage(report: LaunchValidationReport): String =
        buildString {
            append("Validation failed:")
            if (!report.accountOk) append(" account missing;")
            if (!report.runtimeOk) append(" runtime missing;")
            if (!report.versionOk) append(" version not installed;")
            if (!report.mainClassOk) append(" main class missing;")
            if (!report.clientJarOk) append(" client jar missing;")
            if (!report.assetIndexOk) append(" asset index missing;")
            if (!report.loggingConfigOk) append(" logging config missing;")
            if (!report.nativeOk) append(" natives not ready (${report.nativeDetail});")
            if (report.missingLibraries.isNotEmpty()) {
                append(
                    " libraries missing: ${report.missingLibraries.take(MAX_REPORTED_LIBS).joinToString(", ")}"
                )
            }
            if (report.missingAssets > 0) append(" ${report.missingAssets} assets missing;")
        }.trimEnd(';')

    private fun elapsedMs(startNanos: Long): Long =
        (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        const val MAX_REPORTED_LIBS = 10
    }
}

/**
 * Vanilla fallback for the loader slot: no loader is active, everything
 * keeps the version JSON defaults. Used when the pipeline is constructed
 * without a loader registry (tests, future loader-less builds).
 */
private object NoopLoaderConfigurator : LoaderLaunchConfigurator {
    override suspend fun configureLaunch(
        context: LaunchContext,
    ): Result<LoaderLaunchConfiguration> = Result.success(LoaderLaunchConfiguration())

    override suspend fun clientJarFor(versionId: String): java.io.File? = null
}