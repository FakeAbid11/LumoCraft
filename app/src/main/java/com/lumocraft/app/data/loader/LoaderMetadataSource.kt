package com.lumocraft.app.data.loader

import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.loader.LoaderVersion

/**
 * Publishes the compatible loader versions of one loader type.
 * Registered in the [com.lumocraft.app.domain.loader.LoaderRepository];
 * Quilt/Forge/NeoForge add their own source in a later phase.
 */
interface LoaderMetadataSource {

    val type: LoaderType

    /** Loader versions compatible with [minecraftVersion]. */
    suspend fun loaderVersions(minecraftVersion: String): Result<List<LoaderVersion>>
}