package com.lumocraft.app.data.performance

import com.lumocraft.app.data.network.DownloadScheduler
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.performance.CacheManager
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.performance.DeviceProfiler
import com.lumocraft.app.domain.performance.JvmProfile
import com.lumocraft.app.domain.performance.LaunchProfiler
import com.lumocraft.app.domain.performance.MemoryOptimizer
import com.lumocraft.app.domain.performance.PerformanceManager
import com.lumocraft.app.domain.performance.SmartVerifier
import com.lumocraft.app.domain.runtime.JvmConfiguration
import com.lumocraft.app.domain.runtime.RuntimeRepository
import com.lumocraft.app.domain.version.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reference [PerformanceManager]: aggregates the device profiler, the
 * JVM profile selection, the launch cache, smart verification, launch
 * history and the memory optimizer behind one small contract. Later
 * phases (Fabric, Forge, Sodium, shaders) read [deviceProfile] and
 * [effectiveJvmProfile] from here without probing hardware themselves.
 */
class DefaultPerformanceManager(
    private val profiler: DeviceProfiler,
    private val preference: PerformancePreference,
    private val cache: CacheManager,
    private val verifier: SmartVerifier,
    private val launchProfiler: LaunchProfiler,
    private val memory: MemoryOptimizer,
    private val runtimeRepository: RuntimeRepository,
    private val storage: StorageManager,
    private val scheduler: DownloadScheduler,
) : PerformanceManager {

    override fun deviceProfile(): DeviceProfile = profiler.detect()

    override fun jvmProfileOverride(): JvmProfile? = preference.jvmProfileOverride()

    override fun setJvmProfileOverride(profile: JvmProfile?) {
        preference.setJvmProfileOverride(profile)
    }

    override fun effectiveJvmProfile(): JvmProfile =
        jvmProfileOverride() ?: JvmProfile.forDevice(deviceProfile())

    override fun recommendedJvmConfiguration(): JvmConfiguration =
        clampToDevice(JvmProfile.forDevice(deviceProfile()).template)

    override fun resolveJvmConfiguration(base: JvmConfiguration): JvmConfiguration {
        val override = jvmProfileOverride()
        return when {
            override != null -> clampToDevice(override.template)
            // Untouched defaults: adopt the device recommendation entirely.
            base == JvmConfiguration() -> clampToDevice(JvmProfile.forDevice(deviceProfile()).template)
            // User-tuned sliders: keep them, but cap the heap to the device ceiling.
            else -> clampToDevice(base)
        }
    }

    override fun cache(): CacheManager = cache

    override fun verifier(): SmartVerifier = verifier

    override fun profiler(): LaunchProfiler = launchProfiler

    override fun memory(): MemoryOptimizer = memory

    override fun downloadConcurrency(): Int = scheduler.concurrency()

    override suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cache.clear()
            verifier.invalidateAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rebuildCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cache.clear()
            verifier.invalidateAll()
            // Repopulate every installed version: one selective pass each,
            // priming both the launch cache and the checksum cache.
            val installed = storage.readInstallStates()
                .filterValues { it == InstallState.INSTALLED }
                .keys
            for (versionId in installed) {
                verifier.verify(versionId, SmartVerifier.Mode.LAUNCH)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetPerformanceSettings() {
        preference.setJvmProfileOverride(null)
        runtimeRepository.saveJvmConfiguration(recommendedJvmConfiguration())
        cache.resetCounters()
        launchProfiler.clear()
        memory.cleanupAfterLaunch()
    }

    private fun clampToDevice(config: JvmConfiguration): JvmConfiguration {
        val ceiling = deviceProfile().recommendedMaxRamMB()
        return if (config.maxMemoryMB <= ceiling) {
            config
        } else {
            config.copy(maxMemoryMB = ceiling)
        }
    }
}