package com.lumocraft.app.domain.version

/**
 * A library to download, derived from a version JSON file.
 * [path] is Mojang's relative path inside `libraries/` (folder structure
 * preserved); [url] is the absolute download URL.
 */
data class LibraryRef(
    val path: String,
    val sha1: String?,
    val size: Long?,
    val url: String
)

/** Asset index reference from a version JSON (`assetIndex`). */
data class AssetIndexRef(
    val id: String,
    val url: String,
    val sha1: String?,
    val size: Long?
)

/** Logging configuration reference from a version JSON (`logging.client.file`). */
data class LoggingConfigRef(
    val id: String,
    val url: String,
    val sha1: String?,
    val size: Long?
)
