package com.lumocraft.app.domain.native

/** Lifecycle of the extracted native libraries for a version. */
enum class NativeStatus {
    /** No extraction has been attempted (or stamps are missing). */
    NOT_PREPARED,

    /** Extraction in progress. */
    PREPARING,

    /** All natives verified on disk and match the device architecture. */
    READY,

    /** Missing/corrupt natives or an architecture mismatch; launch blocked. */
    CORRUPTED
}