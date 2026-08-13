package com.lumocraft.app.data.performance

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.storage.StorageManager
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cached file checksums keyed by (path, size, lastModified): a lookup
 * only matches when all three agree, so the cached SHA-1 is never used
 * for a changed file. This is what makes repeated verifications
 * hash-free — only files that changed are ever rehashed.
 *
 * Entries are capped; when the cap is hit the whole cache is rebuilt
 * (fresh files are cheap to rehash once).
 */
class ChecksumCache(private val storage: StorageManager) {

    private val mutex = Mutex()

    @Volatile
    private var entries: Map<String, CachedChecksum>? = null

    private fun file(): File =
        File(storage.launcherRoot(), "${AppConfig.CACHE_DIRECTORY_NAME}/${AppConfig.CHECKSUM_CACHE_FILE}")

    /** Returns the cached SHA-1 when [path] still has [size] and [modifiedAt]. */
    suspend fun lookup(path: String, size: Long, modifiedAt: Long): String? =
        withContext(Dispatchers.IO) {
            val cached = entries()?.get(path) ?: return@withContext null
            if (cached.size == size && cached.modifiedAt == modifiedAt) cached.sha1 else null
        }

    suspend fun store(path: String, size: Long, modifiedAt: Long, sha1: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = LinkedHashMap(entries() ?: emptyMap())
                current[path] = CachedChecksum(size, modifiedAt, sha1)
                if (current.size > AppConfig.CHECKSUM_CACHE_LIMIT) {
                    current.clear()
                    current[path] = CachedChecksum(size, modifiedAt, sha1)
                }
                entries = current
                persist(current)
            }
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            entries = emptyMap()
            file().delete()
        }
    }

    private fun entries(): Map<String, CachedChecksum>? {
        entries?.let { return it }
        val file = file()
        if (!file.isFile) return emptyMap()
        val loaded = runCatching {
            val root = JSONObject(file.readText())
            buildMap {
                root.keys().forEach { path ->
                    val obj = root.optJSONObject(path) ?: return@forEach
                    put(
                        path,
                        CachedChecksum(
                            size = obj.optLong("size", -1),
                            modifiedAt = obj.optLong("mtime", -1),
                            sha1 = obj.optString("sha1")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
        entries = loaded
        return loaded
    }

    private fun persist(map: Map<String, CachedChecksum>) {
        val root = JSONObject()
        for ((path, cached) in map) {
            root.put(
                path,
                JSONObject()
                    .put("size", cached.size)
                    .put("mtime", cached.modifiedAt)
                    .put("sha1", cached.sha1)
            )
        }
        file().parentFile?.mkdirs()
        file().writeText(root.toString())
    }

    private data class CachedChecksum(val size: Long, val modifiedAt: Long, val sha1: String)
}