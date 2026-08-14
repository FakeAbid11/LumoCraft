package com.lumocraft.app.domain.runtime

/**
 * Detailed result of a runtime integrity scan.
 *
 * [ok] is true only when every check passed: required binaries,
 * metadata, checksum, `lib/modules`, `lib/server`, `jmods` and root
 * layout consistency. A partially broken runtime is never reported as
 * ok — callers can drive repair from [missingFiles]/[corruptFiles].
 */
data class RuntimeVerificationReport(
    val runtimeId: String,
    val binariesOk: Boolean = false,
    val metadataOk: Boolean = false,
    val checksumOk: Boolean = false,
    val modulesOk: Boolean = false,
    val serverOk: Boolean = false,
    val jmodsOk: Boolean = false,
    val rootOk: Boolean = false,
    val missingFiles: List<String> = emptyList(),
    val corruptFiles: List<String> = emptyList(),
    /** Human-readable checksum mismatch detail (expected vs actual). */
    val checksumDetail: String? = null
) {
    val ok: Boolean get() = binariesOk && metadataOk && checksumOk &&
        modulesOk && serverOk && jmodsOk && rootOk &&
        missingFiles.isEmpty() && corruptFiles.isEmpty()
}