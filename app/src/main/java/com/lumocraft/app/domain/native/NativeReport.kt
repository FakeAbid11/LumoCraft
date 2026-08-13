package com.lumocraft.app.domain.native

import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File

/**
 * Result of native preparation/verification for one version and one
 * architecture. [status] is derived from the on-disk state; the file
 * lists carry the evidence for actionable messages.
 */
data class NativeVerificationReport(
    val versionId: String,
    val arch: RuntimeArchitecture,
    val nativeDirectory: File,
    val status: NativeStatus = NativeStatus.NOT_PREPARED,
    val missingFiles: List<String> = emptyList(),
    val corruptFiles: List<String> = emptyList(),
    val duplicatesRemoved: Int = 0,
    val extractedFiles: Int = 0,
    val skippedJars: Int = 0,
    val archMismatch: Boolean = false
) {
    val ready: Boolean get() = status == NativeStatus.READY
}

/** Raised by the native manager when preparation fails; message is actionable. */
class NativeException(message: String) : Exception(message)