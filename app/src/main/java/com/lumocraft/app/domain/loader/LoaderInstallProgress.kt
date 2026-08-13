package com.lumocraft.app.domain.loader

/** Phases of a loader installation, in execution order. */
enum class LoaderInstallStage {
    PREPARING,
    METADATA,
    LIBRARIES,
    VERIFICATION,
    COMPLETE
}

/**
 * Immutable snapshot of loader install/repair progress. Mirrors
 * [com.lumocraft.app.domain.version.InstallProgress] but with loader
 * stages; [error] marks a terminal failure.
 */
data class LoaderInstallProgress(
    val instanceId: String,
    val stage: LoaderInstallStage,
    val stageFraction: Float? = null,
    val filesCompleted: Int = 0,
    val filesRemaining: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {
    val percentage: Int? get() = when {
        error != null -> null
        stage == LoaderInstallStage.COMPLETE -> 100
        stageFraction == null -> null
        else -> {
            val stageShare = (stageFraction * 100).toInt()
            (stage.ordinal * 100 + stageShare) / LoaderInstallStage.entries.size
        }
    }

    val isFinished: Boolean get() = stage == LoaderInstallStage.COMPLETE
}