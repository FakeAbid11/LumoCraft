package com.lumocraft.app.domain.version

/**
 * Persisted metadata for an installed version, stored as
 * `<launcherRoot>/versions/<id>/metadata.json`.
 */
data class InstalledVersionMetadata(
    val version: String,
    val installedAt: Long,
    val source: String,
    val installerVersion: Int,
    val state: InstallState
)
