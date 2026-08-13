package com.lumocraft.app.domain.launch

/**
 * Result of pre-launch validation. [ok] is true only when every check
 * passed; individual flags let the UI and the pipeline explain exactly
 * what is missing.
 */
data class LaunchValidationReport(
    val accountOk: Boolean = false,
    val runtimeOk: Boolean = false,
    val versionOk: Boolean = false,
    val mainClassOk: Boolean = false,
    val clientJarOk: Boolean = false,
    val assetIndexOk: Boolean = true,
    val loggingConfigOk: Boolean = true,
    val nativeOk: Boolean = true,
    val nativeDetail: String? = null,
    val missingLibraries: List<String> = emptyList(),
    val missingAssets: Int = 0,
    val runtimeDetail: String? = null
) {
    val ok: Boolean get() =
        accountOk && runtimeOk && versionOk && mainClassOk &&
            clientJarOk && assetIndexOk && loggingConfigOk && nativeOk &&
            missingLibraries.isEmpty() && missingAssets == 0
}