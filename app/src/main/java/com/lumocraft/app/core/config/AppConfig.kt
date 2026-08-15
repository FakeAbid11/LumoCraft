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

    // SHA-256 of the pinned JRE 17 archives above. Computed once from the
    // immutable release assets; the installer refuses any archive whose
    // hash does not match, so a corrupted or MITM'd download is rejected
    // before extraction.
    const val RUNTIME_JRE17_ARM64_SHA256 =
        "c64583ac2e0ec8857e43456fa9adcf482c6a8e454a7133173bf15692d2478b8d"
    const val RUNTIME_JRE17_ARM_SHA256 =
        "1c27a6a839fc76fc14618ad547b212f881f8d5b8dfa0c5853a38817ba316b428"
    const val RUNTIME_JRE17_X86_64_SHA256 =
        "ebbdf75ab864a83671a108032c30e67174f79cc19596cfc1d7bfb71be26b6e71"

    /**
     * Android/Bionic-compatible OpenJDK 21 runtime. Minecraft 1.20.5+ requires
     * Java 21, which the older multiarch mirror above never published, so this
     * runtime is sourced from ZalithLauncher's bundled Bionic OpenJDK 21.0.1
     * build, pinned to an immutable commit.
     *
     * Unlike the single-file JRE 17 archives, this runtime is split into an
     * architecture-independent [RUNTIME_JRE21_UNIVERSAL] part (conf/, legal/,
     * lib/ classes) plus a per-architecture `bin-<arch>.tar.xz` (native
     * binaries incl. bin/java and lib/libjli.so). Both parts extract into the
     * same runtime directory to form a complete JRE.
     */
    private const val RUNTIME_JRE21_PIN =
        "606aa07567a58f926fcc46e5b529c52eb9fea9fe"
    const val RUNTIME_JRE21_BASE_URL =
        "https://raw.githubusercontent.com/Vera-Firefly/ZalithLauncher/" +
            "$RUNTIME_JRE21_PIN/ZalithLauncher/src/main/assets/components/jre-21/"
    const val RUNTIME_JRE21_UNIVERSAL = "universal.tar.xz"
    const val RUNTIME_JRE21_UNIVERSAL_SHA256 =
        "57bd118b3696d572257c5f4be1762379eb6a81361a17da1c4b0b9f7131dd6d7c"
    const val RUNTIME_JRE21_ARM64 = "bin-arm64.tar.xz"
    const val RUNTIME_JRE21_ARM64_SHA256 =
        "9889de6d0526e0c9708ea18c6e7e41a7c3d5ae366a02524ae3a392274d3462b5"
    const val RUNTIME_JRE21_ARM = "bin-arm.tar.xz"
    const val RUNTIME_JRE21_ARM_SHA256 =
        "c782b17aabaa2be510144c464b5aa4784f984d2a07cfc4c3cd13a86ee399798e"
    const val RUNTIME_JRE21_X86_64 = "bin-x86_64.tar.xz"
    const val RUNTIME_JRE21_X86_64_SHA256 =
        "1e1ccc34dd24f56efd2440aa80e658df5ac6efa2e3d866d8be0345d31284bef6"

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