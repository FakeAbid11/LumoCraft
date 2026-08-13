package com.lumocraft.app.data.performance

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.performance.CacheManager
import com.lumocraft.app.domain.performance.CacheStats
import com.lumocraft.app.domain.performance.LaunchCacheEntry
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * [CacheManager] persisted as a single JSON document under
 * `<launcher>/cache/launch_cache.json`. An in-memory snapshot serves all
 * reads (no disk I/O on the hot paths); mutations are serialized and
 * written back on the IO dispatcher.
 */
class JsonCacheManager(private val storage: StorageManager) : CacheManager {

    private val mutex = Mutex()

    @Volatile
    private var snapshot: Snapshot? = null

    private fun file(): File =
        File(storage.launcherRoot(), "${AppConfig.CACHE_DIRECTORY_NAME}/${AppConfig.LAUNCH_CACHE_FILE}")

    override suspend fun getEntry(versionId: String): LaunchCacheEntry? =
        withContext(Dispatchers.IO) {
            snapshot()?.entries?.get(versionId)
        }

    override suspend fun putEntry(entry: LaunchCacheEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = snapshot()
            val entries = LinkedHashMap(current?.entries ?: emptyMap()).apply {
                remove(entry.versionId)
                put(entry.versionId, entry)
                // Keep only the most recent versions.
                while (size > AppConfig.CACHE_MAX_ENTRIES) {
                    remove(keys.first())
                }
            }
            snapshot = Snapshot(entries, current?.hits ?: 0, current?.misses ?: 0)
            persist(entries, snapshot!!.hits, snapshot!!.misses)
        }
    }

    override suspend fun removeEntry(versionId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = snapshot() ?: return@withLock
            if (current.entries.containsKey(versionId)) {
                val entries = LinkedHashMap(current.entries).apply { remove(versionId) }
                snapshot = Snapshot(entries, current.hits, current.misses)
                persist(entries, current.hits, current.misses)
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            snapshot = Snapshot(emptyMap(), 0, 0)
            persist(emptyMap(), 0, 0)
        }
    }

    override suspend fun stats(): CacheStats = withContext(Dispatchers.IO) {
        val snap = snapshot()
        val bytes = if (snap == null) file().takeIf { it.isFile }?.length() ?: 0 else {
            runCatching { file().length() }.getOrDefault(0)
        }
        CacheStats(
            itemCount = snap?.entries?.size ?: 0,
            sizeBytes = bytes,
            hits = snap?.hits ?: 0,
            misses = snap?.misses ?: 0
        )
    }

    override fun cacheDirectory(): File =
        File(storage.launcherRoot(), AppConfig.CACHE_DIRECTORY_NAME)

    override suspend fun hits(): Long = withContext(Dispatchers.IO) { snapshot()?.hits ?: 0 }

    override suspend fun misses(): Long = withContext(Dispatchers.IO) { snapshot()?.misses ?: 0 }

    override suspend fun recordHit() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = snapshot()
            snapshot = Snapshot(current?.entries ?: emptyMap(), (current?.hits ?: 0) + 1, current?.misses ?: 0)
            persist(snapshot!!.entries, snapshot!!.hits, snapshot!!.misses)
        }
    }

    override suspend fun recordMiss() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = snapshot()
            snapshot = Snapshot(current?.entries ?: emptyMap(), current?.hits ?: 0, (current?.misses ?: 0) + 1)
            persist(snapshot!!.entries, snapshot!!.hits, snapshot!!.misses)
        }
    }

    override suspend fun resetCounters() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = snapshot()
            snapshot = Snapshot(current?.entries ?: emptyMap(), 0, 0)
            persist(current?.entries ?: emptyMap(), 0, 0)
        }
    }

    private fun snapshot(): Snapshot? {
        snapshot?.let { return it }
        return readFromDisk()
    }

    private fun readFromDisk(): Snapshot? {
        val file = file()
        if (!file.isFile) return null
        val result = runCatching {
            val root = JSONObject(file.readText())
            val entries = LinkedHashMap<String, LaunchCacheEntry>()
            val array = root.optJSONArray(KEY_ENTRIES) ?: JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val versionId = obj.optString("versionId")
                if (versionId.isEmpty()) continue
                entries[versionId] = parseEntry(versionId, obj)
            }
            Snapshot(
                entries = entries,
                hits = root.optLong(KEY_HITS, 0),
                misses = root.optLong(KEY_MISSES, 0)
            )
        }.getOrNull()
        if (result != null) snapshot = result
        return result
    }

    private fun parseEntry(versionId: String, obj: JSONObject): LaunchCacheEntry =
        LaunchCacheEntry(
            versionId = versionId,
            versionJsonFingerprint = obj.optString("versionJsonFingerprint").takeIf { it.isNotEmpty() },
            classpath = obj.optString("classpath").takeIf { it.isNotEmpty() },
            libraryFiles = obj.optJSONArray("libraryFiles")?.toStringList() ?: emptyList(),
            mainClass = obj.optString("mainClass").takeIf { it.isNotEmpty() },
            verifiedLibraries = obj.optJSONArray("verifiedLibraries")?.toStringList() ?: emptyList(),
            verifiedAssets = obj.optInt("verifiedAssets", 0),
            assetIndexFingerprint = obj.optString("assetIndexFingerprint").takeIf { it.isNotEmpty() },
            launchArgumentsFingerprint = obj.optString("launchArgumentsFingerprint")
                .takeIf { it.isNotEmpty() },
            launchArgumentsJson = obj.optString("launchArgumentsJson").takeIf { it.isNotEmpty() },
            runtimeValidated = obj.optBoolean("runtimeValidated", false),
            lastVerifiedAt = obj.optLong("lastVerifiedAt", 0)
        )

    private fun persist(entries: Map<String, LaunchCacheEntry>, hits: Long, misses: Long) {
        val array = JSONArray()
        for (entry in entries.values) {
            array.put(
                JSONObject()
                    .put("versionId", entry.versionId)
                    .put("versionJsonFingerprint", entry.versionJsonFingerprint ?: "")
                    .put("classpath", entry.classpath ?: "")
                    .put("libraryFiles", JSONArray(entry.libraryFiles))
                    .put("mainClass", entry.mainClass ?: "")
                    .put("verifiedLibraries", JSONArray(entry.verifiedLibraries))
                    .put("verifiedAssets", entry.verifiedAssets)
                    .put("assetIndexFingerprint", entry.assetIndexFingerprint ?: "")
                    .put("launchArgumentsFingerprint", entry.launchArgumentsFingerprint ?: "")
                    .put("launchArgumentsJson", entry.launchArgumentsJson ?: "")
                    .put("runtimeValidated", entry.runtimeValidated)
                    .put("lastVerifiedAt", entry.lastVerifiedAt)
            )
        }
        val root = JSONObject()
            .put(KEY_HITS, hits)
            .put(KEY_MISSES, misses)
            .put(KEY_ENTRIES, array)
        file().parentFile?.mkdirs()
        file().writeText(root.toString())
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(optString(i))
    }

    private data class Snapshot(
        val entries: Map<String, LaunchCacheEntry>,
        val hits: Long,
        val misses: Long
    )

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val KEY_HITS = "hits"
        const val KEY_MISSES = "misses"
    }
}