package com.lumocraft.app.data.network

import com.lumocraft.app.core.config.AppConfig
import java.util.ArrayDeque

/**
 * Sliding-window bandwidth estimator. Downloaders feed completed byte
 * counts; the scheduler reads the estimated bytes/second to adapt
 * concurrency. Samples outside the window decay away, so a burst of
 * speed does not inflate the estimate for long.
 */
class ThroughputTracker(
    private val windowMs: Long = AppConfig.THROUGHPUT_WINDOW_MS,
) {

    private val samples = ArrayDeque<Sample>()
    private val lock = Any()

    fun record(bytes: Long, elapsedMs: Long) {
        if (bytes <= 0 || elapsedMs <= 0) return
        val perSecond = bytes * 1000 / elapsedMs
        val now = System.nanoTime()
        synchronized(lock) {
            samples.addLast(Sample(now, perSecond))
            val cutoff = now - windowMs * 1_000_000L
            while (samples.isNotEmpty() && samples.first().timeNanos < cutoff) {
                samples.removeFirst()
            }
        }
    }

    /** Estimated bytes/second across the window, or 0 when unknown. */
    fun estimatedBytesPerSecond(): Long = synchronized(lock) {
        if (samples.isEmpty()) return 0L
        samples.sumOf { it.bytesPerSecond } / samples.size
    }

    fun reset() {
        synchronized(lock) { samples.clear() }
    }

    private data class Sample(val timeNanos: Long, val bytesPerSecond: Long)
}