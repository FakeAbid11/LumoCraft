package com.lumocraft.app.data.network

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.performance.DeviceTier

/**
 * Adaptive download concurrency. The base concurrency comes from the
 * device tier (low-end devices get fewer parallel connections so memory
 * stays flat); when the measured bandwidth is too low to feed all
 * connections, concurrency is shed to avoid fragmentation. This never
 * increases memory: fewer connections means fewer in-flight buffers.
 */
class DownloadScheduler(
    private val deviceProfile: () -> DeviceProfile,
    private val throughput: ThroughputTracker,
) {

    fun concurrency(): Int {
        val base = baseConcurrency()
        if (base <= MIN_CONCURRENCY) return base
        val estimated = throughput.estimatedBytesPerSecond()
        if (estimated <= 0L) return base
        // Each connection needs ~128 KB/s to be worth keeping.
        return if (estimated / base < AppConfig.DOWNLOAD_MIN_CONNECTION_THROUGHPUT_BPS) {
            base - 1
        } else {
            base
        }
    }

    private fun baseConcurrency(): Int = when (deviceProfile().tier) {
        DeviceTier.LOW -> 2
        DeviceTier.MEDIUM -> 3
        DeviceTier.HIGH -> AppConfig.DOWNLOAD_CONCURRENCY
    }

    private companion object {
        const val MIN_CONCURRENCY = 2
    }
}