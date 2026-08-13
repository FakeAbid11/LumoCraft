package com.lumocraft.app.domain.runtime

/**
 * Detailed result of a runtime integrity scan.
 */
data class RuntimeVerificationReport(
    val runtimeId: String,
    val binariesOk: Boolean = false,
    val metadataOk: Boolean = false,
    val checksumOk: Boolean = false,
    val missingFiles: List<String> = emptyList(),
    val corruptFiles: List<String> = emptyList()
) {
    val ok: Boolean get() = binariesOk && metadataOk && checksumOk &&
        missingFiles.isEmpty() && corruptFiles.isEmpty()
}