package com.lumocraft.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.native.RendererType
import com.lumocraft.app.domain.native.ResolutionScale

/**
 * Renderer settings: profile (Compatibility/Performance/Experimental),
 * resolution scale (50/75/100%), FPS limit, VSync and native status.
 * Every control persists through [RendererSettingsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RendererSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: RendererSettingsViewModel = viewModel(factory = RendererSettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.profile

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_section_renderer),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChoiceSetting(
                    label = stringResource(R.string.renderer_profile),
                    options = RendererType.entries,
                    selected = profile.renderer,
                    optionLabel = { type ->
                        stringResource(
                            when (type) {
                                RendererType.COMPATIBILITY -> R.string.renderer_profile_compatibility
                                RendererType.PERFORMANCE -> R.string.renderer_profile_performance
                                RendererType.EXPERIMENTAL -> R.string.renderer_profile_experimental
                            }
                        )
                    },
                    onSelect = viewModel::selectRenderer
                )
                ChoiceSetting(
                    label = stringResource(R.string.renderer_resolution),
                    options = ResolutionScale.entries,
                    selected = profile.resolutionScale,
                    optionLabel = { scale ->
                        stringResource(R.string.renderer_resolution_percent, scale.percent)
                    },
                    onSelect = viewModel::selectResolutionScale
                )
                ChoiceSetting(
                    label = stringResource(R.string.renderer_fps_limit),
                    options = FPS_OPTIONS,
                    selected = profile.fpsLimit,
                    optionLabel = { limit ->
                        if (limit == null) {
                            stringResource(R.string.renderer_fps_unlimited)
                        } else {
                            stringResource(R.string.renderer_fps_value, limit)
                        }
                    },
                    onSelect = viewModel::selectFpsLimit
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.renderer_vsync),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = profile.vsync,
                        onCheckedChange = viewModel::setVsync
                    )
                }
                NativeStatusRow(
                    status = uiState.nativeStatus,
                    arch = uiState.architecture?.abi
                )
            }
        }
    }
}

@Composable
private fun NativeStatusRow(
    status: NativeStatus,
    arch: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.renderer_native_status),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            arch?.let {
                Text(
                    text = stringResource(R.string.renderer_native_arch, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when (status) {
            NativeStatus.READY -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.native_status_ready),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            NativeStatus.CORRUPTED -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.native_status_corrupted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> Text(
                text = stringResource(R.string.native_status_not_prepared),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSetting(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) {
                    Text(
                        text = optionLabel(option),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private val FPS_OPTIONS = listOf(30, 60, 120, null)