package com.lumocraft.app.ui.versions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumocraft.app.R
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.MinecraftVersion

/**
 * Bottom-sheet content for one version: details, live install/repair
 * progress, install and repair actions. Updates in real time while the
 * pipeline is running.
 */
@Composable
fun VersionDetailsSheet(
    version: MinecraftVersion,
    state: InstallState?,
    progress: InstallProgress?,
    onInstall: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = version.id,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailTag(
                text = typeLabel(version.type),
                color = MaterialTheme.colorScheme.primary
            )
            when (state) {
                InstallState.INSTALLED -> DetailTag(
                    text = stringResource(R.string.versions_installed),
                    color = MaterialTheme.colorScheme.primary
                )
                InstallState.PENDING -> DetailTag(
                    text = stringResource(R.string.versions_pending),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InstallState.FAILED -> DetailTag(
                    text = stringResource(R.string.versions_failed),
                    color = MaterialTheme.colorScheme.error
                )
                InstallState.CORRUPTED -> DetailTag(
                    text = stringResource(R.string.versions_corrupted),
                    color = MaterialTheme.colorScheme.error
                )
                null -> Unit
            }
        }
        Text(
            text = formatReleaseDate(version.releaseTime)
                ?.let { stringResource(R.string.versions_released_on, it) }
                ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (progress != null) {
            InstallProgressSection(progress)
        } else {
            when (state) {
                InstallState.INSTALLED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { /* already installed */ },
                        enabled = false,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.versions_install_done))
                    }
                    Button(
                        onClick = onRepair,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(stringResource(R.string.versions_repair))
                    }
                }
                InstallState.CORRUPTED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRepair,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.versions_repair))
                    }
                }
                else -> Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.versions_install))
                }
            }
        }
    }
}

/** Live pipeline progress: stage, percentage bar, bytes and file counts. */
@Composable
private fun InstallProgressSection(progress: InstallProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stageLabel(progress.stage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            progress.percentage?.let { percentage ->
                Text(
                    text = stringResource(R.string.versions_percent, percentage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val percentage = progress.percentage
        if (percentage != null) {
            LinearProgressIndicator(
                progress = { percentage / 100f },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.versions_bytes_progress,
                    formatBytes(progress.downloadedBytes),
                    formatBytes(progress.totalBytes)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pluralStringResource(
                    R.plurals.versions_files_count,
                    progress.filesRemaining,
                    progress.filesRemaining
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailTag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .padding(end = 4.dp)
    )
}
