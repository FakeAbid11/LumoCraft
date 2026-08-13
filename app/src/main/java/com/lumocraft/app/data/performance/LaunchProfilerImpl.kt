package com.lumocraft.app.data.performance

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.performance.LaunchHistory
import com.lumocraft.app.domain.performance.LaunchProfiler
import com.lumocraft.app.domain.performance.LaunchTimings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * [LaunchProfiler] persisted under `<launcher>/cache/launch_history.json`
 * (capped at [AppConfig.LAUNCH_HISTORY_LIMIT] entries). The full entry
 * list is kept in memory after the first disk read; writes are
 * serialized and happen on the IO dispatcher.
 */
class LaunchProfilerImpl(private val storage: StorageManager) : LaunchProfiler {

    private val mutex = Mutex()

    @Volatile
    private var entries: List<LaunchTimings>? = null

    private fun file(): File =
        File(storage.launcherRoot(), "${AppConfig.CACHE_DIRECTORY_NAME}/${AppConfig.LAUNCH_HISTORY_FILE}")

    override suspend fun summary(): LaunchHistory = withContext(Dispatchers.IO) {
        buildHistory(readEntries())
    }

    override suspend fun record(timings: LaunchTimings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = (readEntries() + timings).takeLast(AppConfig.LAUNCH_HISTORY_LIMIT)
            persist(updated)
            entries = updated
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            entries = null
            file().delete()
        }
    }

    private fun readEntries(): List<LaunchTimings> {
        entries?.let { return it }
        val loaded = readFromDisk()
        entries = loaded
        return loaded
    }

    private fun readFromDisk(): List<LaunchTimings> {
        val file = file()
        if (!file.isFile) return emptyList()
        return runCatching {
            JSONObject(file.readText()).optJSONArray(KEY_ENTRIES)?.toTimings() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun buildHistory(entries: List<LaunchTimings>): LaunchHistory =
        LaunchHistory(
            launches = entries.size,
            lastLaunch = entries.lastOrNull(),
            fastestLaunch = entries.filter { it.success }.minByOrNull { it.totalMs }
        )

    private fun persist(entries: List<LaunchTimings>) {
        val array = JSONArray()
        for (timings in entries) {
            array.put(
                JSONObject()
                    .put("validationMs", timings.validationMs)
                    .put("classpathMs", timings.classpathMs)
                    .put("jvmStartMs", timings.jvmStartMs)
                    .put("totalMs", timings.totalMs)
                    .put("cachedValidation", timings.cachedValidation)
                    .put("cacheHits", timings.cacheHits)
                    .put("cacheMisses", timings.cacheMisses)
                    .put("success", timings.success)
                    .put("startedAt", timings.startedAt)
            )
        }
        file().parentFile?.mkdirs()
        file().writeText(JSONObject().put(KEY_ENTRIES, array).toString())
    }

    private fun JSONArray.toTimings(): List<LaunchTimings> = buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            add(
                LaunchTimings(
                    validationMs = obj.optLong("validationMs", 0),
                    classpathMs = obj.optLong("classpathMs", 0),
                    jvmStartMs = obj.optLong("jvmStartMs", 0),
                    totalMs = obj.optLong("totalMs", 0),
                    cachedValidation = obj.optBoolean("cachedValidation", false),
                    cacheHits = obj.optInt("cacheHits", 0),
                    cacheMisses = obj.optInt("cacheMisses", 0),
                    success = obj.optBoolean("success", false),
                    startedAt = obj.optLong("startedAt", 0)
                )
            )
        }
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}