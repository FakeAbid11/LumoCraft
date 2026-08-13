package com.lumocraft.app.domain.version

/**
 * Release types as published by Mojang's version manifest.
 */
enum class VersionType {
    RELEASE,
    SNAPSHOT,
    OLD_BETA,
    OLD_ALPHA,
    UNKNOWN;

    companion object {
        fun fromManifestType(type: String): VersionType = when (type.lowercase()) {
            "release" -> RELEASE
            "snapshot" -> SNAPSHOT
            "old_beta" -> OLD_BETA
            "old_alpha" -> OLD_ALPHA
            else -> UNKNOWN
        }
    }
}

/**
 * User-facing filter categories. Filtering happens locally after the
 * manifest has been downloaded.
 */
enum class VersionFilter {
    ALL,
    RELEASE,
    SNAPSHOT,
    OLD_BETA,
    OLD_ALPHA;

    fun matches(type: VersionType): Boolean = when (this) {
        ALL -> true
        RELEASE -> type == VersionType.RELEASE
        SNAPSHOT -> type == VersionType.SNAPSHOT
        OLD_BETA -> type == VersionType.OLD_BETA
        OLD_ALPHA -> type == VersionType.OLD_ALPHA
    }
}
