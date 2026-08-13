package com.lumocraft.app.data.version

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tenant-provided progress callback for a download stage.
 * [stageFraction] is bytes-based when a total size is known, otherwise
 * file-count based; null when the stage is indeterminate.
 */
typealias StageListener = suspend (
    stageFraction: Float?,
    filesCompleted: Int,
    filesRemaining: Int,
    downloadedBytes: Long,
    totalBytes: Long
) -> Unit

/**
 * Thread-safe accumulator for parallel download stages. Emissions are
 * throttled to ~10 per second and serialized, so concurrent coroutines can
 * report progress without interleaving state.
 */
internal class DownloadTracker(
    private val totalFiles: Int,
    private val totalBytes: Long,
    private val listener: StageListener,
    private val throttleNanos: Long = 100_000_000L,
) {
    private val completed = AtomicInteger(0)
    private val bytesDownloaded = AtomicLong(0)
    private val emitMutex = Mutex()
    private var lastEmitNanos = 0L

    /** Records one finished file (with its downloaded bytes). */
    suspend fun countDone(additionalBytes: Long = 0L) {
        completed.incrementAndGet()
        addBytes(additionalBytes)
    }

    /** Accumulates downloaded bytes and emits a throttled snapshot. */
    suspend fun addBytes(delta: Long) {
        if (delta > 0) {
            bytesDownloaded.addAndGet(delta)
        }
        maybeEmit()
    }

    /** Forces a final emission (e.g. end of a stage). */
    suspend fun flush() = emit()

    private suspend fun maybeEmit() {
        val now = System.nanoTime()
        if (now - lastEmitNanos < throttleNanos) return
        lastEmitNanos = now
        emit()
    }

    private suspend fun emit() = emitMutex.withLock {
        val done = completed.get()
        val bytes = bytesDownloaded.get()
        listener(
            stageFraction = when {
                totalBytes > 0 -> bytes.toFloat() / totalBytes
                totalFiles > 0 -> done.toFloat() / totalFiles
                else -> null
            },
            filesCompleted = done,
            filesRemaining = totalFiles - done,
            downloadedBytes = bytes,
            totalBytes = totalBytes
        )
    }
}