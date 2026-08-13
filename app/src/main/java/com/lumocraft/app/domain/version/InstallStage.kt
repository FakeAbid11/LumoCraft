package com.lumocraft.app.domain.version

/**
 * Phases of the install pipeline, in execution order.
 * Percentage shown in the UI is derived from the stage index combined
 * with the stage's own progress fraction.
 */
enum class InstallStage {
    PREPARING,
    VERSION_JSON,
    LIBRARIES,
    ASSET_INDEX,
    ASSETS,
    LOGGING_CONFIG,
    VERIFICATION,
    COMPLETE
}

/**
 * Immutable snapshot of install/repair progress.
 *
 * [stageFraction] is the fraction (0f..1f) of the current stage, or null
 * when the stage is indeterminate. [error] marks a terminal failure; the
 * stage value is then ignored by the UI.
 */
data class InstallProgress(
    val versionId: String,
    val stage: InstallStage,
    val stageFraction: Float? = null,
    val filesCompleted: Int = 0,
    val filesRemaining: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {

    /** Overall percentage across all stages, or null when indeterminate. */
    val percentage: Int? get() = when {
        error != null -> null
        stage == InstallStage.COMPLETE -> 100
        stageFraction == null -> null
        else -> {
            val stageShare = (stageFraction * 100).toInt()
            (stage.ordinal * 100 + stageShare) / InstallStage.entries.size
        }
    }

    val isFinished: Boolean get() = stage == InstallStage.COMPLETE
}
