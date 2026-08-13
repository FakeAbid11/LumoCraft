package com.lumocraft.app.data.launch

import com.lumocraft.app.data.performance.Fingerprints
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.loader.LoaderLaunchConfiguration
import com.lumocraft.app.domain.performance.CacheManager
import com.lumocraft.app.domain.performance.LaunchCacheEntry
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
    val mainClass: String,
    /** True when served from the launch cache without a disk scan. */
    val fromCache: Boolean = false
)

/**
 * Resolves the full classpath for a version: its own libraries followed
 * by inherited parent versions' libraries (when present), then the client
 * jar, in manifest order with duplicates removed. Every file must exist;
 * missing files fail with a detailed message.
 *
 * A [LoaderLaunchConfiguration] (from the generic loader interface, e.g.
 * Fabric) appends its own libraries and replaces the vanilla client jar
 * with the loader's patched jar; vanilla launches pass the default.
 *
 * Results are cached in the [CacheManager] keyed by the version JSON
 * fingerprint: an unchanged version resolves instantly without touching
 * the disk. Cache rows never rebuild unchanged data.
 */
class ClasspathBuilder(
    private val storage: StorageManager,
    private val cache: CacheManager? = null,
) {

    suspend fun build(
        versionId: String,
        loaderConfig: LoaderLaunchConfiguration = LoaderLaunchConfiguration(),
    ): Result<BuiltClasspath> = withContext(Dispatchers.IO) {
        val chain = loadChain(versionId)
            ?: return@withContext Result.failure(
                LaunchException("Version JSON for '$versionId' is missing or unreadable")
            )

        val fingerprint = Fingerprints.of(chain)
        cache?.let { c ->
            val entry = c.getEntry(versionId)
            if (entry?.classpath != null &&
                entry.versionJsonFingerprint == fingerprint &&
                entry.mainClass != null
            ) {
                c.recordHit()
                return@withContext Result.success(
                    BuiltClasspath(
                        classpath = entry.classpath,
                        libraryFiles = entry.libraryFiles.map(::File),
                        libraryRefs = emptyList(),
                        mainClass = entry.mainClass,
                        fromCache = true
                    )
                )
            }
        }

        val ordered = linkedMapOf<String, File>()
        val refs = mutableListOf<LibraryRef>()
        for (file in chain) {
            val json = JSONObject(file.readText())
            for (ref in VersionJson.libraries(json)) {
                refs += ref
                ordered.putIfAbsent(ref.path, storage.libraryFile(ref.path))
            }
        }
        // Loader libraries (Fabric maven artifacts) are appended on top;
        // files already resolved through the version chain are skipped.
        val chainPaths = ordered.values.map { it.absolutePath }.toSet()
        loaderConfig.libraries.forEach { lib ->
            if (lib.absolutePath !in chainPaths) {
                ordered[lib.absolutePath] = lib
            }
        }

        val missing = ordered.filterValues { !it.isFile }.keys.toList()
        if (missing.isNotEmpty()) {
            return@withContext Result.failure(
                LaunchException(missingLibrariesMessage(missing))
            )
        }

        val clientJar = loaderConfig.clientJar ?: clientJarFile(versionId)
        if (!clientJar.isFile) {
            return@withContext Result.failure(
                LaunchException("Client jar not found: ${clientJar.absolutePath}")
            )
        }

        val mainClass = loaderConfig.mainClass
            ?: chain.firstNotNullOfOrNull { file ->
                runCatching { JSONObject(file.readText()) }.getOrNull()
                    ?.optString("mainClass")?.takeIf { it.isNotEmpty() }
            } ?: return@withContext Result.failure(
            LaunchException("No mainClass declared for '$versionId'")
        )

        val entries = ordered.values
            .filterNot { it == clientJar }
            .toMutableList()
            .also { it.add(clientJar) }
        val built = BuiltClasspath(
            classpath = entries.joinToString(File.pathSeparator) { it.absolutePath },
            libraryFiles = entries,
            libraryRefs = refs,
            mainClass = mainClass
        )

        cache?.let { c ->
            val base = c.getEntry(versionId) ?: LaunchCacheEntry(versionId)
            c.putEntry(
                base.copy(
                    versionJsonFingerprint = fingerprint,
                    classpath = built.classpath,
                    libraryFiles = entries.map { it.absolutePath },
                    mainClass = built.mainClass
                )
            )
            c.recordMiss()
        }
        Result.success(built)
    }

    /** Leaf-first chain: the version itself, then each inherited parent. */
    private fun loadChain(versionId: String): List<File>? {
        val result = mutableListOf<File>()
        var current = versionId
        val seen = mutableSetOf<String>()
        while (true) {
            if (!seen.add(current)) return null
            val file = storage.versionJsonFile(current)
            if (!file.isFile) return null
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
            result.add(file)
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