package com.lumocraft.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.BuildConfig
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.core.theme.LumoCraftTheme
import com.lumocraft.app.core.theme.lumoColors

/**
 * Home: brand, version picker + Play gated on launch readiness, account
 * and version overview. Play navigates to the launch screen with the
 * pipeline already started via [onPlay].
 */
@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val state by viewModel.uiState.collectAsState()

    val playHint = when {
        state.canPlay -> null
        state.selectedAccount == null -> context.getString(R.string.home_play_need_account)
        state.runtime == null || !state.readiness.runtimeOk ->
            context.getString(R.string.home_play_need_runtime)
        state.selectedVersionId == null ->
            context.getString(R.string.home_play_need_version)
        state.readiness.missingLibraries.isNotEmpty() ||
            state.readiness.missingAssets > 0 ||
            !state.readiness.assetIndexOk ->
            context.getString(R.string.home_play_need_repair)
        else -> context.getString(R.string.home_play_unavailable)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BrandCard()
        PlayCard(
            selectedVersionId = state.selectedVersionId,
            installedVersions = state.installedVersions,
            canPlay = state.canPlay,
            hint = playHint,
            onVersionSelected = viewModel::selectVersion,
            onPlayClick = {
                val launchContext = viewModel.buildLaunchContext()
                if (launchContext != null) {
                    (context.applicationContext as LumoCraftApplication)
                        .pendingLaunchContext = launchContext
                    onPlay()
                } else {
                    Toast.makeText(
                        context,
                        playHint ?: context.getString(R.string.home_play_unavailable),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        StatusRow(
            selectedAccountUsername = state.selectedAccount?.username,
            installedVersionsCount = state.installedVersions.size
        )
        PlannedFeaturesCard()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_footer_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun BrandCard(modifier: Modifier = Modifier) {
    val colors = lumoColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.brandGradientStart,
                            colors.brandGradientEnd
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun PlayCard(
    selectedVersionId: String?,
    installedVersions: List<String>,
    canPlay: Boolean,
    hint: String?,
    onVersionSelected: (String) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.home_version_selection),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (installedVersions.isNotEmpty()) {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text(
                            text = selectedVersionId
                                ?: stringResource(R.string.home_version_none),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        installedVersions.forEach { versionId ->
                            DropdownMenuItem(
                                text = { Text(versionId) },
                                onClick = {
                                    menuExpanded = false
                                    onVersionSelected(versionId)
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_version_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPlayClick,
                enabled = canPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.home_play),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (hint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    selectedAccountUsername: String?,
    installedVersionsCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(
            title = stringResource(R.string.home_stat_profile),
            value = selectedAccountUsername ?: stringResource(R.string.home_stat_no_account),
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            title = stringResource(R.string.home_stat_versions),
            value = if (installedVersionsCount > 0) {
                stringResource(R.plurals.versions_count, installedVersionsCount)
            } else {
                stringResource(R.string.home_stat_no_versions)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlannedFeaturesCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_planned_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.home_planned_chip_mods)) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.home_planned_chip_shaders)) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.home_planned_chip_java)) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LumoCraftTheme {
        HomeScreen(onPlay = {})
    }
}