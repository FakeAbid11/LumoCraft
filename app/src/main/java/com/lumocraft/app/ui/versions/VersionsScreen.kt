package com.lumocraft.app.ui.versions

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.MinecraftVersion
import com.lumocraft.app.domain.version.VersionFilter

/** One removal request waiting for the confirm dialog. */
private sealed interface PendingRemoval {
    data class Version(val version: MinecraftVersion) : PendingRemoval
    data class Loader(val instance: LoaderInstance) : PendingRemoval
}

/**
 * Safe area pipeline of the Versions tab:
 * rare network state handling (skeleton / error / empty), instant local
 * search + type filters, cached manifest display and a details sheet
 * that drives installation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(
    modifier: Modifier = Modifier,
    onOpenLoaders: () -> Unit = {},
    viewModel: VersionViewModel = viewModel(factory = VersionViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedVersion by remember { mutableStateOf<MinecraftVersion?>(null) }
    var pendingRemoval by remember { mutableStateOf<PendingRemoval?>(null) }

    LaunchedEffect(uiState.errorMessageRes) {
        uiState.errorMessageRes?.let { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
            viewModel.clearInstallError()
        }
    }

    LaunchedEffect(selectedVersion) {
        selectedVersion?.let { viewModel.fetchLoaderVersions(it.id) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onOpenLoaders) {
                        Text(stringResource(R.string.loader_manager_open))
                    }
                }
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    placeholder = { Text(stringResource(R.string.versions_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription =
                                        stringResource(R.string.versions_search_clear)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                FilterChipRow(
                    selected = uiState.filter,
                    onSelect = viewModel::setFilter
                )
                when {
                    uiState.manifest == null && uiState.isLoading -> VersionListSkeleton()
                    uiState.manifest == null -> VersionLoadError(onRetry = viewModel::refresh)
                    uiState.visibleVersions.isEmpty() -> VersionEmptyState()
                    else -> VersionList(
                        versions = uiState.visibleVersions,
                        installStates = uiState.installStates,
                        installedLoaders = uiState.installedLoaders,
                        installingId = uiState.installingId,
                        installProgress = uiState.installProgress,
                        onVersionClick = { selectedVersion = it }
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    selectedVersion?.let { version ->
        ModalBottomSheet(onDismissRequest = { selectedVersion = null }) {
            VersionDetailsSheet(
                version = version,
                state = uiState.installStates[version.id],
                loaders = uiState.loadersFor(version.id),
                loaderVersionsState = uiState.loaderVersions[version.id],
                progress = if (uiState.installingId == version.id) {
                    uiState.installProgress
                } else {
                    null
                },
                loaderProgress = uiState.loaderInstallProgress,
                installingLoaderId = uiState.installingLoaderId,
                onInstall = { viewModel.install(version) },
                onRepair = { viewModel.repair(version) },
                onRemove = { pendingRemoval = PendingRemoval.Version(version) },
                onRetryLoaderVersions = { viewModel.retryLoaderVersions(version.id) },
                onInstallLoader = { loaderVersion ->
                    viewModel.installLoader(version.id, loaderVersion)
                },
                onRepairLoader = { instanceId -> viewModel.repairLoader(instanceId) },
                onRemoveLoader = { instanceId ->
                    uiState.installedLoaders[instanceId]?.let { instance ->
                        pendingRemoval = PendingRemoval.Loader(instance)
                    }
                }
            )
        }
    }

    pendingRemoval?.let { removal ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = {
                Text(
                    stringResource(
                        if (removal is PendingRemoval.Loader) {
                            R.string.loader_remove_confirm_title
                        } else {
                            R.string.versions_remove_confirm_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (removal is PendingRemoval.Loader) {
                            R.string.loader_remove_confirm_message
                        } else {
                            R.string.versions_remove_confirm_message
                        },
                        when (removal) {
                            is PendingRemoval.Loader -> removal.instance.metadata.minecraftVersion
                            is PendingRemoval.Version -> removal.version.id
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (removal) {
                            is PendingRemoval.Loader ->
                                viewModel.removeLoader(removal.instance.instanceId)
                            is PendingRemoval.Version ->
                                viewModel.remove(removal.version)
                        }
                        pendingRemoval = null
                    }
                ) {
                    Text(stringResource(R.string.loader_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FilterChipRow(
    selected: VersionFilter,
    onSelect: (VersionFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VersionFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filterLabelRes(filter))) }
            )
        }
    }
}

@Composable
private fun filterLabelRes(filter: VersionFilter): Int = when (filter) {
    VersionFilter.ALL -> R.string.version_filter_all
    VersionFilter.RELEASE -> R.string.version_filter_release
    VersionFilter.SNAPSHOT -> R.string.version_filter_snapshot
    VersionFilter.OLD_BETA -> R.string.version_filter_old_beta
    VersionFilter.OLD_ALPHA -> R.string.version_filter_old_alpha
}

@Composable
private fun VersionListSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(8) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )
            }
        }
    }
}

@Composable
private fun VersionLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.versions_load_error),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.versions_load_error_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.versions_retry))
        }
    }
}

@Composable
private fun VersionEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.versions_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.versions_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VersionList(
    versions: List<MinecraftVersion>,
    installStates: Map<String, InstallState>,
    installedLoaders: Map<String, LoaderInstance>,
    installingId: String?,
    installProgress: InstallProgress?,
    onVersionClick: (MinecraftVersion) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(versions, key = { it.id }) { version ->
            VersionListItem(
                version = version,
                state = installStates[version.id],
                loaderInstances = installedLoaders.values
                    .filter { it.metadata.minecraftVersion == version.id },
                isInstalling = installingId == version.id,
                progress = installProgress,
                onClick = { onVersionClick(version) }
            )
        }
    }
}