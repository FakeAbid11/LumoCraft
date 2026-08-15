package com.lumocraft.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.BuildConfig
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.core.theme.ChipShape
import com.lumocraft.app.core.theme.LumoCraftTheme
import com.lumocraft.app.core.theme.LumoDimens
import com.lumocraft.app.core.theme.PanelShape
import com.lumocraft.app.core.theme.lumoColors
import com.lumocraft.app.ui.components.LumoPanel
import com.lumocraft.app.ui.components.LumoPlayButton

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
            .padding(LumoDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(LumoDimens.sectionGap)
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
        Spacer(modifier = Modifier.height(LumoDimens.tightGap))
        Text(
            text = stringResource(R.string.home_footer_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
// PLACEHOLDER_HOME_BODY

@Composable
private fun BrandCard(modifier: Modifier = Modifier) {
    val colors = lumoColors()
    // Hero panel: grass→diamond gradient fill with a hard beveled frame.
    LumoPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(colors.brandGradientStart, colors.brandGradientEnd)
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LumoDimens.tightGap)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
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

    LumoPanel(modifier = modifier.fillMaxWidth()) {
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
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
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
        Spacer(modifier = Modifier.height(LumoDimens.itemGap))
        LumoPlayButton(
            text = stringResource(R.string.home_play),
            icon = Icons.Filled.PlayArrow,
            onClick = onPlayClick,
            enabled = canPlay
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(LumoDimens.tightGap))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
// PLACEHOLDER_HOME_STATUS

@Composable
private fun StatusRow(
    selectedAccountUsername: String?,
    installedVersionsCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LumoDimens.itemGap)
    ) {
        StatusCard(
            title = stringResource(R.string.home_stat_profile),
            value = selectedAccountUsername ?: stringResource(R.string.home_stat_no_account),
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            title = stringResource(R.string.home_stat_versions),
            value = if (installedVersionsCount > 0) {
                pluralStringResource(R.plurals.versions_count, installedVersionsCount)
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
    LumoPanel(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlannedFeaturesCard(modifier: Modifier = Modifier) {
    LumoPanel(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_planned_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(LumoDimens.itemGap))
        Row(horizontalArrangement = Arrangement.spacedBy(LumoDimens.tightGap)) {
            listOf(
                R.string.home_planned_chip_mods,
                R.string.home_planned_chip_shaders,
                R.string.home_planned_chip_java
            ).forEach { labelRes ->
                SuggestionChip(
                    onClick = {},
                    shape = ChipShape,
                    label = { Text(stringResource(labelRes)) }
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


