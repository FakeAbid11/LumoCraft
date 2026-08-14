package com.lumocraft.app.ui.diagnostics

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.core.version.VersionManager
import com.lumocraft.app.data.export.CrashReportExporter
import com.lumocraft.app.data.export.ExportKind
import com.lumocraft.app.data.export.ExportResult
import com.lumocraft.app.data.export.LogRedactor
import com.lumocraft.app.domain.loader.LoaderMetadata
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.runtime.RuntimeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the Diagnostics screen. */
data class DiagnosticsUiState(
    val appVersionDisplay: String = "",
    val deviceProfile: DeviceProfile? = null,
    val defaultRuntime: RuntimeInfo? = null,
    val selectedVersionId: String? = null,
    val activeLoader: LoaderMetadata? = null,
    val nativeArch: String = "",
    val logCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Aggregates every hardware/software fact the Diagnostics screen shows
 * and drives the export (logs-only or full diagnostics ZIP), log
 * clearing and cache clearing. Exports are handed to the OS share sheet
 * through [shareIntent] so the user can attach them to a bug report.
 */
class DiagnosticsViewModel(
    private val application: LumoCraftApplication,
    private val exporter: CrashReportExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private val _shareIntent = MutableStateFlow<Intent?>(null)
    val shareIntent: StateFlow<Intent?> = _shareIntent.asStateFlow()

    private val logRedactor: LogRedactor by lazy {
        LogRedactor(application, application.storageManager)
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val app = application
            val selectedVersion = app.versionPreference.loadSelectedVersionId()
            _uiState.update {
                it.copy(
                    appVersionDisplay = VersionManager.currentDisplayName(),
                    deviceProfile = app.performanceManager.deviceProfile(),
                    defaultRuntime = app.runtimeRepository.getDefaultRuntime(),
                    selectedVersionId = selectedVersion,
                    activeLoader = selectedVersion?.let { id ->
                        app.loaderRepository.resolveActiveLoader(id)?.metadata
                    },
                    nativeArch = app.nativeRuntimeManager.architecture().abi,
                    logCount = app.launcherLogRepository.listLogFiles().size
                )
            }
        }
    }

    fun exportLogs() = export(ExportKind.LOGS)

    fun exportDiagnostics() = export(ExportKind.DIAGNOSTICS)

    private fun export(kind: ExportKind) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val usernames = application.accountRepository.observeAccounts().first().mapNotNull { it.username }
            val result = exporter.export(
                kind = kind,
                redact = { line -> logRedactor.redact(line, usernames) }
            )
            result.fold(
                onSuccess = { res ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            message = application.getString(R.string.diagnostics_export_ready),
                            logCount = application.launcherLogRepository.listLogFiles().size
                        )
                    }
                    _shareIntent.value = shareIntentFor(res)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            message = application.getString(
                                R.string.diagnostics_export_failed,
                                error.message ?: ""
                            )
                        )
                    }
                }
            )
            refresh()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val ok = application.launcherLogRepository.clearLogs().isSuccess
            _uiState.update {
                it.copy(
                    busy = false,
                    message = if (ok) {
                        application.getString(R.string.diagnostics_logs_cleared)
                    } else {
                        application.getString(R.string.diagnostics_clear_failed)
                    },
                    logCount = application.launcherLogRepository.listLogFiles().size
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val ok = application.performanceManager.clearCache().isSuccess
            _uiState.update {
                it.copy(
                    busy = false,
                    message = if (ok) {
                        application.getString(R.string.diagnostics_cache_cleared)
                    } else {
                        application.getString(R.string.diagnostics_clear_failed)
                    }
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeShare() {
        _shareIntent.value = null
    }

    /** Q+: the file lives in public Downloads — no sharing needed. Pre-Q: share it. */
    private fun shareIntentFor(result: ExportResult): Intent? {
        val file = result.file ?: return null
        val provider = androidx.core.content.FileProvider.getUriForFile(
            application,
            "${application.packageName}.exports",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, provider)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let {
            Intent.createChooser(it, application.getString(R.string.diagnostics_share_title))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                DiagnosticsViewModel(
                    application = application,
                    exporter = application.crashReportExporter
                )
            }
        }
    }
}
