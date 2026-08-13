package com.lumocraft.app.ui.loader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.data.preferences.VersionPreference
import com.lumocraft.app.domain.loader.LoaderInstallProgress
import com.lumocraft.app.domain.loader.LoaderInstallStage
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.loader.LoaderRepository
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.VersionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state for the Loader Manager screen. */
data class LoaderManagerUiState(
    val installedLoaders: List<LoaderInstance> = emptyList(),
    /** Loader that the Home screen would launch (selected version). */
    val activeLoader: LoaderInstance? = null,
    val selectedVersionId: String? = null,
    /** Installed vanilla versions (loader instances excluded). */
    val installedVanillaVersions: List<String> = emptyList(),
    /** Fabric compatibility: vanilla version -> published loader count. */
    val compatibility: Map<String, Int> = emptyMap(),
    val compatibilityLoading: Boolean = true,
    val repairingId: String? = null,
    val repairProgress: LoaderInstallProgress? = null,
    val errorMessage: String? = null
)

/**
 * Aggregates installed loaders, the active launch target and Fabric
 * compatibility for the installed vanilla versions. Loader types are
 * discovered through [LoaderRepository], so future loaders (Quilt,
 * Forge, NeoForge) appear here automatically once registered.
 */
class LoaderManagerViewModel(
    versionRepository: VersionRepository,
    private val loaderRepository: LoaderRepository,
    private val versionPreference: VersionPreference,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoaderManagerUiState())
    val uiState: StateFlow<LoaderManagerUiState> = _uiState.asStateFlow()

    private var repairJob: Job? = null
    private val compatibilityJobs = mutableMapOf<String, Job>()
    private val tick = MutableStateFlow(Unit)

    init {
        viewModelScope.launch {
            combine(
                loaderRepository.observeInstalledLoaders(),
                versionRepository.observeInstalledStates(),
                tick
            ) { loaders, states, _ ->
                val selected = versionPreference.loadSelectedVersionId()
                val activeLoader = loaders.firstOrNull { it.instanceId == selected }
                val vanillaInstalled = states
                    .filterValues { it == InstallState.INSTALLED }
                    .keys
                    .filter { id -> loaders.none { it.instanceId == id } }
                    .sorted()
                LoaderManagerUiState(
                    installedLoaders = loaders,
                    activeLoader = activeLoader,
                    selectedVersionId = selected,
                    installedVanillaVersions = vanillaInstalled
                )
            }.collect { base ->
                _uiState.update { current ->
                    base.copy(
                        compatibility = current.compatibility,
                        compatibilityLoading = current.compatibilityLoading,
                        repairingId = current.repairingId,
                        repairProgress = current.repairProgress,
                        errorMessage = current.errorMessage
                    )
                }
                fetchCompatibility(base.installedVanillaVersions)
            }
        }
    }

    /** Fetches Fabric compatibility once per installed vanilla version. */
    private fun fetchCompatibility(vanillaVersions: List<String>) {
        _uiState.update {
            it.copy(
                compatibilityLoading = vanillaVersions.any { id -> !it.compatibility.containsKey(id) }
            )
        }
        vanillaVersions.forEach { versionId ->
            if (compatibilityJobs[versionId]?.isActive == true) return@forEach
            compatibilityJobs[versionId] = viewModelScope.launch {
                val result = loaderRepository.fetchLoaderVersions(LoaderType.FABRIC, versionId)
                _uiState.update { state ->
                    val compat = state.compatibility +
                        (versionId to result.getOrNull().orEmpty().size)
                    state.copy(
                        compatibility = compat,
                        compatibilityLoading = vanillaVersions.any { id -> !compat.containsKey(id) }
                    )
                }
            }
        }
    }

    fun repair(instanceId: String) {
        if (repairJob?.isActive == true) return
        repairJob = viewModelScope.launch {
            loaderRepository.repair(LoaderType.FABRIC, instanceId).collect { progress ->
                _uiState.update { state ->
                    when {
                        progress.error != null -> state.copy(
                            repairingId = null,
                            repairProgress = null,
                            errorMessage = progress.error
                        )
                        progress.stage == LoaderInstallStage.COMPLETE -> state.copy(
                            repairingId = null,
                            repairProgress = null
                        )
                        else -> state.copy(
                            repairingId = progress.instanceId.ifEmpty { instanceId },
                            repairProgress = progress
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                LoaderManagerViewModel(
                    versionRepository = application.versionRepository,
                    loaderRepository = application.loaderRepository,
                    versionPreference = VersionPreference(application)
                )
            }
        }
    }
}