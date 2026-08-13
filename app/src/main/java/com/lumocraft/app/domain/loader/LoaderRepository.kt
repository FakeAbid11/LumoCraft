package com.lumocraft.app.domain.loader

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for loaders (Fabric today; Quilt, Forge,
 * NeoForge in later phases).
 *
 * The repository is loader-type agnostic: every call either takes a
 * [LoaderType] or resolves it from the instance id, so new loaders only
 * need a metadata service + installer registered at construction time.
 */
interface LoaderRepository {

    /** All installed loader instances with live health status. */
    fun observeInstalledLoaders(): Flow<List<LoaderInstance>>

    /**
     * Loader versions compatible with [minecraftVersion], published by
     * the loader's metadata service (network; cached on disk).
     */
    suspend fun fetchLoaderVersions(
        type: LoaderType,
        minecraftVersion: String,
    ): Result<List<LoaderVersion>>

    /**
     * Installs [loaderVersion] on top of [minecraftVersion]. The vanilla
     * version must already be installed (the loader profile inherits its
     * libraries). Emits [LoaderInstallProgress] until completion or a
     * terminal error; cancelling the collector aborts the operation.
     */
    fun install(
        type: LoaderType,
        minecraftVersion: String,
        loaderVersion: String,
    ): Flow<LoaderInstallProgress>

    /**
     * Verifies an installed loader instance and redownloads only the
     * missing or corrupted files, then re-verifies.
     */
    fun repair(type: LoaderType, instanceId: String): Flow<LoaderInstallProgress>

    /**
     * Removes a loader instance: its version directory and its loader
     * metadata. The underlying Minecraft version is untouched.
     */
    suspend fun remove(type: LoaderType, instanceId: String): Result<Unit>

    /**
     * The loader instance a launch target resolves to, or null when the
     * version id is a plain vanilla version.
     */
    suspend fun resolveActiveLoader(versionId: String): LoaderInstance?
}