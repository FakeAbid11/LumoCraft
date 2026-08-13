package com.lumocraft.app.domain.performance

import com.lumocraft.app.domain.runtime.RuntimeArchitecture

/**
 * Hardware tier used to pick sensible defaults. Low-end devices get the
 * cheapest settings; high-end devices get headroom. Future phases
 * (Fabric, Forge, Sodium, shaders) consume this profile to pick render
 * and mod settings without probing hardware again.
 */
enum class DeviceTier(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    companion object {
        fun fromName(name: String?): DeviceTier? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Immutable snapshot of the device's capabilities, detected once and
 * cached. [tier] derives the coarse LOW/MEDIUM/HIGH classification:
 *
 * - LOW: low-RAM device (ActivityManager flag) or under 2 GB RAM
 * - MEDIUM: under 4 GB RAM or four cores or fewer
 * - HIGH: everything else
 */
data class DeviceProfile(
    val architecture: RuntimeArchitecture,
    val totalRamMB: Long,
    val cpuCores: Int,
    val androidSdk: Int,
    val androidRelease: String,
    val lowRamDevice: Boolean
) {
    val tier: DeviceTier
        get() = when {
            lowRamDevice || totalRamMB < LOW_RAM_MB -> DeviceTier.LOW
            totalRamMB < MEDIUM_RAM_MB || cpuCores <= MEDIUM_CORES -> DeviceTier.MEDIUM
            else -> DeviceTier.HIGH
        }

    /** Recommended maximum JVM heap: ~60% of device RAM, bounded. */
    fun recommendedMaxRamMB(): Int =
        (totalRamMB * RECOMMENDED_RAM_FRACTION_NUMERATOR / RECOMMENDED_RAM_FRACTION_DENOMINATOR)
            .toInt()
            .coerceIn(512, 4096)

    private companion object {
        const val LOW_RAM_MB = 2048L
        const val MEDIUM_RAM_MB = 4096L
        const val MEDIUM_CORES = 4
        const val RECOMMENDED_RAM_FRACTION_NUMERATOR = 3
        const val RECOMMENDED_RAM_FRACTION_DENOMINATOR = 5
    }
}