package com.lumocraft.app.domain.loader

import com.lumocraft.app.domain.version.InstallState

/**
 * Persisted metadata for one installed loader instance, stored as
 * `<launcherRoot>/loader/<type>/instances/<instanceId>/metadata.json`.
 *
 * [instanceId] doubles as the version directory name under
 * `<launcherRoot>/versions/<instanceId>/` (e.g.
 * `fabric-loader-0.15.11-1.20.1`), so the standard version pipeline and
 * the loader registry track the same on-disk installation.
 */
data class LoaderMetadata(
    val instanceId: String,
    val type: LoaderType,
    val minecraftVersion: String,
    val loaderVersion: String,
    val installerVersion: String,
    val installedAt: Long,
    val state: InstallState
)