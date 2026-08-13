package com.lumocraft.app.domain.performance

/**
 * Detects the device's hardware capabilities once; the result is cached
 * so repeated calls never rescan system properties or memory info.
 * Every performance decision (JVM profile, download concurrency,
 * verification depth) starts from this single detection.
 */
interface DeviceProfiler {

    /** Detects (first call) or returns the cached [DeviceProfile]. */
    fun detect(): DeviceProfile
}