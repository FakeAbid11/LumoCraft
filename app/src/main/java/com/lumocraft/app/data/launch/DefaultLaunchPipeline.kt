package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.launch.LaunchFailure
import com.lumocraft.app.domain.launch.LaunchPipeline
import com.lumocraft.app.domain.launch.LaunchProgress
import com.lumocraft.app.domain.launch.LaunchState
import com.lumocraft.app.domain.launch.LaunchValidationReport
import com.lumocraft.app.domain.native.NativeException
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.native.NativeStatus
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

/**
 * Reference [LaunchPipeline]: validates, prepares (including native
 * libraries through [NativeRuntimeManager]), classpaths, builds
 * arguments with the JNI environment and renderer profile injected,
 * spawns the Java process and streams its output into the session log.
 * Any terminal state leaves the session log file on disk for the
 * "Open logs" action.
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
        val sessionFile = logs.startSession()
        writeLauncherLine("Session started: ${context.account.username} on ${context.versionId}")
        writeLauncherLine("Session log: ${sessionFile.absolutePath}")

        var process: JavaProcessHandle? = null
        var failure: LaunchFailure? = null
        var exitCode: Int? = null
        try {
            environment.prepare()
            logs.logArchitecture(nativeRuntimeManager.architecture().abi)

            _state.value = LaunchProgress(LaunchState.PREPARING, "Fetching client jar")
            clientJarManager.ensure(context.versionId).getOrElse { throw it }

            // Natives first: preparation self-heals corrupt extractions, so
            // validation below sees the real state.
            _state.value = LaunchProgress(LaunchState.BUILDING_CLASSPATH, "Preparing native libraries")
            prepareNatives(context.versionId)

            _state.value = LaunchProgress(LaunchState.VALIDATING, "Validating installation")
            val report = validator.validate(context)
            if (!report.ok) throw LaunchException(validationFailureMessage(report))
            writeLauncherLine("Validation passed")

            _state.value = LaunchProgress(LaunchState.BUILDING_CLASSPATH, "Resolving classpath")
            val built = classpathBuilder.build(context.versionId).getOrElse { throw it }
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

            val args = argumentBuilder.build(
                context = context,
                classpath = built.classpath,
                environment = environment,
                nativesDirectory = nativesDirectory,
                jniEnvironment = jniEnvironment,
                rendererProfile = rendererProfile
            ).getOrElse { throw it }

            val javaHome = File(context.runtime.path)
            _state.value = LaunchProgress(LaunchState.STARTING_JAVA, "Starting Java")
            writeLauncherLine("JVM: $javaHome")
            writeLauncherLine("JVM args: ${args.jvmArguments.joinToString(" ")}")
            writeLauncherLine("Game args: ${args.gameArguments.joinToString(" ")}")

            val command = JavaCommand(
                executable = File(javaHome, "bin/java"),
                arguments = args.jvmArguments + args.mainClass + args.gameArguments,
                workingDirectory = context.gameDirectory,
                environment = environment.buildProcessEnvironment(javaHome)
            )

            process = launcher.start(command)
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
            logs.endSession()
            if (failure != null) {
                _state.value = LaunchProgress(LaunchState.FAILED, null, exitCode, failure)
            }
            job = null
        }
    }

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

    private companion object {
        const val MAX_REPORTED_LIBS = 10
    }
}