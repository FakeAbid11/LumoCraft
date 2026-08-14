package com.lumocraft.app.data.launch

import android.util.Log
import com.lumocraft.app.data.runtime.RuntimePermissions
import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchException
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full command line for one Java process. */
data class JavaCommand(
    val executable: File,
    val arguments: List<String>,
    val workingDirectory: File,
    val environment: Map<String, String>
)

/**
 * A running Java process. [stream] pumps both output streams (on the IO
 * dispatcher, with buffered readers) until the process exits and returns
 * its exit code; the pumping coroutines are children of the caller's
 * scope, so cancellation propagates. [cancel] destroys the process.
 */
class JavaProcessHandle internal constructor(private val process: Process) {

    /** Streams stdout and stderr through [onLine]; returns the exit code. */
    suspend fun stream(onLine: suspend (String) -> Unit): Int = coroutineScope {
        val stdout = launch { pump(process.inputStream, onLine) }
        val stderr = launch { pump(process.errorStream, onLine) }
        val exit = withContext(Dispatchers.IO) { process.waitFor() }
        stdout.join()
        stderr.join()
        exit
    }

    fun cancel() {
        if (!process.isAlive) return
        process.destroy()
        Thread {
            try {
                Thread.sleep(CANCEL_GRACE_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (process.isAlive) process.destroyForcibly()
        }.apply { isDaemon = true }.start()
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

    private companion object {
        const val CANCEL_GRACE_MS = 3_000L
    }
}

/** Spawns [JavaCommand]'s process without blocking the caller. */
class JavaLauncher {

    /**
     * Pre-flight diagnostics before [ProcessBuilder]: absolute path,
     * existence, readability, executability and parent directory
     * permissions. A non-executable launcher is refused up front with a
     * user-friendly [LaunchException] instead of a raw
     * `Permission denied` crash from ProcessBuilder.
     */
    suspend fun start(command: JavaCommand): JavaProcessHandle = withContext(Dispatchers.IO) {
        val executable = command.executable
        logDiagnostics(executable)
        if (!RuntimePermissions.canExecute(executable)) {
            Log.e(
                TAG,
                "RUNTIME_EXECUTABLE_FAILED path=${executable.absolutePath} " +
                    "canExecute=${executable.canExecute()} result=launchRefused"
            )
            throw LaunchException(
                message = "The Java binary is missing or not executable: " +
                    executable.absolutePath,
                type = LaunchErrorType.PERMISSION_DENIED
            )
        }
        val builder = ProcessBuilder(
            executable.absolutePath,
            *command.arguments.toTypedArray()
        )
        builder.directory(command.workingDirectory)
        builder.environment().putAll(command.environment)
        JavaProcessHandle(builder.start())
    }

    private fun logDiagnostics(executable: File) {
        val parent = executable.parentFile
        Log.d(
            TAG,
            "JAVA_LAUNCHER_PREFLIGHT " +
                "path=${executable.absolutePath} " +
                "exists=${executable.exists()} " +
                "canRead=${executable.canRead()} " +
                "canExecute=${executable.canExecute()} " +
                "parentExists=${parent?.exists() ?: false} " +
                "parentCanRead=${parent?.canRead() ?: false} " +
                "parentCanWrite=${parent?.canWrite() ?: false}"
        )
    }

    private companion object {
        const val TAG = "LumoCraft/JavaLauncher"
    }
}