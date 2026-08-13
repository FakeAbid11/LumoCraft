package com.lumocraft.app.domain.version

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for Minecraft versions on this device.
 *
 * The manifest is fetched over the network; install states come from local
 * metadata. [install] and [repair] emit [InstallProgress] snapshots until
 * completion (stage COMPLETE) or a terminal error (error != null);
 * cancelling the collecting coroutine aborts the operation.
 */
interface VersionRepository {

    /** Maps installed version id -> its current state, loaded from disk. */
    fun observeInstalledStates(): Flow<Map<String, InstallState>>

    /** Downloads and parses the official version manifest. */
    suspend fun fetchManifest(): Result<VersionManifest>

    /** Fully installs a vanilla version (JSON, libraries, assets, logging config). */
    fun install(version: MinecraftVersion): Flow<InstallProgress>

    /**
     * Scans the installation and redownloads only missing or corrupted
     * files, then re-verifies.
     */
    fun repair(version: MinecraftVersion): Flow<InstallProgress>

    /**
     * Uninstalls a version: removes its version directory (version JSON,
     * client jar, metadata, logging configuration). Loader instances
     * built on top of it are left untouched (they resolve their own
     * parent at launch time).
     */
    suspend fun remove(versionId: String): Result<Unit>
}
