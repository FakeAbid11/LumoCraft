package com.lumocraft.app.data.launch

import android.os.Build
import android.system.Os
import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Full command line for one in-process JVM launch. */
data class JvmLaunchCommand(
    val javaHome: File,
    val arguments: List<String>,
    val workingDirectory: File,
    val environment: Map<String, String>,
    val architecture: RuntimeArchitecture? = null
)

/**
 * A JVM running inside the launcher process (dlopen(libjli.so) +
 * JLI_Launch). [stream] pumps the game's stdout/stderr from the pipe the
 * native side redirects into and polls for the exit code; [cancel] asks
 * the game JVM to exit through its own JNI invocation API. Nothing here
 * blocks on the JLI thread: [NativeJvmLauncher.waitForExit] polls a
 * completion flag with a bounded timeout, so a wedged JVM can never
 * freeze the caller.
 */
class JvmProcessHandle internal constructor(
    private val launcher: NativeJvmLauncher,
    private val readFd: FileDescriptor
) {

    private val input = FileInputStream(readFd)
    private val cancelled = AtomicBoolean(false)

    // The pump runs in its own scope, not as a child of the stream
    // coroutine: a blocked readLine on the pipe must never stall the
    // session, no matter how the JVM misbehaves.
    private val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pumpJob: Job? = null

    /**
     * Streams stdout/stderr through [onLine] and returns the exit code.
     *
     * JVM startup and JVM lifetime are separated: the first
     * [STARTUP_WINDOW_MS] is the bounded startup phase. During it,
     * [onHeartbeat] fires roughly every second so the UI can show
     * liveness. If the JLI thread exits inside the window, its exit code
     * is the (failed) startup outcome. If it is still running after the
     * window but produced no output at all, the JVM is considered wedged
     * during initialization and [NativeJvmLauncher.K_START_TIMEOUT] is
     * returned instead of waiting forever. Otherwise the session runs
     * until the game exits — an unbounded wait in total, but polled in
     * short, cancellable slices so stopping the session always works.
     */
    suspend fun stream(
        onLine: suspend (String) -> Unit,
        onHeartbeat: suspend (Long) -> Unit,
        onStarted: suspend () -> Unit,
    ): Int = coroutineScope {
        val sawOutput = AtomicBoolean(false)
        pumpJob = pumpScope.launch {
            pump(input) { line ->
                sawOutput.set(true)
                onLine(line)
            }
        }
        val start = System.nanoTime()
        var lastHeartbeatMs = 0L

        // Startup window: bounded, poll-based, with heartbeats. If the
        // JLI thread exits inside the window, its code is the startup
        // outcome. If it is still running after the window with no output
        // at all, the JVM is wedged during initialization.
        while (true) {
            val code = withContext(Dispatchers.IO) {
                launcher.waitForExit(timeoutMillis = 0)
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            if (code != NativeJvmLauncher.K_EXIT_TIMEOUT) {
                pumpShutdown()
                return@coroutineScope code
            }
            if (elapsedMs >= STARTUP_WINDOW_MS) {
                if (!sawOutput.get()) {
                    pumpShutdown()
                    return@coroutineScope NativeJvmLauncher.K_START_TIMEOUT
                }
                break
            }
            if (elapsedMs - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
                lastHeartbeatMs = elapsedMs
                onHeartbeat(elapsedMs / 1000)
            }
            delay(HEARTBEAT_POLL_MS)
        }

        // Startup succeeded: the JVM is running, switch the UI state and
        // wait for the game to exit. The wait stays poll-based and
        // cancellable so a wedged JVM can never freeze or block the
        // session from being stopped.
        onStarted()
        while (true) {
            val exit = withContext(Dispatchers.IO) {
                launcher.waitForExit(timeoutMillis = LIFETIME_POLL_MS)
            }
            if (exit != NativeJvmLauncher.K_EXIT_TIMEOUT) {
                pumpShutdown()
                return@coroutineScope exit
            }
            delay(LIFETIME_POLL_MS)
        }
    }

    /**
     * Ends the pump without ever joining it blindly: closes the read end
     * (the native side closes the write end on JVM exit), waits at most
     * [PUMP_JOIN_TIMEOUT_MS] for the pump coroutine, then cancels the
     * pump scope. A pump still blocked on the pipe drains itself once
     * the native side closes its write end.
     */
    private suspend fun pumpShutdown() {
        runCatching { input.close() }
        withTimeoutOrNull(PUMP_JOIN_TIMEOUT_MS) { pumpJob?.join() }
        pumpJob?.cancel()
        pumpScope.cancel()
    }

    /** Asks the game JVM to exit; runs on IO, never on the UI thread. */
    suspend fun cancel() = withContext(NonCancellable + Dispatchers.IO) {
        if (!cancelled.compareAndSet(false, true)) return@withContext
        launcher.cancel()
        // Unblock the pipe reader immediately; the native side restores
        // the process output once the JVM thread exits.
        runCatching { input.close() }
    }

    /** Reaps the finished JLI thread and resets the native launch state. */
    suspend fun recycle() = withContext(NonCancellable + Dispatchers.IO) {
        runCatching { input.close() }
        launcher.recycleLaunch()
    }

    private suspend fun pump(
        input: InputStream,
        onLine: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val reader = input.bufferedReader(Charsets.UTF_8)
        try {
            while (true) {
                coroutineContext.ensureActive()
                val line = reader.readLine() ?: break
                runCatching { onLine(line) }
            }
        } finally {
            reader.close()
        }
    }

    private companion object {
        const val STARTUP_WINDOW_MS = 30_000L
        const val HEARTBEAT_INTERVAL_MS = 1_000L
        const val HEARTBEAT_POLL_MS = 250L
        const val LIFETIME_POLL_MS = 250L
        const val PUMP_JOIN_TIMEOUT_MS = 2_000L
    }
}

/**
 * Starts the game JVM in-process through JNI instead of exec'ing
 * `bin/java`. Android mounts app-writable directories with `noexec`, so
 * ProcessBuilder fails with `Permission denied` (error=13); shared
 * libraries are unaffected, so the runtime's `lib/jli/libjli.so` is
 * dlopen'd and `JLI_Launch` runs the JVM on a native thread. The
 * existing classpath/argument/environment builders are reused as-is.
 *
 * The native handshake is deliberately asynchronous and bounded: all
 * native calls happen on the IO dispatcher, [waitForExit] polls instead
 * of joining, and the caller bounds the whole start with a timeout, so
 * the Android UI never waits on the JVM.
 */
class NativeJvmLauncher(private val logs: LauncherLogRepository) {

    private val loadFailure: String? = runCatching {
        System.loadLibrary("jvm_launcher")
    }.exceptionOrNull()?.message

    suspend fun start(command: JvmLaunchCommand): JvmProcessHandle =
        withContext(Dispatchers.IO) {
            loadFailure?.let {
                throw LaunchException(
                    message = "The native JVM launcher is unavailable: $it",
                    type = LaunchErrorType.JVM_INITIALIZATION_FAILURE
                )
            }
            val javaHome = command.javaHome
            checkArchitecture(command)
            val jli = resolveJliLibrary(javaHome, command.architecture)
            val missing = requiredLibraries(javaHome, jli)
            if (missing.isNotEmpty()) {
                logs.writeLine("JLI: runtime libraries missing: $missing")
                throw LaunchException(
                    message = "The Java runtime is missing the JVM launcher " +
                        "libraries ($missing). Repair the runtime in Settings.",
                    type = LaunchErrorType.JVM_INITIALIZATION_FAILURE
                )
            }
            logs.writeLine("JLI library path: ${jli.absolutePath}")

            val pipe = Os.pipe()
            val code = try {
                launch(
                    javaHome = javaHome.absolutePath,
                    workingDirectory = command.workingDirectory.absolutePath,
                    environment = command.environment
                        .map { (key, value) -> "$key=$value" }
                        .toTypedArray(),
                    argv = buildList {
                        add("java")
                        addAll(command.arguments)
                    }.toTypedArray(),
                    stdoutFd = pipe[1]
                )
            } catch (error: Throwable) {
                runCatching { Os.close(pipe[0]) }
                throw LaunchException(
                    message = "The native JVM launcher failed: ${error.message}",
                    type = LaunchErrorType.JVM_INITIALIZATION_FAILURE
                )
            }
            if (code != kOk) {
                val detail = lastError()
                runCatching { Os.close(pipe[0]) }
                logs.writeLine("JLI: launch failed (code $code): $detail")
                throw LaunchException(
                    message = "The JVM could not be started in-process: $detail",
                    type = LaunchErrorType.JVM_INITIALIZATION_FAILURE
                )
            }

            logs.writeLine("JLI: dlopen ok, JLI_Launch resolved, JVM thread started")
            JvmProcessHandle(this@NativeJvmLauncher, pipe[0])
        }

    /**
     * Native side; see app/src/main/cpp/jvm_launcher.cpp. Returns 0 when
     * the JVM thread was started, or a negative error code; [lastError]
     * carries the detail.
     */
    private external fun launch(
        javaHome: String,
        workingDirectory: String,
        environment: Array<String>,
        argv: Array<String>,
        stdoutFd: FileDescriptor
    ): Int

    /**
     * Polls for JLI_Launch completion. Returns the JVM exit code when
     * the JLI thread finished within [timeoutMillis] (<= 0 waits
     * indefinitely), otherwise [K_EXIT_TIMEOUT]. Never joins.
     */
    internal external fun waitForExit(timeoutMillis: Long): Int

    /** Human-readable detail of the last native failure. */
    private external fun lastError(): String

    /** Best-effort stop of the running game JVM. */
    internal external fun cancel()

    /** Reaps the JLI thread after exit and resets the launch state. */
    internal external fun recycleLaunch()

    /** Rejects a runtime recorded for a different device architecture. */
    private fun checkArchitecture(command: JvmLaunchCommand) {
        val expected = command.architecture ?: return
        val device = RuntimeArchitecture.fromAbi(Build.SUPPORTED_ABIS.firstOrNull() ?: "")
        if (device != null && device != expected) {
            throw LaunchException(
                message = "This runtime was installed for ${expected.abi}, " +
                    "but the device uses ${device.abi}. Install a runtime " +
                    "for ${device.abi} in Settings.",
                type = LaunchErrorType.NATIVE_ARCH_MISMATCH
            )
        }
    }

    /**
     * Modern JDK 9+ layouts ship `lib/jli/libjli.so` (Temurin); some
     * builds (e.g. Microsoft) keep it flat in `lib/`. Legacy JDK 8
     * layouts used `lib/<arch>/libjli.so`.
     */
    private fun resolveJliLibrary(
        javaHome: File,
        architecture: RuntimeArchitecture?
    ): File {
        val primary = File(javaHome, "lib/jli/libjli.so")
        if (primary.isFile) return primary
        val flat = File(javaHome, "lib/libjli.so")
        if (flat.isFile) return flat
        val legacyArch = when (architecture) {
            RuntimeArchitecture.ARM64_V8A -> "aarch64"
            RuntimeArchitecture.ARMEABI_V7A -> "arm"
            RuntimeArchitecture.X86_64 -> "x64"
            null -> null
        }
        val legacy = legacyArch?.let { File(javaHome, "lib/$it/libjli.so") }
        return if (legacy != null && legacy.isFile) legacy else primary
    }

    /** Files the JLI launcher needs to start the JVM. */
    private fun requiredLibraries(javaHome: File, jli: File): List<String> =
        listOf(
            jli to "libjli.so",
            File(javaHome, "lib/server/libjvm.so") to "libjvm.so",
            File(javaHome, "lib/modules") to "lib/modules",
            File(javaHome, "lib/jvm.cfg") to "lib/jvm.cfg"
        ).filterNot { (file, _) -> file.isFile }
            .map { (_, name) -> name }

    companion object {
        const val kOk = 0

        /** Native waitForExit() sentinel: JLI thread still running. */
        internal const val K_EXIT_TIMEOUT = -6

        /** Kotlin-side sentinel: startup window elapsed with no output. */
        internal const val K_START_TIMEOUT = -7
    }
}