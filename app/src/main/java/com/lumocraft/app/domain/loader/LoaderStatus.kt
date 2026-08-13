package com.lumocraft.app.domain.loader

/**
 * Detected health of an installed loader instance.
 *
 * - [INSTALLED]: metadata present and every file verified
 * - [MISSING]: the version directory or metadata is gone
 * - [CORRUPTED]: metadata present but verification found broken files
 * - [PENDING]/[FAILED]: transient states written during installation
 */
enum class LoaderStatus {
    INSTALLED,
    MISSING,
    CORRUPTED,
    PENDING,
    FAILED
}