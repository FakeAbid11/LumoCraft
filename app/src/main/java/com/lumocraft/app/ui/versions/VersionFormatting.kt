package com.lumocraft.app.ui.versions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lumocraft.app.R
import com.lumocraft.app.domain.loader.LoaderInstallStage
import com.lumocraft.app.domain.version.InstallStage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RELEASE_DATE_FORMAT = DateTimeFormatter.ofPattern(
    "d MMM yyyy",
    Locale.getDefault()
)

/** Formats a release time as e.g. "2 Jun 2026", or null when unknown. */
fun formatReleaseDate(epochMillis: Long?): String? {
    if (epochMillis == null || epochMillis <= 0L) return null
    return RELEASE_DATE_FORMAT.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    )
}

/** Formats a byte count for display, e.g. "12.3 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

/** Human-readable label for an install stage. */
@Composable
fun stageLabel(stage: InstallStage): String = stringResource(
    when (stage) {
        InstallStage.PREPARING -> R.string.versions_stage_preparing
        InstallStage.VERSION_JSON -> R.string.versions_stage_version_json
        InstallStage.LIBRARIES -> R.string.versions_stage_libraries
        InstallStage.ASSET_INDEX -> R.string.versions_stage_asset_index
        InstallStage.ASSETS -> R.string.versions_stage_assets
        InstallStage.LOGGING_CONFIG -> R.string.versions_stage_logging_config
        InstallStage.VERIFICATION -> R.string.versions_stage_verification
        InstallStage.COMPLETE -> R.string.versions_stage_complete
    }
)

/** Human-readable label for a loader install stage. */
@Composable
fun loaderStageLabel(stage: LoaderInstallStage): String = stringResource(
    when (stage) {
        LoaderInstallStage.PREPARING -> R.string.loader_stage_preparing
        LoaderInstallStage.METADATA -> R.string.loader_stage_metadata
        LoaderInstallStage.LIBRARIES -> R.string.loader_stage_libraries
        LoaderInstallStage.VERIFICATION -> R.string.loader_stage_verification
        LoaderInstallStage.COMPLETE -> R.string.loader_stage_complete
    }
)
