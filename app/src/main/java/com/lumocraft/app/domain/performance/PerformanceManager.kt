package com.lumocraft.app.domain.performance

import com.lumocraft.app.domain.runtime.JvmConfiguration

/**
 * Single entry point for launcher-side optimization. Later phases
 * (Fabric, Forge, Sodium, shader loading) consume [deviceProfile] and
 * [effectiveJvmProfile] without touching this contract.
 *
 * Responsibilities: device profiling, JVM profile selection (automatic
 * from the device, manually overridable), the launch cache, smart
 * verification, launch history and the memory optimizer.
 */
interface PerformanceManager {

    /** Cached hardware profile of this device. */
    fun deviceProfile(): DeviceProfile

    /** Manual override, or null when the device-derived profile is used. */
    fun jvmProfileOverride(): JvmProfile?

    /** Sets/clears the manual override (null = automatic). */
    fun setJvmProfileOverride(profile: JvmProfile?)

    /** The profile a launch uses: override, or device-derived. */
    fun effectiveJvmProfile(): JvmProfile

    /** The recommended JVM configuration for this device. */
    fun recommendedJvmConfiguration(): JvmConfiguration

    /**
     * Resolves the effective JVM configuration for a launch:
     * manual override wins, otherwise the device recommendation is used
     * when the user left the defaults, and everything is clamped to the
     * device's recommended heap ceiling.
     */
    fun resolveJvmConfiguration(base: JvmConfiguration): JvmConfiguration

    fun cache(): CacheManager

    fun verifier(): SmartVerifier

    fun profiler(): LaunchProfiler

    fun memory(): MemoryOptimizer

    /** Bandwidth-aware download concurrency for install stages. */
    fun downloadConcurrency(): Int

    /** Drops the whole launch cache. */
    suspend fun clearCache(): Result<Unit>

    /** Drops and repopulates the cache from the installed versions. */
    suspend fun rebuildCache(): Result<Unit>

    /** Clears the override, counters and history; reapplies the recommendation. */
    suspend fun resetPerformanceSettings()
}