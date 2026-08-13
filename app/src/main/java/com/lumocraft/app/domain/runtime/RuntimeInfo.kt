package com.lumocraft.app.domain.runtime

/**
 * Metadata for an installed (or attempted) Java runtime.
 * Mirrored to `<launcherRoot>/runtime/metadata.json`.
 */
data class RuntimeInfo(
    val id: String,
    val version: String,
    val architecture: RuntimeArchitecture,
    val vendor: String,
    val path: String,
    val installedAt: Long,
    val isDefault: Boolean,
    val status: RuntimeStatus,
    val checksum: String? = null
)

/** Lifecycle state of a runtime on disk. */
enum class RuntimeStatus {
    INSTALLED,
    MISSING,
    VERIFYING,
    CORRUPTED
}