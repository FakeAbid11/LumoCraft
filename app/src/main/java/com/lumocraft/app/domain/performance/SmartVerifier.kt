package com.lumocraft.app.domain.performance

/**
 * Result of a smart verification pass. [cached] marks a result that was
 * fully served from the launch cache (no disk scan at all); otherwise
 * [filesChecked] files were stat-checked and [filesHashed] were actually
 * hashed (only files without a valid cached checksum).
 */
data class SmartVerificationResult(
    val versionId: String,
    val ok: Boolean = false,
    val cached: Boolean = false,
    val filesChecked: Int = 0,
    val filesHashed: Int = 0,
    val assetIndexOk: Boolean = true,
    val missingLibraries: List<String> = emptyList(),
    val corruptLibraries: List<String> = emptyList(),
    val missingAssets: Int = 0,
    val corruptAssets: Int = 0
) {
    val allMissingLibraries: List<String> get() = missingLibraries + corruptLibraries
}

/**
 * Selective verification instead of hashing every file every launch:
 *
 * - cached results (fingerprint match) are trusted outright for cheap
 *   readiness checks ([verify] with [SmartVerifier.mode] READINESS)
 * - launch-time checks stat every library/asset (size only, no hashing)
 * - only files whose size changed (or that have no cached checksum) are
 *   rehashed; the checksum cache makes repeat scans hash-free
 *
 * [SmartVerifier.invalidate] is called by the installer whenever a
 * version's files change, so stale rows are dropped exactly when needed.
 */
interface SmartVerifier {

    enum class Mode { READINESS, LAUNCH }

    suspend fun verify(versionId: String, mode: Mode): SmartVerificationResult

    /** Drops the cached verification for a version. */
    suspend fun invalidate(versionId: String)

    /** Drops every cached verification. */
    suspend fun invalidateAll()
}