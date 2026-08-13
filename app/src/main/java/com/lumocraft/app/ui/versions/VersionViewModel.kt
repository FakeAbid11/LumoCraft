package com.lumocraft.app.ui.versions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallStage
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.MinecraftVersion
import com.lumocraft.app.domain.version.VersionFilter
import com.lumocraft.app.domain.version.VersionManifest
import com.lumocraft.app.domain.version.VersionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state for the Versions screen. */
data class VersionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val manifest: VersionManifest? = null,
    val loadError: Boolean = false,
    val searchQuery: String = "",
    val filter: VersionFilter = VersionFilter.ALL,
    val installStates: Map<String, InstallState> = emptyMap(),
    val installingId: String? = null,
    val installProgress: InstallProgress? = null,
    val errorMessageRes: Int? = null
) {
    /** Filtering + search happen locally on the downloaded manifest. */
    val visibleVersions: List<MinecraftVersion>
        get() {
            val versions = manifest?.versions ?: return emptyList()
            val query = searchQuery.trim()
            return versions.filter { version ->
                filter.matches(version.type) &&
                    (query.isEmpty() || version.id.contains(query, ignoreCase = true))
            }
        }
}

/**
 * Loads the manifest, exposes filtering/search and drives installation
 * and repair. All networking goes through [VersionRepository]; the UI
 * never touches HTTP or files directly.
 */
class VersionViewModel(private val repository: VersionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(VersionsUiState())
    val uiState: StateFlow<VersionsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var installJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeInstalledStates().collect { states ->
                _uiState.update { it.copy(installStates = states) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hadManifest = _uiState.value.manifest != null
            _uiState.update {
                it.copy(
                    isLoading = !hadManifest,
                    isRefreshing = hadManifest,
                    loadError = false
                )
            }
            repository.fetchManifest()
                .onSuccess { manifest ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, manifest = manifest)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, loadError = true)
                    }
                }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: VersionFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun install(version: MinecraftVersion) {
        runInstall(repository.install(version), isRepair = false)
    }

    fun repair(version: MinecraftVersion) {
        runInstall(repository.repair(version), isRepair = true)
    }

    fun clearInstallError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    private fun runInstall(flow: Flow<InstallProgress>, isRepair: Boolean) {
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            flow.collect { progress ->
                _uiState.update { state ->
                    when {
                        progress.error != null -> state.copy(
                            installingId = null,
                            installProgress = null,
                            errorMessageRes = if (isRepair) {
                                R.string.versions_repair_failed
                            } else {
                                R.string.versions_install_failed
                            }
                        )
                        progress.stage == InstallStage.COMPLETE -> state.copy(
                            installingId = null,
                            installProgress = null
                        )
                        else -> state.copy(
                            installingId = progress.versionId,
                            installProgress = progress,
                            errorMessageRes = null
                        )
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                VersionViewModel(application.versionRepository)
            }
        }
    }
}