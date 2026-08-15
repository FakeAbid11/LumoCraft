package com.lumocraft.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.update.UpdateStatus

/**
 * About section (Settings → About): installed version, a manual
 * "Check for updates" action and, when a newer release exists, a link to
 * the release page. Checks are read-only and user-initiated.
 */
@Composable
fun AboutSection(
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_section_about),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.about_version),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.versionDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    uiState.checking -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(2.dp)
                        )
                        Text(
                            text = stringResource(R.string.about_checking),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    uiState.status == UpdateStatus.UPDATE_AVAILABLE -> {
                        Text(
                            text = stringResource(
                                R.string.about_update_available,
                                uiState.latestVersion.orEmpty()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        uiState.releaseUrl?.let { url ->
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.about_view_release))
                            }
                        }
                    }
                    uiState.status == UpdateStatus.UP_TO_DATE -> Text(
                        text = stringResource(R.string.about_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.status == UpdateStatus.UNKNOWN && uiState.checked -> Text(
                        text = stringResource(R.string.about_update_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.status == UpdateStatus.UNKNOWN -> Unit
                }
                OutlinedButton(
                    onClick = viewModel::checkForUpdates,
                    enabled = !uiState.checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.about_check_updates))
                }
                Text(
                    text = stringResource(R.string.about_license_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.about_license_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
