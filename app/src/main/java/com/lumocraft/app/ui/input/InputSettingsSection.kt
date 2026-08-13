package com.lumocraft.app.ui.input

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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

/**
 * Input settings: profile selection/duplication, sensitivity, invert Y,
 * cursor speed, button opacity, controller and keyboard toggles with
 * connection status, plus shortcuts to the layout editor and the live
 * controls preview. Everything persists through [InputSettingsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSettingsSection(
    onEditLayout: () -> Unit,
    onPreviewControls: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InputSettingsViewModel = viewModel(factory = InputSettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.activeProfile
    if (profile == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_section_input),
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
                ProfileRow(
                    profiles = uiState.profiles,
                    activeId = profile.id,
                    onSelect = viewModel::selectProfile,
                    onDuplicate = viewModel::duplicateProfile
                )
                SliderRow(
                    label = stringResource(R.string.input_sensitivity),
                    value = profile.sensitivity,
                    valueRange = 0.2f..3f,
                    onValueChange = viewModel::setSensitivity
                )
                SwitchRow(
                    label = stringResource(R.string.input_invert_y),
                    checked = profile.invertY,
                    onCheckedChange = viewModel::setInvertY
                )
                SliderRow(
                    label = stringResource(R.string.input_cursor_speed),
                    value = uiState.settings.cursorSpeed,
                    valueRange = 0.25f..3f,
                    onValueChange = viewModel::setCursorSpeed
                )
                SliderRow(
                    label = stringResource(R.string.input_button_opacity),
                    value = uiState.settings.buttonOpacity,
                    valueRange = 0.1f..1f,
                    onValueChange = viewModel::setButtonOpacity
                )
                SwitchRow(
                    label = stringResource(R.string.input_controller),
                    checked = profile.controllerEnabled,
                    onCheckedChange = viewModel::setControllerEnabled,
                    status = if (uiState.controllerConnected) {
                        uiState.controllerName ?: stringResource(R.string.input_connected)
                    } else {
                        stringResource(R.string.input_disconnected)
                    }
                )
                SwitchRow(
                    label = stringResource(R.string.input_keyboard),
                    checked = profile.keyboardEnabled,
                    onCheckedChange = viewModel::setKeyboardEnabled,
                    status = if (uiState.keyboardConnected) {
                        stringResource(R.string.input_connected)
                    } else {
                        stringResource(R.string.input_disconnected)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditLayout) {
                        Text(stringResource(R.string.input_edit_layout))
                    }
                    OutlinedButton(onClick = onPreviewControls) {
                        Text(stringResource(R.string.input_preview))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileRow(
    profiles: List<com.lumocraft.app.domain.input.InputProfile>,
    activeId: String,
    onSelect: (String) -> Unit,
    onDuplicate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.input_profile),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(onClick = onDuplicate) {
                Text(stringResource(R.string.input_profile_duplicate))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEach { item ->
                FilterChip(
                    selected = item.id == activeId,
                    onClick = { onSelect(item.id) },
                    label = { Text(item.name) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    status: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}