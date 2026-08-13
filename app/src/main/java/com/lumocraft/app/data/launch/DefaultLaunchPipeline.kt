package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.launch.LaunchFailure
import com.lumocraft.app.domain.launch.LaunchPipeline
import com.lumocraft.app.domain.launch.LaunchProgress
import com.lumocraft.app.domain.launch.LaunchState
import com.lumocraft.app.domain.launch.LaunchValidationReport
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
 * Reference [LaunchPipeline]: validates, prepares, classpaths, extracts
 * natives, builds arguments and runs the Java process while streaming
 * its output into the session log. Any terminal state leaves the session
 * log file on disk for the "Open logs" action.
 */
class DefaultLaunchPipeline(
    private val environment: LaunchEnvironment,
    private val validator: LaunchValidator,
    private val classpathBuilder: ClasspathBuilder,
    private val argumentBuilder: LaunchArgumentBuilder,
    private val clientJarManager: ClientJarManager,
    private val nativeExtractor: NativeExtractor,
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

            _state.value = LaunchProgress(LaunchState.PREPARING, "Fetching client jar")
            clientJarManager.ensure(context.versionId).getOrElse { throw it }

            _state.value = LaunchProgress(LaunchState.VALIDATING, "Validating installation")
            val report = validator.validate(context)
            if (!report.ok) throw LaunchException(validationFailureMessage(report))
            writeLauncherLine("Validation passed")

            _state.value = LaunchProgress(LaunchState.BUILDING_CLASSPATH, "Resolving classpath")
            val built = classpathBuilder.build(context.versionId).getOrElse { throw it }
            writeLauncherLine(
                "Classpath: ${built.libraryFiles.size} libraries, main class ${built.mainClass}"
            )

            _state.value = LaunchProgress(LaunchState.BUILDING_CLASSPATH, "Extracting native libraries")
            nativeExtractor.extract(context.versionId, built.libraryRefs).getOrElse { throw it }

            _state.value = LaunchProgress(LaunchState.BUILDING_ARGUMENTS, "Building launch arguments")
            val args = argumentBuilder.build(context, built.classpath, environment)
                .getOrElse { throw it }

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
            failure = LaunchFailure(LaunchErrorType.UNKNOWN, error.message)
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