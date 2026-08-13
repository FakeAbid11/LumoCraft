package com.lumocraft.app.ui.launch

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.data.launch.LauncherLogRepository
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchPipeline
import com.lumocraft.app.domain.launch.LaunchProgress
import com.lumocraft.app.domain.launch.LaunchState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Immutable state for the Launch screen. */
data class LaunchUiState(
    val versionId: String? = null,
    val progress: LaunchProgress = LaunchProgress(),
    val logLines: List<String> = emptyList(),
    val logFile: File? = null,
    val logFileExported: Boolean = false
) {
    val active: Boolean get() = progress.isRunning
}

/**
 * Bridges [LaunchPipeline] and [LauncherLogRepository] to the UI:
 * auto-launches the pending context on first composition, streams the
 * session log into the console and exposes Cancel/Retry/Open logs.
 */
class LaunchViewModel(
    private val application: LumoCraftApplication,
    private val pipeline: LaunchPipeline,
    private val logs: LauncherLogRepository,
) : ViewModel() {

    private val _logFileExported = MutableStateFlow(false)
    private val _pendingContext = MutableStateFlow(application.pendingLaunchContext)

    val uiState: StateFlow<LaunchUiState> = combine(
        _pendingContext,
        pipeline.state,
        pipeline.logLines,
        _logFileExported
    ) { context, launchProgress, lines, exported ->
        LaunchUiState(
            versionId = context?.versionId,
            progress = launchProgress,
            logLines = lines,
            logFile = logs.currentSessionFile(),
            logFileExported = exported
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LaunchUiState(versionId = _pendingContext.value?.versionId)
    )

    init {
        val context = _pendingContext.value
        if (context != null) {
            // Consumed: subsequent visits must bring their own context.
            application.pendingLaunchContext = null
            if (pipeline.state.value.state == LaunchState.IDLE) {
                pipeline.launch(context)
            }
        }
    }

    fun retry() {
        val context = _pendingContext.value ?: return
        val current = pipeline.state.value.state
        if (current == LaunchState.FAILED || current == LaunchState.FINISHED) {
            pipeline.launch(context)
        }
    }

    fun cancel() {
        pipeline.cancel()
    }

    fun openLogs() {
        val file = logs.currentSessionFile() ?: return
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.logs",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { application.startActivity(intent) }
        _logFileExported.value = true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                LaunchViewModel(
                    application = application,
                    pipeline = application.launchPipeline,
                    logs = application.launcherLogRepository
                )
            }
        }
    }
}