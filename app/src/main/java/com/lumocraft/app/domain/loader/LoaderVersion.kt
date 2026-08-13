package com.lumocraft.app.domain.loader

/**
 * A loader version pair published by the loader's metadata service.
 * [loaderVersion] is the loader itself (e.g. Fabric Loader 0.15.11),
 * [intermediaryVersion] is the mapping artifact for the Minecraft
 * version it was built for.
 */
data class LoaderVersion(
    val loaderVersion: String,
    val intermediaryVersion: String,
    val stable: Boolean,
    val maven: String
)