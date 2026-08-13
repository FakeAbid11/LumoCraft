package com.lumocraft.app.data.launch

import android.os.Build
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.version.LibraryRef
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pre-extracts native shared libraries from natives-classifier jars into
 * the version's natives directory. Multi-arch jars (LWJGL 3.3.x) keep
 * per-arch subdirectories (`linux/arm64/`); only the matching
 * architecture is extracted, flattened into the natives dir. Flat
 * single-arch jars are extracted as-is.
 *
 * LWJGL itself also extracts on demand; this gives JNA and other loaders
 * a stable, populated java.library.path. Already-present entries with the
 * expected size are skipped.
 */
class NativeExtractor(private val storage: StorageManager) {

    suspend fun extract(versionId: String, libraries: List<LibraryRef>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetDir = File(storage.versionDirectory(versionId), "natives")
                targetDir.mkdirs()
                for (lib in libraries) {
                    if (lib.classifier == null) continue
                    val jar = storage.libraryFile(lib.path)
                    if (!jar.isFile) continue
                    extractJar(jar, targetDir)
                }
            }
        }

    private fun extractJar(jar: File, targetDir: File) {
        ZipFile(jar).use { zip ->
            val entries = zip.entries().toList()
            val multiArch = entries.any { isArchEntry(it.name) }
            for (entry in entries) {
                if (entry.isDirectory || !isNativeFile(entry.name)) continue
                if (multiArch && !archMatches(entry.name)) continue
                val fileName = entry.name.substringAfterLast('/')
                val target = File(targetDir, fileName)
                if (target.isFile && target.length() == entry.size) continue
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    private fun isNativeFile(name: String): Boolean =
        !name.startsWith("META-INF") && NATIVE_EXTENSIONS.any { name.endsWith(it) }

    private fun isArchEntry(name: String): Boolean =
        ARCH_SUBDIRS.any { "/$it/" in name }

    /** Entries at the jar root of an arch subdir belong to one arch. */
    private fun archMatches(name: String): Boolean {
        val dir = name.substringBeforeLast('/', "").substringAfterLast('/')
        return when (dir) {
            "arm64" -> detectedArch == "arm64"
            "arm32" -> detectedArch == "arm32"
            "x86_64" -> detectedArch == "x86_64"
            else -> detectedArch == "x86_64"
        }
    }

    private val detectedArch: String = when {
        Build.SUPPORTED_ABIS.firstOrNull()?.contains("arm64") == true -> "arm64"
        Build.SUPPORTED_ABIS.firstOrNull()?.contains("v7a") == true -> "arm32"
        else -> "x86_64"
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        val NATIVE_EXTENSIONS = listOf(".so", ".dylib", ".dll", ".jnilib")
        val ARCH_SUBDIRS = listOf("arm64", "arm32", "x86_64")
    }
}