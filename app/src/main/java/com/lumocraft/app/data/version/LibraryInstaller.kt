package com.lumocraft.app.data.version

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.version.LibraryRef
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Downloads the libraries listed in a version JSON into
 * `<launcher>/libraries/`, preserving Mojang's folder structure.
 *
 * - already existing files are skipped (by size; hash is the authority
 *   during download and in the later verification stage)
 * - each download is verified by SHA-1 and size before being renamed into
 *   place (atomic per file, no half-written files)
 * - downloads run in parallel with a fixed concurrency limit
 * - failures cancel the remaining batch and fail the whole stage
 */
class LibraryInstaller(
    private val storage: StorageManager,
    private val downloader: Downloader,
    private val concurrency: Int = AppConfig.DOWNLOAD_CONCURRENCY,
) {

    /**
     * @param forcePaths library paths that must be redownloaded even when
     * their files exist (repair mode).
     */
    suspend fun install(
        json: JSONObject,
        forcePaths: Set<String> = emptySet(),
        onProgress: StageListener,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val libraries = VersionJson.libraries(json)
            if (libraries.isEmpty()) {
                onProgress(1f, 0, 0, 0, 0)
                return@withContext Result.success(Unit)
            }
            val needed = libraries.filter { lib ->
                lib.path in forcePaths || needsDownload(lib)
            }
            if (needed.isEmpty()) {
                onProgress(1f, libraries.size, 0, 0, 0)
                return@withContext Result.success(Unit)
            }
            val tracker = DownloadTracker(
                totalFiles = needed.size,
                totalBytes = needed.sumOf { it.size ?: 0L },
                listener = onProgress
            )
            coroutineScope {
                needed.chunked(concurrency).forEach { chunk ->
                    chunk.map { lib -> async { downloadLibrary(lib, tracker) } }
                        .awaitAll()
                }
            }
            tracker.flush()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun needsDownload(lib: LibraryRef): Boolean {
        val file = storage.libraryFile(lib.path)
        return !file.isFile || (lib.size != null && file.length() != lib.size)
    }

    private suspend fun downloadLibrary(lib: LibraryRef, tracker: DownloadTracker) {
        val destination = storage.libraryFile(lib.path)
        val temp = File(destination.parentFile, ".${destination.name}.part")
        var lastFraction = 0f
        val result = downloader.download(lib.url, temp) { fraction ->
            fraction?.let { f ->
                val delta = ((f - lastFraction) * (lib.size ?: 0L)).toLong()
                lastFraction = f
                tracker.addBytes(delta)
            }
        }
        if (result.isFailure) {
            throw result.exceptionOrNull()
                ?: IOException("Library download failed: ${lib.path}")
        }
        val file = result.getOrThrow()
        if (lib.sha1 != null && HashUtils.sha1(file) != lib.sha1) {
            file.delete()
            throw IOException("SHA-1 mismatch: ${lib.path}")
        }
        if (lib.size != null && file.length() != lib.size) {
            file.delete()
            throw IOException("Size mismatch: ${lib.path}")
        }
        file.renameTo(destination)
        tracker.countDone(lib.size ?: file.length())
    }
}