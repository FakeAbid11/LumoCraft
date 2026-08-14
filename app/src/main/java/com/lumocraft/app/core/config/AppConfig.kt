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

    /** Minimum per-connection throughput before concurrency is shed (B/s). */
    const val DOWNLOAD_MIN_CONNECTION_THROUGHPUT_BPS = 128 * 1024

    /** Bandwidth estimation window for adaptive concurrency. */
    const val THROUGHPUT_WINDOW_MS = 10_000L

    /**
     * Android/Bionic-compatible OpenJDK 17 runtime (PojavLauncher multiarch build).
     *
     * A standard desktop Linux (glibc) JDK — e.g. from Adoptium — cannot be
     * dlopen'd on Android: its libjli.so has a hard dependency on "libdl.so.2",
     * which Bionic never provides (Bionic folded libdl into libc and does not
     * version it), so the load fails before the JVM starts. The runtime must
     * therefore be an OpenJDK build linked against Bionic. These assets are
     * per-architecture .tar.xz JREs.
     */
    const val RUNTIME_JRE17_BASE_URL =
        "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/" +
            "releases/download/jre17-ec28559/"
    const val RUNTIME_JRE17_ARM64 = "jre17-arm64-20210825-release.tar.xz"
    const val RUNTIME_JRE17_ARM = "jre17-arm-20210914-release.tar.xz"
    const val RUNTIME_JRE17_X86_64 = "jre17-x86_64-20210825-release.tar.xz"

    /** Update channel: GitHub releases for this project. */
    const val GITHUB_OWNER = "FakeAbid11"
    const val GITHUB_REPO = "LumoCraft"
    const val GITHUB_RELEASES_LATEST_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val GITHUB_RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    /** Performance engine storage, under the launcher root. */
    const val CACHE_DIRECTORY_NAME = "cache"
    const val LAUNCH_CACHE_FILE = "launch_cache.json"
    const val CHECKSUM_CACHE_FILE = "checksums.json"
    const val LAUNCH_HISTORY_FILE = "launch_history.json"
    const val CACHE_MAX_ENTRIES = 24
    const val LAUNCH_HISTORY_LIMIT = 10
    const val CHECKSUM_CACHE_LIMIT = 4000

    /** How long a validated runtime stays trusted without re-verification. */
    const val RUNTIME_CACHE_VALIDITY_MS = 5 * 60 * 1000L

    /** Memory optimizer pool bounds (kept small for low-end devices). */
    const val BUFFER_POOL_MAX_BUFFERS = 8
    const val BUFFER_POOL_MAX_BYTES = 256L * 1024
    const val BUFFER_POOL_MAX_BUFFER_SIZE = 64 * 1024

    val USER_AGENT: String = "LumoCraft/${BuildConfig.VERSION_NAME}"
}