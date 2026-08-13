package com.lumocraft.app.domain.update

/**
 * Result of an update check against the release channel.
 */
enum class UpdateStatus {
    /** The installed version is the newest available. */
    UP_TO_DATE,

    /** A newer release exists. */
    UPDATE_AVAILABLE,

    /** The check could not complete (offline, server error, bad payload). */
    UNKNOWN
}

/** A published release as reported by the channel. */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val isPrerelease: Boolean,
    val publishedAt: String?,
    val body: String?,
    val releaseUrl: String?,
    val downloadUrl: String?,
)

/** Outcome of [UpdateRepository.checkForUpdates]. */
data class UpdateCheckResult(
    val status: UpdateStatus,
    val currentVersionName: String,
    val latest: ReleaseInfo? = null,
    val error: String? = null,
)

/**
 * Single entry point for release-channel checks. The launcher only
 * *informs* the user about updates — it never downloads or installs
 * them automatically.
 */
interface UpdateRepository {

    /**
     * Queries the latest release and compares it against the installed
     * version. Safe to call from any thread; heavy work runs on IO.
     */
    suspend fun checkForUpdates(): UpdateCheckResult
}
