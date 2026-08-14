package com.lumocraft.app.data.export

import android.content.Context
import com.lumocraft.app.data.storage.StorageManager
import java.io.File

/**
 * Redacts personally identifiable and environment-specific information
 * from exported log/crash text while keeping the debugging value:
 *
 * - account usernames are replaced with [USERNAME_PLACEHOLDER]
 * - absolute paths under the launcher root, app data directories and
 *   common Android storage roots are replaced with path placeholders
 * - sensitive environment-variable assignments (`HOME=...`,
 *   `JAVA_HOME=...`, `PATH=...`, …) keep the variable name but drop the
 *   value
 *
 * The redaction is applied line-by-line during export, so the on-device
 * log files themselves are never modified.
 */
class LogRedactor(
    private val context: Context,
    private val storage: StorageManager,
) {

    private val pathReplacements: List<Pair<String, String>> = buildList {
        add(storage.launcherRoot().absolutePath to LAUNCHER_ROOT_PLACEHOLDER)
        add(context.filesDir.absolutePath to "<files-dir>")
        add(context.cacheDir.absolutePath to "<cache-dir>")
        context.getExternalFilesDir(null)?.let { add(it.absolutePath to "<external-files>") }
        add("/data/user/0/${context.packageName}" to "<app-data>")
        add("/data/data/${context.packageName}" to "<app-data>")
        add("/data/user/0" to "<android-data>")
        add("/data/data" to "<android-data>")
        add("/storage/emulated/0" to "<storage-root>")
        add("/storage/self/primary" to "<storage-root>")
        add(System.getProperty("user.home").orEmpty() to "<user-home>")
        add(System.getProperty("java.io.tmpdir").orEmpty() to "<tmp>")
    }.filter { it.first.isNotEmpty() && it.first.length > 2 }

    /**
     * Redacts one log line. [usernames] are the account names to protect.
     */
    fun redact(line: String, usernames: List<String>): String {
        var out = line
        for (name in usernames) {
            if (name.isNotEmpty()) out = out.replace(name, USERNAME_PLACEHOLDER)
        }
        for ((path, placeholder) in pathReplacements) {
            out = out.replace(path, placeholder)
        }
        return SANITIZED_ENV_VARS.fold(out) { result, key ->
            result.replace(Regex("(^|[\\s;])$key=[^\\s\"]+")) { match ->
                match.value.substringBefore('=') + "=<redacted>"
            }
        }
    }

    companion object {
        const val USERNAME_PLACEHOLDER = "[REDACTED]"
        const val LAUNCHER_ROOT_PLACEHOLDER = "<launcher-root>"

        /** Env var names whose values must not leave the device. */
        private val SANITIZED_ENV_VARS = listOf(
            "JAVA_HOME", "JDK_HOME", "HOME", "USER", "USERNAME", "LOGNAME",
            "PATH", "ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_DATA",
            "ANDROID_ROOT", "GRADLE_USER_HOME", "TMPDIR", "TMP", "TEMP"
        )
    }
}

/** Convenience: redact a file path into a safe display form. */
fun File.redactedPath(redactor: LogRedactor): String =
    redactor.redact(absolutePath, emptyList())