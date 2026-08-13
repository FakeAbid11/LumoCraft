package com.lumocraft.app.domain.runtime

/**
 * JVM memory and argument configuration for a future launch.
 * Persisted via SharedPreferences; [buildArguments] generates the
 * actual JVM argument list without launching anything.
 */
data class JvmConfiguration(
    val maxMemoryMB: Int = DEFAULT_MAX_MB,
    val minMemoryMB: Int = DEFAULT_MIN_MB,
    val gcMode: GcMode = GcMode.G1,
    val extraArguments: List<String> = emptyList()
) {
    /** Generates the JVM argument list for this configuration. */
    fun buildArguments(): List<String> = buildList {
        add("-Xmx${maxMemoryMB}M")
        add("-Xms${minMemoryMB}M")
        add(gcMode.argument)
        addAll(extraArguments)
    }

    enum class GcMode(val argument: String) {
        G1("-XX:+UseG1GC"),
        ZGC("-XX:+UseZGC"),
        SERIAL("-XX:+UseSerialGC")
    }

    companion object {
        const val DEFAULT_MAX_MB = 1024
        const val DEFAULT_MIN_MB = 256
        const val MIN_RAM_MB = 512
        const val MAX_RAM_MB = 4096
        const val RECOMMENDED_RAM_MB = 2048
    }
}