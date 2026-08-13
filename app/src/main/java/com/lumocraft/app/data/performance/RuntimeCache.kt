package com.lumocraft.app.data.performance

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.runtime.RuntimeInfo
import java.io.File

/**
 * Runtime validation cache: remembers the last successfully verified
 * runtime (id + path + release checksum). Re-verifying an unchanged
 * runtime is skipped entirely within the validity window, so the Home
 * readiness check and launch validation avoid repeated binary/metadata
 * scans.
 */
class RuntimeCache(private val storage: StorageManager) {

    @Volatile
    private var cached: CachedRuntime? = null

    /** True when [runtime] was validated recently and is unchanged. */
    fun isFresh(runtime: RuntimeInfo, now: Long = System.currentTimeMillis()): Boolean {
        val entry = cached ?: return false
        return entry.id == runtime.id &&
            entry.path == runtime.path &&
            entry.checksum == runtime.checksum &&
            now - entry.validatedAt < AppConfig.RUNTIME_CACHE_VALIDITY_MS
    }

    fun markValidated(runtime: RuntimeInfo, now: Long = System.currentTimeMillis()) {
        cached = CachedRuntime(runtime.id, runtime.path, runtime.checksum, now)
    }

    fun invalidate() {
        cached = null
    }

    private data class CachedRuntime(
        val id: String,
        val path: String,
        val checksum: String?,
        val validatedAt: Long
    )
}