package com.lumocraft.app.ui.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.core.theme.LumoCraftTheme
import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchState

/**
 * Full-screen launch session: stage card, live console, and
 * Cancel / Retry / Open logs. The system back button leaves the session
 * running; only Cancel stops the Java process.
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val viewModel: LaunchViewModel = viewModel(factory = LaunchViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val listState = rememberLazyListState()
    LaunchedEffect(state.logLines.size) {
        if (state.logLines.isNotEmpty()) {
            listState.animateScrollToItem(state.logLines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StageCard(
            versionId = state.versionId,
            progress = state.progress,
            onCancel = viewModel::cancel,
            onRetry = viewModel::retry,
            onOpenLogs = {
                val file = state.logFile
                if (file != null && file.isFile) {
                    viewModel.openLogs()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.launch_logs_unavailable),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            logFileAvailable = state.logFile?.isFile == true,
            retryEnabled = !state.active && state.versionId != null
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.launch_console_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.logLines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageCard(
    versionId: String?,
    progress: com.lumocraft.app.domain.launch.LaunchProgress,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenLogs: () -> Unit,
    logFileAvailable: Boolean,
    retryEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = versionId
                            ?.let { stringResource(R.string.launch_title, it) }
                            ?: stringResource(R.string.launch_title_unknown),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = progress.message
                            ?: stringResource(stageLabel(progress.state)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (progress.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Text(
                        text = when (progress.state) {
                            LaunchState.FINISHED -> contextString(R.string.launch_stage_finished)
                            LaunchState.FAILED -> contextString(R.string.launch_stage_failed)
                            else -> contextString(R.string.launch_stage_idle)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (progress.state == LaunchState.FAILED && progress.failure != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = contextString(errorLabel(progress.failure.type)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                if (!progress.failure.detail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.failure.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    progress.isRunning -> Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.launch_cancel))
                    }
                    retryEnabled -> OutlinedButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.launch_retry))
                    }
                }
                if (logFileAvailable) {
                    OutlinedButton(onClick = onOpenLogs) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.launch_open_logs))
                    }
                }
            }
        }
    }
}

@Composable
private fun contextString(resId: Int): String = stringResource(resId)

private fun stageLabel(state: LaunchState): Int = when (state) {
    LaunchState.PREPARING -> R.string.launch_stage_preparing
    LaunchState.VALIDATING -> R.string.launch_stage_validating
    LaunchState.BUILDING_CLASSPATH -> R.string.launch_stage_classpath
    LaunchState.BUILDING_ARGUMENTS -> R.string.launch_stage_arguments
    LaunchState.STARTING_JAVA -> R.string.launch_stage_starting
    LaunchState.RUNNING -> R.string.launch_stage_running
    LaunchState.FINISHED -> R.string.launch_stage_finished
    LaunchState.FAILED -> R.string.launch_stage_failed
    LaunchState.IDLE -> R.string.launch_stage_idle
}

private fun errorLabel(type: LaunchErrorType): Int = when (type) {
    LaunchErrorType.ACCOUNT_MISSING -> R.string.launch_error_account
    LaunchErrorType.RUNTIME_MISSING -> R.string.launch_error_runtime
    LaunchErrorType.VERSION_MISSING -> R.string.launch_error_version
    LaunchErrorType.LIBRARIES_MISSING -> R.string.launch_error_libraries
    LaunchErrorType.ASSETS_MISSING -> R.string.launch_error_assets
    LaunchErrorType.CLIENT_JAR_MISSING -> R.string.launch_error_client_jar
    LaunchErrorType.MAIN_CLASS_MISSING -> R.string.launch_error_main_class
    LaunchErrorType.NATIVE_LIBRARY_MISSING -> R.string.launch_error_native
    LaunchErrorType.NATIVE_ARCH_MISMATCH -> R.string.launch_error_native_arch
    LaunchErrorType.NATIVE_CORRUPTED -> R.string.launch_error_native_corrupted
    LaunchErrorType.INVALID_CLASSPATH -> R.string.launch_error_classpath
    LaunchErrorType.JVM_INITIALIZATION_FAILURE -> R.string.launch_error_jvm
    LaunchErrorType.GAME_CRASHED -> R.string.launch_error_crashed
    LaunchErrorType.CANCELLED -> R.string.launch_error_cancelled
    LaunchErrorType.UNKNOWN -> R.string.launch_error_unknown
}

@Preview(showBackground = true)
@Composable
private fun LaunchScreenPreview() {
    LumoCraftTheme {
        LaunchScreen()
    }
}