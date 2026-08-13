package com.lumocraft.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumocraft.app.R
import com.lumocraft.app.domain.model.ThemeMode
import com.lumocraft.app.ui.input.InputSettingsSection

/**
 * Settings section. Appearance, Java runtime and renderer settings are
 * live; the input section adds profiles, sensitivity and control layout;
 * the launcher section links to Diagnostics and the About section offers
 * a manual update check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onEditLayout: () -> Unit,
    onPreviewControls: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPerformance: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
            ThemeSettingRow(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
        RuntimeSettingsSection()
        RendererSettingsSection()
        SettingsSection(title = stringResource(R.string.settings_section_performance)) {
            PerformanceEntryRow(onOpenPerformance)
        }
        InputSettingsSection(
            onEditLayout = onEditLayout,
            onPreviewControls = onPreviewControls
        )
        SettingsSection(title = stringResource(R.string.settings_section_launcher)) {
            DiagnosticsEntryRow(onOpenDiagnostics)
        }
        AboutSection()
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PerformanceEntryRow(
    onOpenPerformance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onOpenPerformance,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.performance_open_dashboard),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
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
            Column {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingRow(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsEntryRow(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onOpenDiagnostics,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_open_diagnostics),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}