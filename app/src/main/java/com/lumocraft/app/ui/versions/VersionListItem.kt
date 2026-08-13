package com.lumocraft.app.ui.versions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumocraft.app.R
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.MinecraftVersion
import com.lumocraft.app.domain.version.VersionType

@Composable
fun VersionListItem(
    version: MinecraftVersion,
    state: InstallState?,
    loaderInstances: List<LoaderInstance>,
    isInstalling: Boolean,
    progress: InstallProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = version.id,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        when (state) {
                            InstallState.INSTALLED -> {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.versions_installed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            InstallState.CORRUPTED -> Text(
                                text = stringResource(R.string.versions_corrupted),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Unit
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = buildString {
                                append(typeLabel(version.type))
                                formatReleaseDate(version.releaseTime)?.let { date ->
                                    append("  ·  ").append(date)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Badge(
                            text = stringResource(R.string.loader_badge_vanilla),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (loaderInstances.isNotEmpty()) {
                            Badge(
                                text = stringResource(R.string.loader_badge_fabric),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                when (state) {
                    InstallState.PENDING -> StatusLabel(
                        text = stringResource(R.string.versions_pending),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InstallState.FAILED -> StatusLabel(
                        text = stringResource(R.string.versions_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }
            }
            if (isInstalling) {
                val percentage = progress?.percentage
                if (percentage != null) {
                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1
    )
}

@Composable
fun typeLabel(type: VersionType): String = when (type) {
    VersionType.RELEASE -> stringResource(R.string.version_type_release)
    VersionType.SNAPSHOT -> stringResource(R.string.version_type_snapshot)
    VersionType.OLD_BETA -> stringResource(R.string.version_type_old_beta)
    VersionType.OLD_ALPHA -> stringResource(R.string.version_type_old_alpha)
    VersionType.UNKNOWN -> stringResource(R.string.version_type_unknown)
}
