package com.lumocraft.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import com.lumocraft.app.domain.runtime.JvmConfiguration
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeProgress
import com.lumocraft.app.domain.runtime.RuntimeStage
import com.lumocraft.app.domain.runtime.RuntimeStatus
import com.lumocraft.app.ui.components.InfoRow
import com.lumocraft.app.ui.components.LumoSectionPanel

@Composable
fun RuntimeSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: RuntimeViewModel = viewModel(factory = RuntimeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    LumoSectionPanel(
        title = stringResource(R.string.settings_section_runtime),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            uiState.architecture?.let { arch ->
                InfoRow(
                    label = stringResource(R.string.runtime_architecture),
                    value = arch.abi
                )
            }

            val runtime = uiState.defaultRuntime
            if (runtime != null) {
                RuntimeStatusCard(
                    runtime = runtime,
                    isActive = uiState.activeRuntimeId == runtime.id,
                    onVerify = { viewModel.verify(runtime.id) },
                    onRepair = { viewModel.repair(runtime.id) },
                    onRemove = { viewModel.remove(runtime.id) }
                )
            } else {
                Text(
                    text = stringResource(R.string.runtime_not_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.install("java17") },
                    enabled = uiState.activeRuntimeId == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_install_java17))
                }
                Button(
                    onClick = { viewModel.install("java21") },
                    enabled = uiState.activeRuntimeId == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_install_java21))
                }
            }

            uiState.progress?.let { progress ->
                RuntimeProgressBar(progress)
            }

            RamSettings(
                config = uiState.jvmConfig,
                onMaxMemoryChange = viewModel::setMaxMemory,
                onMinMemoryChange = viewModel::setMinMemory
            )
        }
    }
}

@Composable
private fun RuntimeStatusCard(
    runtime: RuntimeInfo,
    isActive: Boolean,
    onVerify: () -> Unit,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = runtime.id,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${runtime.vendor} ${runtime.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (runtime.status) {
                RuntimeStatus.INSTALLED -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.runtime_installed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RuntimeStatus.CORRUPTED -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.runtime_corrupted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                RuntimeStatus.MISSING -> Text(
                    text = stringResource(R.string.runtime_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RuntimeStatus.VERIFYING -> Text(
                    text = stringResource(R.string.runtime_verifying),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onVerify,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_verify))
                }
                OutlinedButton(
                    onClick = onRepair,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_repair))
                }
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_remove))
                }
            }
        }
    }
}

@Composable
private fun RuntimeProgressBar(progress: RuntimeProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(
                when (progress.stage) {
                    RuntimeStage.PREPARING -> R.string.runtime_stage_preparing
                    RuntimeStage.DOWNLOADING -> R.string.runtime_stage_downloading
                    RuntimeStage.EXTRACTING -> R.string.runtime_stage_extracting
                    RuntimeStage.VERIFYING -> R.string.runtime_stage_verifying
                    RuntimeStage.COMPLETE -> R.string.runtime_stage_complete
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val fraction = progress.fraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
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
    }
}

@Composable
private fun RamSettings(
    config: JvmConfiguration,
    onMaxMemoryChange: (Int) -> Unit,
    onMinMemoryChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.runtime_ram_settings),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.runtime_max_ram, config.maxMemoryMB),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = config.maxMemoryMB.toFloat(),
            onValueChange = { onMaxMemoryChange(it.toInt()) },
            valueRange = JvmConfiguration.MIN_RAM_MB.toFloat()..JvmConfiguration.MAX_RAM_MB.toFloat(),
            steps = (JvmConfiguration.MAX_RAM_MB - JvmConfiguration.MIN_RAM_MB) / 256 - 1
        )
        Text(
            text = stringResource(R.string.runtime_min_ram, config.minMemoryMB),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = config.minMemoryMB.toFloat(),
            onValueChange = { onMinMemoryChange(it.toInt()) },
            valueRange = 128f..config.maxMemoryMB.toFloat(),
            steps = (config.maxMemoryMB - 128) / 64 - 1
        )
        Text(
            text = stringResource(
                R.string.runtime_recommended_ram,
                JvmConfiguration.RECOMMENDED_RAM_MB
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}