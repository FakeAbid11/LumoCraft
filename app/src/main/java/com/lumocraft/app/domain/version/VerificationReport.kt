package com.lumocraft.app.domain.version

/**
 * Result of a full integrity scan of an installed version.
 * Libraries are verified by SHA-1; assets are verified by existence and
 * size (an object's file name is its hash, so a size mismatch is
 * conclusive). [ok] is true only when everything checks out.
 */
data class VerificationReport(
    val versionId: String,
    val versionJsonOk: Boolean = false,
    val metadataOk: Boolean = false,
    val assetIndexOk: Boolean = false,
    val loggingConfigOk: Boolean = false,
    val missingLibraries: List<LibraryRef> = emptyList(),
    val corruptLibraries: List<LibraryRef> = emptyList(),
    val missingAssets: List<String> = emptyList(),
    val corruptAssets: List<String> = emptyList(),
    val totalLibraries: Int = 0,
    val totalAssets: Int = 0,
    val verifiedAssets: Int = 0
) {
    val ok: Boolean get() =
        versionJsonOk && metadataOk && assetIndexOk && loggingConfigOk &&
            missingLibraries.isEmpty() && corruptLibraries.isEmpty() &&
            missingAssets.isEmpty() && corruptAssets.isEmpty()
}
