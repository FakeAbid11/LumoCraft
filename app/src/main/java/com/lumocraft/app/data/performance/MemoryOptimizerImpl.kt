package com.lumocraft.app.data.performance

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.domain.performance.MemoryOptimizer
import com.lumocraft.app.domain.performance.PoolStats

/**
 * Bounded byte-buffer pool for hot file I/O (hashing, downloads). Sized
 * for Android: at most [AppConfig.BUFFER_POOL_MAX_BUFFERS] buffers and
 * [AppConfig.BUFFER_POOL_MAX_BYTES] pooled; oversized buffers are never
 * pooled. [cleanupAfterLaunch] empties the pool so RAM returns to the
 * game process after each launch.
 */
class MemoryOptimizerImpl(
    private val maxBuffers: Int = AppConfig.BUFFER_POOL_MAX_BUFFERS,
    private val maxPoolBytes: Long = AppConfig.BUFFER_POOL_MAX_BYTES,
    private val maxBufferSize: Int = AppConfig.BUFFER_POOL_MAX_BUFFER_SIZE,
) : MemoryOptimizer {

    private val pool = ArrayDeque<ByteArray>()
    private var pooledBytes = 0L

    override fun acquireBuffer(minSize: Int): ByteArray {
        synchronized(this) {
            val index = pool.indexOfFirst { it.size >= minSize }
            if (index >= 0) {
                val buffer = pool.removeAt(index)
                pooledBytes -= buffer.size
                return buffer
            }
        }
        return ByteArray(minSize.coerceAtLeast(MIN_BUFFER_SIZE))
    }

    override fun releaseBuffer(buffer: ByteArray) {
        if (buffer.size > maxBufferSize) return
        synchronized(this) {
            if (pool.size >= maxBuffers || pooledBytes + buffer.size > maxPoolBytes) return
            pool.addLast(buffer)
            pooledBytes += buffer.size
        }
    }

    override fun cleanupAfterLaunch() {
        synchronized(this) {
            pool.clear()
            pooledBytes = 0
        }
    }

    override fun poolStats(): PoolStats = synchronized(this) {
        PoolStats(buffers = pool.size, bytes = pooledBytes, maxBytes = maxPoolBytes)
    }

    private companion object {
        const val MIN_BUFFER_SIZE = 1024
    }
}