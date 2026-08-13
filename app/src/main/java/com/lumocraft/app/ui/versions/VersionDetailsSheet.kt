package com.lumocraft.app.ui.versions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumocraft.app.R
import com.lumocraft.app.domain.loader.LoaderInstallProgress
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.loader.LoaderStatus
import com.lumocraft.app.domain.loader.LoaderVersion
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.MinecraftVersion

/**
 * Bottom-sheet content for one version: details, live install/repair
 * progress, install/repair/remove actions and the Fabric loader section
 * (compatible loader versions, install, repair, remove).
 */
@Composable
fun VersionDetailsSheet(
    version: MinecraftVersion,
    state: InstallState?,
    loaders: List<LoaderInstance>,
    loaderVersionsState: LoaderVersionsState?,
    progress: InstallProgress?,
    loaderProgress: LoaderInstallProgress?,
    installingLoaderId: String?,
    onInstall: () -> Unit,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
    onRetryLoaderVersions: () -> Unit,
    onInstallLoader: (String) -> Unit,
    onRepairLoader: (String) -> Unit,
    onRemoveLoader: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = version.id,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailTag(
                text = typeLabel(version.type),
                color = MaterialTheme.colorScheme.primary
            )
            DetailTag(
                text = stringResource(R.string.loader_badge_vanilla),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loaders.isNotEmpty()) {
                DetailTag(
                    text = stringResource(R.string.loader_badge_fabric),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            when (state) {
                InstallState.INSTALLED -> DetailTag(
                    text = stringResource(R.string.versions_installed),
                    color = MaterialTheme.colorScheme.primary
                )
                InstallState.PENDING -> DetailTag(
                    text = stringResource(R.string.versions_pending),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InstallState.FAILED -> DetailTag(
                    text = stringResource(R.string.versions_failed),
                    color = MaterialTheme.colorScheme.error
                )
                InstallState.CORRUPTED -> DetailTag(
                    text = stringResource(R.string.versions_corrupted),
                    color = MaterialTheme.colorScheme.error
                )
                null -> Unit
            }
        }
        Text(
            text = formatReleaseDate(version.releaseTime)
                ?.let { stringResource(R.string.versions_released_on, it) }
                ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (progress != null) {
            InstallProgressSection(progress)
        } else {
            when (state) {
                InstallState.INSTALLED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRepair,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.versions_repair))
                    }
                    Button(
                        onClick = onRemove,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(R.string.loader_remove))
                    }
                }
                InstallState.CORRUPTED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRepair,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.versions_repair))
                    }
                }
                else -> Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.versions_install))
                }
            }
        }

        LoaderSection(
            version = version,
            state = state,
            loaders = loaders,
            loaderVersionsState = loaderVersionsState,
            loaderProgress = loaderProgress,
            installingLoaderId = installingLoaderId,
            onRetryLoaderVersions = onRetryLoaderVersions,
            onInstallLoader = onInstallLoader,
            onRepairLoader = onRepairLoader,
            onRemoveLoader = onRemoveLoader
        )
    }
}

