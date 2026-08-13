package com.lumocraft.app.domain.performance

/** Current state of the byte buffer pool. */
data class PoolStats(
    val buffers: Int = 0,
    val bytes: Long = 0,
    val maxBytes: Long = 0
)

/**
 * Reduces allocations for hot file I/O: byte arrays are pooled and
 * reused instead of allocated per operation, and the pool is emptied
 * after each launch so RAM returns to the game process.
 *
 * Optimized for Android: the pool is size-bounded and buffers above
 * the maximum size are never pooled.
 */
interface MemoryOptimizer {

    /** Returns a buffer of at least [minSize] bytes (pooled or new). */
    fun acquireBuffer(minSize: Int): ByteArray

    /** Returns a buffer to the pool (ignored when oversized/full). */
    fun releaseBuffer(buffer: ByteArray)

    /** Releases pooled memory after a launch finishes. */
    fun cleanupAfterLaunch()

    fun poolStats(): PoolStats
}