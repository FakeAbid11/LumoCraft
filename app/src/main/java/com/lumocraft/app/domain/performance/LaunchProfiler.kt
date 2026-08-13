package com.lumocraft.app.domain.performance

/**
 * One measured launch: how long validation, classpath resolution and JVM
 * startup took, whether validation was served from cache, and the cache
 * counters observed during the session. Stored in recent history.
 */
data class LaunchTimings(
    val validationMs: Long = 0,
    val classpathMs: Long = 0,
    val jvmStartMs: Long = 0,
    val totalMs: Long = 0,
    val cachedValidation: Boolean = false,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
    val success: Boolean = false,
    val startedAt: Long = 0L
)

/** Recent launch history: last launch, fastest launch and the count. */
data class LaunchHistory(
    val launches: Int = 0,
    val lastLaunch: LaunchTimings? = null,
    val fastestLaunch: LaunchTimings? = null
)

/**
 * Measures launch phases (validation, classpath, JVM start, total) and
 * keeps the recent history on disk for the Performance dashboard.
 */
interface LaunchProfiler {

    suspend fun summary(): LaunchHistory

    suspend fun record(timings: LaunchTimings)

    suspend fun clear()
}