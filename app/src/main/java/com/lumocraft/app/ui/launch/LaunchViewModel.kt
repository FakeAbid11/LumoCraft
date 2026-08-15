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
import com.lumocraft.app.ui.game.GameActivity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.utils.JREUtils

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
    private val _logLines = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<LaunchUiState> = combine(
        _pendingContext,
        pipeline.state,
        _logLines,
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
                startGameViewIfRenderable()
                pipeline.launch(context)
            }
        }
        viewModelScope.launch {
            pipeline.logLines.collect { line ->
                _logLines.update { (it + line).takeLast(MAX_LOG_LINES) }
            }
        }
    }

    fun retry() {
        val context = _pendingContext.value ?: return
        val current = pipeline.state.value.state
        if (current == LaunchState.FAILED || current == LaunchState.FINISHED) {
            startGameViewIfRenderable()
            pipeline.launch(context)
        }
    }

    /**
     * Opens the full-screen [GameActivity] so its render surface exists before
     * the in-process JVM reaches LWJGL init, and tells the pipeline to wait
     * for it. Only when the PojavLauncher rendering bridge actually loaded:
     * without the vendored natives (CI / not-yet-fetched builds) the bridge is
     * absent, so the launch stays on the console screen exactly as before.
     */
    private fun startGameViewIfRenderable() {
        if (JREUtils.ensureLoaded() != null) return
        application.gameSurfaceGate.expectSurface()
        val intent = Intent(application, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { application.startActivity(intent) }
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
        const val MAX_LOG_LINES = 500

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