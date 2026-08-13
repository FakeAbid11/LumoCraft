package com.lumocraft.app.domain.version

/**
 * A single entry from Mojang's official version manifest.
 * All timestamps are epoch millis; [url] points to the version JSON.
 * [sha1] and [size] are the manifest-published digests of the version JSON
 * itself, used to skip or verify an existing download.
 */
data class MinecraftVersion(
    val id: String,
    val type: VersionType,
    val url: String,
    val releaseTime: Long,
    val time: Long,
    val sha1: String? = null,
    val size: Long? = null
)

/**
 * The parsed version manifest, ordered newest-first as published.
 */
data class VersionManifest(
    val latestRelease: String?,
    val latestSnapshot: String?,
    val versions: List<MinecraftVersion>
)
