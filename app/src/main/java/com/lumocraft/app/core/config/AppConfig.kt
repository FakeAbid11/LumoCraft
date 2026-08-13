package com.lumocraft.app.core.config

import com.lumocraft.app.BuildConfig

/**
 * Central configuration for launcher-wide constants.
 * Everything that might need to change (endpoints, timeouts, versioning)
 * lives here instead of being scattered across the codebase.
 */
object AppConfig {

    /** Name of the launcher directory created under app storage. */
    const val LAUNCHER_DIRECTORY_NAME = "minecraft"

    /**
     * Mojang's official version manifest.
     * The endpoint can be swapped (mirror/CDN) by changing this single value;
     * version URLs inside the manifest are used as-is.
     */
    const val MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    /** Bumped whenever the on-disk metadata format changes. */
    const val INSTALLER_VERSION = 1

    const val HTTP_CONNECT_TIMEOUT_MS = 10_000
    const val HTTP_READ_TIMEOUT_MS = 30_000
    const val DOWNLOAD_MAX_ATTEMPTS = 3
    const val DOWNLOAD_RETRY_BACKOFF_MS = 500L

    /** Base URL for libraries that do not publish their own download URL. */
    const val LIBRARIES_BASE_URL = "https://libraries.minecraft.net/"

    /** Base URL for asset objects, keyed by hash: <base><hash[0..2]>/<hash>. */
    const val ASSETS_BASE_URL = "https://resources.download.minecraft.net/"

    /** Parallel downloads per stage. Kept low for low-end devices. */
    const val DOWNLOAD_CONCURRENCY = 4

    /** Base URL for Java runtime archives (placeholder; swap for a real mirror). */
    const val RUNTIME_BASE_URL = "https://api.adoptium.net/v3/binary/latest/"

    val USER_AGENT: String = "LumoCraft/${BuildConfig.VERSION_NAME}"
}