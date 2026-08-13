package com.lumocraft.app.ui.loader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.loader.LoaderStatus
import com.lumocraft.app.ui.versions.formatBytes

/**
 * Dedicated loader overview: installed loaders with health status, the
 * active launch target and Fabric compatibility per installed version.
 * Content is driven entirely by [LoaderRepository], so future loaders
 * (Quilt, Forge, NeoForge) show up here automatically.
 */
@Composable
fun LoaderManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoaderManagerViewModel = viewModel(factory = LoaderManagerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { viewModel.clearError() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loader_manager_back)
                )
            }
            Text(
                text = stringResource(R.string.loader_manager_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        ActiveLoaderCard(activeLoader = state.activeLoader)

        Text(
            text = stringResource(R.string.loader_manager_installed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (state.installedLoaders.isEmpty()) {
            Text(
                text = stringResource(R.string.loader_manager_no_loaders),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.installedLoaders.forEach { instance ->
                InstalledLoaderCard(
                    instance = instance,
                    isActive = state.activeLoader?.instanceId == instance.instanceId,
                    repairing = state.repairingId == instance.instanceId,
                    progress = state.repairProgress,
                    onRepair = { viewModel.repair(instance.instanceId) }
                )
            }
        }

        Text(
            text = stringResource(R.string.loader_manager_compatibility),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        CompatibilitySection(state)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActiveLoaderCard(activeLoader: LoaderInstance?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.loader_manager_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (activeLoader != null) {
                Text(
                    text = stringResource(
                        R.string.loader_version_label,
                        activeLoader.metadata.loaderVersion
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(
                        R.string.loader_for_minecraft,
                        activeLoader.metadata.minecraftVersion
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = stringResource(R.string.loader_manager_no_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun InstalledLoaderCard(
    instance: LoaderInstance,
    isActive: Boolean,
    repairing: Boolean,
    progress: com.lumocraft.app.domain.loader.LoaderInstallProgress?,
    onRepair: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.metadata.type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.loader_version_label,
                            instance.metadata.loaderVersion
                        ),
                        style = MaterialTheme.typography.bodyMedium,
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
                LoaderStatusText(instance.status)
            }
            if (isActive) {
                Text(
                    text = stringResource(
                        R.string.loader_launch_active,
                        instance.metadata.instanceId
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(
                    R.string.loader_manager_repair_status,
                    stringResource(
                        when (instance.status) {
                            LoaderStatus.INSTALLED -> R.string.loader_installed
                            LoaderStatus.MISSING -> R.string.loader_missing
                            LoaderStatus.CORRUPTED -> R.string.loader_corrupted
                            LoaderStatus.PENDING -> R.string.loader_pending
                            LoaderStatus.FAILED -> R.string.loader_failed
                        }
                    )
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                repairing && progress != null -> {
                    LinearProgressIndicator(
                        progress = { (progress.percentage ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                instance.status == LoaderStatus.INSTALLED -> Unit
                else -> Button(
                    onClick = onRepair,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.versions_repair))
                }
            }
        }
    }
}

@Composable
private fun CompatibilitySection(state: LoaderManagerUiState) {
    when {
        state.compatibilityLoading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.loader_versions_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.installedVanillaVersions.isEmpty() -> Text(
            text = stringResource(R.string.loader_manager_compat_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.installedVanillaVersions.forEach { versionId ->
                val count = state.compatibility[versionId]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = versionId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (count == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.loader_manager_compat_count,
                                count
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (count > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoaderStatusText(status: LoaderStatus) {
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
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}