@Composable
private fun LoaderSection(
    version: MinecraftVersion,
    state: InstallState?,
    loaders: List<LoaderInstance>,
    loaderVersionsState: LoaderVersionsState?,
    loaderProgress: LoaderInstallProgress?,
    installingLoaderId: String?,
    onRetryLoaderVersions: () -> Unit,
    onInstallLoader: (String) -> Unit,
    onRepairLoader: (String) -> Unit,
    onRemoveLoader: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.loader_badge_fabric),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (state != InstallState.INSTALLED) {
            Text(
                text = stringResource(R.string.loader_need_vanilla, version.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        loaders.forEach { instance ->
            InstalledLoaderRow(
                instance = instance,
                installing = installingLoaderId == instance.instanceId,
                progress = loaderProgress,
                onRepair = { onRepairLoader(instance.instanceId) },
                onRemove = { onRemoveLoader(instance.instanceId) }
            )
        }

        if (loaders.isEmpty() && installingLoaderId != null && loaderProgress != null) {
            LoaderProgressSection(loaderProgress)
        }

        if (loaders.isEmpty() && installingLoaderId == null) {
            when (loaderVersionsState) {
                null, is LoaderVersionsState.Loading -> Text(
                    text = stringResource(R.string.loader_versions_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LoaderVersionsState.Error -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.loader_versions_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetryLoaderVersions) {
                        Text(stringResource(R.string.versions_retry))
                    }
                }
                is LoaderVersionsState.Loaded -> {
                    if (loaderVersionsState.versions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.loader_versions_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LoaderVersionSelector(
                            versions = loaderVersionsState.versions,
                            onInstallLoader = onInstallLoader
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledLoaderRow(
    instance: LoaderInstance,
    installing: Boolean,
    progress: LoaderInstallProgress?,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.loader_version_label,
                        instance.metadata.loaderVersion
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.loader_for_minecraft,
                        instance.metadata.minecraftVersion
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LoaderStatusLabel(instance.status)
        }
        if (installing && progress != null) {
            LoaderProgressSection(progress)
        } else if (instance.status != LoaderStatus.INSTALLED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRepair,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.versions_repair))
                }
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.loader_remove))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRepair,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.versions_repair))
                }
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.loader_remove))
                }
            }
        }
    }
}

@Composable
private fun LoaderVersionSelector(
    versions: List<LoaderVersion>,
    onInstallLoader: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    val ordered = versions.sortedBy { !it.stable }
    LaunchedEffect(Unit) {
        if (selected == null) {
            selected = ordered.firstOrNull { it.stable }?.loaderVersion
                ?: ordered.firstOrNull()?.loaderVersion
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.loader_selector),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = selected
                        ?: stringResource(R.string.loader_selector_hint),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ordered.forEach { loaderVersion ->
                    DropdownMenuItem(
                        text = { Text(loaderVersion.loaderVersion) },
                        onClick = {
                            expanded = false
                            selected = loaderVersion.loaderVersion
                        }
                    )
                }
            }
        }
        Button(
            onClick = { selected?.let(onInstallLoader) },
            enabled = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.loader_install_fabric))
        }
    }
}

/** Live loader pipeline progress: stage, percentage bar, bytes. */
@Composable
private fun LoaderProgressSection(progress: LoaderInstallProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = loaderStageLabel(progress.stage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            progress.percentage?.let { percentage ->
                Text(
                    text = stringResource(R.string.versions_percent, percentage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val percentage = progress.percentage
        if (percentage != null) {
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
        Text(
            text = stringResource(
                R.string.versions_bytes_progress,
                formatBytes(progress.downloadedBytes),
                formatBytes(progress.totalBytes)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoaderStatusLabel(status: LoaderStatus) {
    val (text, color) = when (status) {
        LoaderStatus.INSTALLED -> stringResource(R.string.loader_installed) to
            MaterialTheme.colorScheme.primary
        LoaderStatus.MISSING -> stringResource(R.string.loader_missing) to
            MaterialTheme.colorScheme.error
        LoaderStatus.CORRUPTED -> stringResource(R.string.loader_corrupted) to
            MaterialTheme.colorScheme.error
        LoaderStatus.PENDING -> stringResource(R.string.loader_pending) to
            MaterialTheme.colorScheme.onSurfaceVariant
        LoaderStatus.FAILED -> stringResource(R.string.loader_failed) to
            MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

/** Live pipeline progress: stage, percentage bar, bytes and file counts. */
@Composable
private fun InstallProgressSection(progress: InstallProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stageLabel(progress.stage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            progress.percentage?.let { percentage ->
                Text(
                    text = stringResource(R.string.versions_percent, percentage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val percentage = progress.percentage
        if (percentage != null) {
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.versions_bytes_progress,
                    formatBytes(progress.downloadedBytes),
                    formatBytes(progress.totalBytes)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pluralStringResource(
                    R.plurals.versions_files_count,
                    progress.filesRemaining,
                    progress.filesRemaining
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailTag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .padding(end = 4.dp)
    )
}