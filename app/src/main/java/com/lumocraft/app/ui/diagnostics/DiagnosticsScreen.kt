package com.lumocraft.app.ui.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.loader.LoaderMetadata
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.runtime.RuntimeInfo
import java.util.Locale

/**
 * Diagnostics (Settings → Diagnostics): every hardware/software fact
 * relevant to a bug report, plus actions to export the logs or a full
 * diagnostics archive (shared through the OS share sheet), clear logs
 * and clear the launch cache.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val shareIntent by viewModel.shareIntent.collectAsState()

    LaunchedEffect(shareIntent) {
        shareIntent?.let {
            context.startActivity(it)
            viewModel.consumeShare()
        }
    }
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
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
                    contentDescription = stringResource(R.string.diagnostics_back)
                )
            }
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        DiagnosticsSection(title = stringResource(R.string.diagnostics_app)) {
            AppCard(uiState.appVersionDisplay)
        }
        DiagnosticsSection(title = stringResource(R.string.diagnostics_hardware)) {
            DeviceCard(uiState.deviceProfile)
        }
        DiagnosticsSection(title = stringResource(R.string.diagnostics_launch)) {
            LaunchStateCard(
                runtime = uiState.defaultRuntime,
                versionId = uiState.selectedVersionId,
                loader = uiState.activeLoader,
                nativeArch = uiState.nativeArch
            )
        }
        DiagnosticsSection(title = stringResource(R.string.diagnostics_logs)) {
            LogsCard(
                logCount = uiState.logCount,
                busy = uiState.busy,
                onExportLogs = viewModel::exportLogs,
                onExportDiagnostics = viewModel::exportDiagnostics,
                onClearLogs = viewModel::clearLogs
            )
        }
        OutlinedButton(
            onClick = viewModel::clearCache,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.diagnostics_clear_cache))
        }
        uiState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DiagnosticsSection(
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
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AppCard(appVersionDisplay: String) {
    InfoRow(
        label = stringResource(R.string.diagnostics_version),
        value = appVersionDisplay
    )
}

@Composable
private fun DeviceCard(profile: DeviceProfile?) {
    if (profile == null) {
        Text(
            text = stringResource(R.string.diagnostics_no_device_profile),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(
            label = stringResource(R.string.diagnostics_device),
            value = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_android),
            value = "Android ${profile.androidRelease} (SDK ${profile.androidSdk})"
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_arch),
            value = profile.architecture.abi
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_ram),
            value = formatRam(profile.totalRamMB)
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_tier),
            value = profile.tier.name
        )
    }
}

@Composable
private fun LaunchStateCard(
    runtime: RuntimeInfo?,
    versionId: String?,
    loader: LoaderMetadata?,
    nativeArch: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(
            label = stringResource(R.string.diagnostics_runtime),
            value = runtime?.let { "Java ${it.version} · ${it.status.name.lowercase(Locale.US)}" }
                ?: stringResource(R.string.diagnostics_runtime_none)
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_version),
            value = versionId ?: stringResource(R.string.diagnostics_no_version)
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_loader),
            value = loader?.let {
                "${it.type.displayName} ${it.loaderVersion} (MC ${it.minecraftVersion})"
            } ?: stringResource(R.string.diagnostics_no_loader)
        )
        InfoRow(
            label = stringResource(R.string.diagnostics_native_arch),
            value = nativeArch
        )
    }
}

@Composable
private fun LogsCard(
    logCount: Int,
    busy: Boolean,
    onExportLogs: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoRow(
            label = stringResource(R.string.diagnostics_log_files),
            value = logCount.toString()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExportLogs,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.diagnostics_export_logs))
            }
            OutlinedButton(
                onClick = onExportDiagnostics,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.diagnostics_export_diagnostics))
            }
        }
        OutlinedButton(
            onClick = onClearLogs,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.diagnostics_clear_logs))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatRam(mb: Long): String = when {
    mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024f)
    else -> "$mb MB"
}
