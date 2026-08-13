package com.lumocraft.app.domain.version

/** Installation state of a version, mirrored to its metadata file. */
enum class InstallState {
    INSTALLED,
    PENDING,
    FAILED,
    CORRUPTED
}
