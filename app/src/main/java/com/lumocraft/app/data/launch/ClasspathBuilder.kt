package com.lumocraft.app.data.launch

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.version.LibraryRef
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Result of classpath resolution: the join-path plus its parts. */
data class BuiltClasspath(
    val classpath: String,
    val libraryFiles: List<File>,
    val libraryRefs: List<LibraryRef>,
    val mainClass: String
)

/**
 * Resolves the full classpath for a version: its own libraries followed
 * by inherited parent versions' libraries (when present), then the client
 * jar, in manifest order with duplicates removed. Every file must exist;
 * missing files fail with a detailed message.
 */
class ClasspathBuilder(private val storage: StorageManager) {

    suspend fun build(versionId: String): Result<BuiltClasspath> = withContext(Dispatchers.IO) {
        val chain = loadChain(versionId)
            ?: return@withContext Result.failure(
                LaunchException("Version JSON for '$versionId' is missing or unreadable")
            )

        val ordered = linkedMapOf<String, File>()
        val refs = mutableListOf<LibraryRef>()
        for (json in chain) {
            for (ref in VersionJson.libraries(json)) {
                refs += ref
                ordered.putIfAbsent(ref.path, storage.libraryFile(ref.path))
            }
        }

        val missing = ordered.filterValues { !it.isFile }.keys.toList()
        if (missing.isNotEmpty()) {
            return@withContext Result.failure(
                LaunchException(missingLibrariesMessage(missing))
            )
        }

        val clientJar = clientJarFile(versionId)
        if (!clientJar.isFile) {
            return@withContext Result.failure(
                LaunchException("Client jar not found: ${clientJar.absolutePath}")
            )
        }

        val mainClass = chain.firstNotNullOfOrNull { json ->
            json.optString("mainClass").takeIf { it.isNotEmpty() }
        } ?: return@withContext Result.failure(
            LaunchException("No mainClass declared for '$versionId'")
        )

        val entries = ordered.values.toMutableList().also { it.add(clientJar) }
        Result.success(
            BuiltClasspath(
                classpath = entries.joinToString(File.pathSeparator) { it.absolutePath },
                libraryFiles = entries,
                libraryRefs = refs,
                mainClass = mainClass
            )
        )
    }

    /** Leaf-first chain: the version itself, then each inherited parent. */
    private fun loadChain(versionId: String): List<JSONObject>? {
        val result = mutableListOf<JSONObject>()
        var current = versionId
        val seen = mutableSetOf<String>()
        while (true) {
            if (!seen.add(current)) return null
            val file = storage.versionJsonFile(current)
            if (!file.isFile) return null
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
            result.add(json)
            val parent = json.optString("inheritsFrom").takeIf { it.isNotEmpty() } ?: break
            current = parent
        }
        return result
    }

    private fun clientJarFile(versionId: String): File =
        File(storage.versionDirectory(versionId), "$versionId.jar")

    private fun missingLibrariesMessage(missing: List<String>): String {
        val shown = missing.take(MAX_REPORTED).joinToString(", ")
        val extra = missing.size - MAX_REPORTED
        return "Missing libraries: $shown" +
            if (extra > 0) " (+$extra more)" else ""
    }

    private companion object {
        const val MAX_REPORTED = 10
    }
}