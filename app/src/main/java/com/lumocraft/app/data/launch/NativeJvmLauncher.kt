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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * native side redirects into, then waits for the JVM exit code;
 * [cancel] asks the game JVM to exit through its own JNI invocation API.
 */
class JvmProcessHandle internal constructor(
    private val launcher: NativeJvmLauncher,
    private val readFd: FileDescriptor
) {

    private val input = FileInputStream(readFd)
    private val cancelled = AtomicBoolean(false)

    /** Streams stdout and stderr through [onLine]; returns the exit code. */
    suspend fun stream(onLine: suspend (String) -> Unit): Int = coroutineScope {
        val pump = launch { pump(input, onLine) }
        val exit = withContext(Dispatchers.IO) { launcher.exitCode() }
        pump.join()
        exit
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        launcher.cancel()
        // Unblock the pipe reader immediately; the native side restores
        // the process output once the JVM thread exits.
        runCatching { input.close() }
    }

    private suspend fun pump(input: InputStream, onLine: suspend (String) -> Unit) =
        withContext(Dispatchers.IO) {
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
}

/**
 * Starts the game JVM in-process through JNI instead of exec'ing
 * `bin/java`. Android mounts app-writable directories with `noexec`, so
 * ProcessBuilder fails with `Permission denied` (error=13); shared
 * libraries are unaffected, so the runtime's `lib/jli/libjli.so` is
 * dlopen'd and `JLI_Launch` runs the JVM on a native thread. The
 * existing classpath/argument/environment builders are reused as-is.
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
            if (code != 0) {
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

    /** Blocks until the JVM exits and returns its exit code. */
    internal external fun exitCode(): Int

    /** Human-readable detail of the last native failure. */
    private external fun lastError(): String

    /** Best-effort stop of the running game JVM. */
    internal external fun cancel()

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
}