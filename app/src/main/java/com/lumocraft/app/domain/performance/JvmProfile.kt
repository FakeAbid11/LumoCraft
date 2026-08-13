package com.lumocraft.app.domain.performance

import com.lumocraft.app.domain.runtime.JvmConfiguration

/**
 * Ready-made JVM setting profiles. The launcher picks one automatically
 * from the [DeviceProfile] (see [forDevice]); the user may override the
 * automatic choice (Auto = device-derived) in the Performance dashboard.
 *
 * Each profile is a full [JvmConfiguration] template — heap, GC and a
 * few JIT-friendly flags — tuned for the device class it targets.
 */
enum class JvmProfile(val displayName: String) {

    /** Lower RAM + SerialGC: keeps the game alive on very low-end devices. */
    BATTERY_SAVER("Battery Saver"),

    /** G1GC with moderate RAM: the default for typical devices. */
    BALANCED("Balanced"),

    /** Larger heap + optimized GC: headroom for high-end devices. */
    PERFORMANCE("Performance");

    val template: JvmConfiguration
        get() = when (this) {
            BATTERY_SAVER -> JvmConfiguration(
                maxMemoryMB = 768,
                minMemoryMB = 256,
                gcMode = JvmConfiguration.GcMode.SERIAL,
                extraArguments = listOf("-XX:+ExitOnOutOfMemoryError")
            )
            BALANCED -> JvmConfiguration(
                maxMemoryMB = 1536,
                minMemoryMB = 256,
                gcMode = JvmConfiguration.GcMode.G1,
                extraArguments = listOf("-XX:+UseStringDeduplication")
            )
            PERFORMANCE -> JvmConfiguration(
                maxMemoryMB = 2048,
                minMemoryMB = 512,
                gcMode = JvmConfiguration.GcMode.G1,
                extraArguments = listOf(
                    "-XX:+UseStringDeduplication",
                    "-XX:+ParallelRefProcEnabled"
                )
            )
        }

    companion object {
        /** The automatic profile for a device: cheapest possible tier. */
        fun forDevice(profile: DeviceProfile): JvmProfile = when (profile.tier) {
            DeviceTier.LOW -> BATTERY_SAVER
            DeviceTier.MEDIUM -> BALANCED
            DeviceTier.HIGH -> PERFORMANCE
        }

        fun fromName(name: String?): JvmProfile? = entries.firstOrNull { it.name == name }
    }
}