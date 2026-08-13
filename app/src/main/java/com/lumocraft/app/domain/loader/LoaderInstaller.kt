package com.lumocraft.app.domain.loader

/**
 * Installs and repairs one loader type on this device. Implementations
 * are registered in the [LoaderRepository] keyed by [type]; the launcher
 * never dispatches on the loader type itself.
 */
interface LoaderInstaller {

    val type: LoaderType

    /**
     * Installs [loaderVersion] for [minecraftVersion]. Produces a fully
     * prepared version directory (loader profile JSON, libraries) plus
     * loader metadata, followed by a full verification pass.
     */
    suspend fun install(
        minecraftVersion: String,
        loaderVersion: String,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): Result<LoaderMetadata>

    /**
     * Scans [instanceId] and redownloads only what is missing or
     * corrupted, then re-verifies.
     */
    suspend fun repair(
        instanceId: String,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): Result<LoaderMetadata>
}