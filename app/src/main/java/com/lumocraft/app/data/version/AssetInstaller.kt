package com.lumocraft.app.data.version

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.storage.StorageManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Parsed asset index plus the on-disk reference used by the pipeline. */
internal data class AssetIndexData(
    val indexId: String,
    val objects: JSONObject,
    val totalSize: Long,
    val objectCount: Int
)

private data class AssetEntry(val hash: String, val size: Long)

/**
 * Downloads the asset index and every asset object into
 * `<launcher>/assets/`, exactly like the official launcher:
 *
 * - index          -> assets/indexes/<id>.json       (verified by SHA-1)
 * - objects        -> assets/objects/<hash[0..2]>/<hash>
 * - skip existing objects whose size matches the index (their file name is
 *   their hash, so a size match is as good as a hash match)
 * - hash verified before rename, parallel with a concurrency limit
 */
class AssetInstaller(
    private val storage: StorageManager,
    private val downloader: Downloader,
    private val concurrency: Int = AppConfig.DOWNLOAD_CONCURRENCY,
) {

    suspend fun downloadIndex(
        json: JSONObject,
        onProgress: StageListener,
    ): Result<AssetIndexData> = withContext(Dispatchers.IO) {
        try {
            val ref = VersionJson.assetIndex(json)
            if (ref == null) {
                onProgress(1f, 0, 0, 0, 0)
                return@withContext Result.success(AssetIndexData("", JSONObject(), 0, 0))
            }
            val destination = storage.assetIndexFile(ref.id)
            val alreadyValid = destination.isFile &&
                (ref.sha1 == null || HashUtils.sha1(destination) == ref.sha1)
            val tracker = DownloadTracker(1, ref.size ?: 0L, onProgress)
            if (!alreadyValid) {
                var lastFraction = 0f
                val result = downloader.downloadVerified(
                    url = ref.url,
                    destination = destination,
                    expectedSha1 = ref.sha1,
                    expectedSize = ref.size
                ) { fraction ->
                    fraction?.let { f ->
                        val delta = ((f - lastFraction) * (ref.size ?: 0L)).toLong()
                        lastFraction = f
                        tracker.addBytes(delta)
                    }
                }
                if (result.isFailure) {
                    tracker.flush()
                    return@withContext result.map { AssetIndexData("", JSONObject(), 0, 0) }
                }
                tracker.countDone(0)
            }
            val objects = JSONObject(destination.readText())
                .optJSONObject("objects") ?: JSONObject()
            var totalSize = 0L
            objects.keys().forEach { key ->
                totalSize += objects.optJSONObject(key).optLong("size", 0L)
            }
            Result.success(
                AssetIndexData(ref.id, objects, totalSize, objects.length())
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads all asset objects from the parsed index.
     * @param forceHashes object hashes that must be redownloaded (repair mode).
     */
    suspend fun downloadObjects(
        data: AssetIndexData,
        forceHashes: Set<String> = emptySet(),
        onProgress: StageListener,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val objects = data.objects
            if (objects.length() == 0) {
                onProgress(1f, 0, 0, 0, 0)
                return@withContext Result.success(Unit)
            }
            val needed = buildList {
                objects.keys().forEach { key ->
                    val obj = objects.optJSONObject(key) ?: return@forEach
                    val hash = obj.optString("hash")
                    val size = obj.optLong("size", 0L)
                    if (hash.isEmpty()) return@forEach
                    if (hash in forceHashes || needsDownload(hash, size)) {
                        add(AssetEntry(hash, size))
                    }
                }
            }
            if (needed.isEmpty()) {
                onProgress(1f, data.objectCount, 0, 0, 0)
                return@withContext Result.success(Unit)
            }
            val tracker = DownloadTracker(needed.size, data.totalSize, onProgress)
            coroutineScope {
                needed.chunked(concurrency).forEach { chunk ->
                    chunk.map { entry -> async { downloadObject(entry, tracker) } }
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

    private fun needsDownload(hash: String, size: Long): Boolean {
        val file = storage.objectFile(hash)
        return !file.isFile || file.length() != size
    }

    private suspend fun downloadObject(entry: AssetEntry, tracker: DownloadTracker) {
        val destination = storage.objectFile(entry.hash)
        val temp = File(destination.parentFile, "${entry.hash}.part")
        var lastFraction = 0f
        val url = AppConfig.ASSETS_BASE_URL + entry.hash.take(2) + "/" + entry.hash
        val result = downloader.download(url, temp) { fraction ->
            fraction?.let { f ->
                val delta = ((f - lastFraction) * entry.size).toLong()
                lastFraction = f
                tracker.addBytes(delta)
            }
        }
        if (result.isFailure) {
            throw result.exceptionOrNull()
                ?: IOException("Asset download failed: ${entry.hash}")
        }
        val file = result.getOrThrow()
        if (HashUtils.sha1(file) != entry.hash) {
            file.delete()
            throw IOException("Asset hash mismatch: ${entry.hash}")
        }
        file.renameTo(destination)
        tracker.countDone(entry.size)
    }
}