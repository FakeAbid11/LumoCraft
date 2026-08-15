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

    /**
     * Process environment for the Java executable.
     *
     * [nativeLibraryDir], when supplied, is the APK's packaged native-library
     * directory holding the bundled PojavLauncher rendering natives; it is
     * prepended to `LD_LIBRARY_PATH` so the game JVM's LWJGL can `dlopen`
     * libpojavexec/gl4es. [rendererEnv] carries the gl4es/pojav renderer
     * variables (see [RendererEnvironment]) that those C libraries read.
     */
    fun buildProcessEnvironment(
        javaHome: File,
        nativeLibraryDir: File? = null,
        rendererEnv: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val javaLibPaths = listOf("lib/server", "lib", "lib/jli", "bin")
            .map { File(javaHome, it).absolutePath }
        // The rendering natives directory goes first so pojavexec/gl4es and
        // their transitive deps resolve ahead of the JDK's own libraries.
        val ldLibraryPath = (listOfNotNull(nativeLibraryDir?.absolutePath) + javaLibPaths)
            .joinToString(File.pathSeparator)
        return buildMap {
            put("JAVA_HOME", javaHome.absolutePath)
            put("HOME", homeDirectory().absolutePath)
            put("TMPDIR", tempDirectory().absolutePath)
            put("LD_LIBRARY_PATH", ldLibraryPath)
            put("PATH", "${File(javaHome, "bin").absolutePath}${File.pathSeparator}${System.getenv("PATH")}")
            // Renderer env last: it only adds LIBGL_*/POJAV_* keys and never
            // collides with the process paths above.
            putAll(rendererEnv)
        }
    }

    private companion object {
        const val HOME_DIR = "home"
        const val TMP_DIR = "tmp"
        const val NATIVES_DIR = "natives"
    }
}