package com.lumocraft.app.data.runtime

import android.util.Log
import java.io.File

/**
 * Single owner of the "runtime launcher binaries are executable" rule.
 *
 * Android storage does not reliably preserve POSIX exec metadata from
 * downloaded tar.gz archives, so after every extraction the launcher
 * binaries under `bin/` are made executable explicitly with
 * [restoreExecutableBits]. Callers that must not continue (installer,
 * launch preflight) use [canExecute] as the gate and fail with a
 * user-friendly error instead of a Permission-denied crash.
 */
object RuntimePermissions {

    private const val TAG = "LumoCraft/RuntimePermissions"

    /**
     * Applies `setExecutable(true, false)` to every launcher binary
     * inside `<runtimeRoot>/bin/` (java, javac, keytool and any other
     * launcher shipped with the runtime), then re-checks each file with
     * `canExecute()`. Returns true only when every file is executable.
     */
    fun restoreExecutableBits(runtimeRoot: File): Boolean {
        Log.d(TAG, "RUNTIME_PERMISSION_REPAIR_STARTED root=${runtimeRoot.absolutePath}")
        val binDir = File(runtimeRoot, "bin")
        val launchers = if (binDir.isDirectory) {
            binDir.listFiles()?.filter { it.isFile } ?: emptyList()
        } else {
            emptyList()
        }
        if (launchers.isEmpty()) {
            Log.e(
                TAG,
                "RUNTIME_EXECUTABLE_FAILED path=${binDir.absolutePath} " +
                    "canExecute=false result=noLauncherBinaries"
            )
            return false
        }
        var allExecutable = true
        for (launcher in launchers) {
            launcher.setExecutable(true, false)
            val executable = launcher.canExecute()
            if (!executable) allExecutable = false
            Log.d(
                TAG,
                "RUNTIME_EXECUTABLE_SET path=${launcher.absolutePath} " +
                    "canExecute=$executable"
            )
        }
        if (allExecutable) {
            Log.d(
                TAG,
                "RUNTIME_EXECUTABLE_VERIFIED root=${runtimeRoot.absolutePath} " +
                    "files=${launchers.size} result=ok"
            )
        } else {
            Log.e(
                TAG,
                "RUNTIME_EXECUTABLE_FAILED root=${runtimeRoot.absolutePath} " +
                    "files=${launchers.size} result=notExecutable"
            )
        }
        return allExecutable
    }

    /**
     * True when [file] exists, is a regular file and carries the
     * executable bit: `exists() && isFile && canExecute()`.
     */
    fun canExecute(file: File): Boolean =
        file.exists() && file.isFile && file.canExecute()
}