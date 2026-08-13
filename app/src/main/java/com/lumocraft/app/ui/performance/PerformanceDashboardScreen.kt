package com.lumocraft.app.ui.performance

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.performance.DeviceTier
import com.lumocraft.app.domain.performance.JvmProfile
import com.lumocraft.app.domain.performance.LaunchHistory
import com.lumocraft.app.domain.performance.LaunchTimings
import java.util.Locale

/**
 * Performance dashboard (Settings → Performance): device profile, JVM
 * profile selection (automatic or manual override), launch history and
 * cache stats, plus Clear cache / Rebuild cache / Reset performance
 * settings.
 */
@Composable
fun PerformanceDashboardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerformanceViewModel = viewModel(factory = PerformanceViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PerformanceSection(title = stringResource(R.string.performance_device_profile)) {
            DeviceProfileCard(uiState.deviceProfile)
        }
        PerformanceSection(title = stringResource(R.string.performance_jvm_profile)) {
            JvmProfileCard(
                selected = uiState.jvmProfile,
                automatic = uiState.automatic,
                enabled = !uiState.busy,
                onSelect = viewModel::selectJvmProfile
            )
        }
        PerformanceSection(title = stringResource(R.string.performance_launch_performance)) {
            LaunchHistoryCard(uiState.history)
        }
        PerformanceSection(title = stringResource(R.string.performance_cache)) {
            CacheCard(
                items = uiState.cacheStats.itemCount,
                sizeBytes = uiState.cacheStats.sizeBytes,
                hits = uiState.cacheStats.hits,
                misses = uiState.cacheStats.misses,
                busy = uiState.busy,
                onClear = viewModel::clearCache,
                onRebuild = viewModel::rebuildCache
            )
        }
        OutlinedButton(
            onClick = viewModel::resetSettings,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.performance_reset_settings))
        }
        uiState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.performance_back))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PerformanceSection(
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
private fun DeviceProfileCard(profile: DeviceProfile?) {
    if (profile == null) {
        Text(
            text = stringResource(R.string.performance_device_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(
            label = stringResource(R.string.performance_tier),
            value = stringResource(tierLabel(profile.tier))
        )
        InfoRow(
            label = stringResource(R.string.performance_ram),
            value = stringResource(
                R.string.performance_ram_value,
                formatRam(profile.totalRamMB),
                profile.recommendedMaxRamMB()
            )
        )
        InfoRow(
            label = stringResource(R.string.performance_cpu_cores),
            value = profile.cpuCores.toString()
        )
        InfoRow(
            label = stringResource(R.string.performance_android),
            value = "Android ${profile.androidRelease} (SDK ${profile.androidSdk})"
        )
        InfoRow(
            label = stringResource(R.string.performance_architecture),
            value = profile.architecture.abi
        )
        InfoRow(
            label = stringResource(R.string.performance_low_ram),
            value = stringResource(
                if (profile.lowRamDevice) R.string.performance_yes else R.string.performance_no
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JvmProfileCard(
    selected: JvmProfile?,
    automatic: Boolean,
    enabled: Boolean,
    onSelect: (JvmProfile?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val options = listOf(
            null to stringResource(R.string.performance_jvm_auto),
            JvmProfile.BATTERY_SAVER to stringResource(R.string.performance_jvm_battery),
            JvmProfile.BALANCED to stringResource(R.string.performance_jvm_balanced),
            JvmProfile.PERFORMANCE to stringResource(R.string.performance_jvm_performance)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (profile, label) ->
                SegmentedButton(
                    selected = if (automatic) profile == null else selected == profile,
                    onClick = { onSelect(profile) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.performance_jvm_auto_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LaunchHistoryCard(history: LaunchHistory) {
    val last = history.lastLaunch
    if (last == null) {
        Text(
            text = stringResource(R.string.performance_never_launched),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(
            label = stringResource(R.string.performance_last_launch),
            value = launchSummary(last)
        )
        history.fastestLaunch?.let { fastest ->
            InfoRow(
                label = stringResource(R.string.performance_fastest_launch),
                value = formatDuration(fastest.totalMs)
            )
        }
        InfoRow(
            label = stringResource(R.string.performance_launch_count),
            value = history.launches.toString()
        )
    }
}

@Composable
private fun CacheCard(
    items: Int,
    sizeBytes: Long,
    hits: Long,
    misses: Long,
    busy: Boolean,
    onClear: () -> Unit,
    onRebuild: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoRow(
            label = stringResource(R.string.performance_cache_items),
            value = items.toString()
        )
        InfoRow(
            label = stringResource(R.string.performance_cache_size),
            value = formatBytes(sizeBytes)
        )
        InfoRow(
            label = stringResource(R.string.performance_cache_ratio),
            value = "$hits / $misses"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.performance_clear_cache))
            }
            OutlinedButton(
                onClick = onRebuild,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.performance_rebuild_cache))
            }
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

private fun launchSummary(timings: LaunchTimings): String =
    formatDuration(timings.totalMs) +
        " · v${timings.validationMs}ms" +
        if (timings.cachedValidation) " (cached)" else ""

private fun tierLabel(tier: DeviceTier): Int = when (tier) {
    DeviceTier.LOW -> R.string.performance_tier_low
    DeviceTier.MEDIUM -> R.string.performance_tier_medium
    DeviceTier.HIGH -> R.string.performance_tier_high
}

private fun formatRam(mb: Long): String = when {
    mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024f)
    else -> "$mb MB"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

private fun formatDuration(ms: Long): String = when {
    ms >= 1000 -> String.format(Locale.US, "%.1f s", ms / 1000f)
    else -> "$ms ms"
}