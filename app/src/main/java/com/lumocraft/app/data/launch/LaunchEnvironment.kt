package com.lumocraft.app.data.launch

import com.lumocraft.app.data.storage.StorageManager
import java.io.File

/**
 * Android-friendly filesystem layout for the Java process: everything
 * lives inside LumoCraft's storage so the game never touches the real
 * device home directory or /tmp.
 */
class LaunchEnvironment(private val storage: StorageManager) {

    fun gameDirectory(): File = storage.launcherRoot()

    fun homeDirectory(): File = File(storage.launcherRoot(), HOME_DIR)

    fun tempDirectory(): File = File(storage.launcherRoot(), TMP_DIR)

    fun nativesDirectory(versionId: String): File =
        File(storage.versionDirectory(versionId), NATIVES_DIR)

    fun clientJarFile(versionId: String): File =
        File(storage.versionDirectory(versionId), "$versionId.jar")

    /** Creates every directory the game needs. Safe to call repeatedly. */
    fun prepare() {
        listOf(gameDirectory(), homeDirectory(), tempDirectory()).forEach { it.mkdirs() }
    }

    /** Process environment for the Java executable. */
    fun buildProcessEnvironment(javaHome: File): Map<String, String> {
        val javaLibPath = listOf("lib/server", "lib", "lib/jli", "bin")
            .map { File(javaHome, it).absolutePath }
            .joinToString(File.pathSeparator)
        return mapOf(
            "JAVA_HOME" to javaHome.absolutePath,
            "HOME" to homeDirectory().absolutePath,
            "TMPDIR" to tempDirectory().absolutePath,
            "LD_LIBRARY_PATH" to javaLibPath,
            "PATH" to "${File(javaHome, "bin").absolutePath}${File.pathSeparator}${System.getenv("PATH")}"
        )
    }

    private companion object {
        const val HOME_DIR = "home"
        const val TMP_DIR = "tmp"
        const val NATIVES_DIR = "natives"
    }
}