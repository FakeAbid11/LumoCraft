package com.lumocraft.app.data.export

import android.os.Build
import android.os.Process
import com.lumocraft.app.BuildConfig
import com.lumocraft.app.data.storage.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught-exception handler that persists a crash report under
 * `<launcherRoot>/logs/crashes/` so the Diagnostics screen can export
 * it later, then delegates to the previous handler so the process
 * terminates exactly as before. The handler itself is defensively
 * wrapped — it can never make a crash worse.
 */
class CrashLogHandler(
    private val storage: StorageManager,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val dir = crashDirectory().apply { mkdirs() }
            val file = File(dir, "crash-${timestamp()}.txt")
            val report = buildString {
                appendLine("=== LumoCraft crash report ===")
                appendLine("Time: ${Date()}")
                appendLine("App: LumoCraft ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                appendLine("Thread: ${thread.name}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine()
                appendLine(throwable.stackTraceToString())
            }
            file.writeText(report)
        } catch (_: Exception) {
            // The crash handler must never throw.
        } finally {
            val fallback = defaultHandler ?: object : Thread.UncaughtExceptionHandler {
                override fun uncaughtException(t: Thread, e: Throwable) {
                    Process.killProcess(Process.myPid())
                }
            }
            fallback.uncaughtException(thread, throwable)
        }
    }

    private fun crashDirectory(): File = File(storage.launcherRoot(), "logs/crashes")

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
