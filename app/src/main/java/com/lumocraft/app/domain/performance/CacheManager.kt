package com.lumocraft.app.domain.performance

/**
 * One cache row for a version. Every field is optional so the launch
 * cache survives partial population: the classpath builder, the launch
 * pipeline (arguments) and the smart verifier each merge their own data
 * into the same row instead of replacing it.
 *
 * [versionJsonFingerprint] gates everything: when the version JSON (and
 * inherited parents) still has the same size/mtime, the row is valid and
 * unchanged data is never rebuilt.
 */
data class LaunchCacheEntry(
    val versionId: String,
    val versionJsonFingerprint: String? = null,
    val classpath: String? = null,
    val libraryFiles: List<String> = emptyList(),
    val mainClass: String? = null,
    val verifiedLibraries: List<String> = emptyList(),
    val verifiedAssets: Int = 0,
    val assetIndexFingerprint: String? = null,
    val launchArgumentsFingerprint: String? = null,
    val launchArgumentsJson: String? = null,
    val runtimeValidated: Boolean = false,
    val lastVerifiedAt: Long = 0L
)

/** Aggregate cache usage + hit/miss counters for the dashboard. */
data class CacheStats(
    val itemCount: Int = 0,
    val sizeBytes: Long = 0,
    val hits: Long = 0,
    val misses: Long = 0
)

/**
 * Launch cache: resolved classpaths, launch arguments, verified library
 * and asset results and runtime validation markers, keyed by version.
 *
 * Rows are invalidated only when their fingerprint changes or an install
 * touches the version; everything else is reused across launches. All
 * methods are suspend and run on the IO dispatcher so the UI thread
 * never blocks.
 */
interface CacheManager {

    suspend fun getEntry(versionId: String): LaunchCacheEntry?

    /** Full replace of one row; components merge with a prior entry. */
    suspend fun putEntry(entry: LaunchCacheEntry)

    /** Drops a single row (e.g. after install/repair changed the version). */
    suspend fun removeEntry(versionId: String)

    /** Drops every row and the hit/miss counters. */
    suspend fun clear()

    suspend fun stats(): CacheStats

    fun cacheDirectory(): java.io.File

    /** Counters (aggregate over all cache kinds). */
    suspend fun hits(): Long

    suspend fun misses(): Long

    /** Records a cache hit/miss for the aggregate counters. */
    suspend fun recordHit()

    suspend fun recordMiss()

    suspend fun resetCounters()
}