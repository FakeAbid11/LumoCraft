package com.lumocraft.app.ui.versions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.data.launch.LauncherLogRepository
import com.lumocraft.app.domain.loader.LoaderInstallProgress
import com.lumocraft.app.domain.loader.LoaderInstallStage
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.loader.LoaderRepository
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.loader.LoaderVersion
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

/** Loader selector state for one Minecraft version. */
sealed interface LoaderVersionsState {
    data object Loading : LoaderVersionsState
    data class Loaded(val versions: List<LoaderVersion>) : LoaderVersionsState
    data object Error : LoaderVersionsState
}

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
    val errorMessageRes: Int? = null,
    /** Loader selector data per Minecraft version (fetched on demand). */
    val loaderVersions: Map<String, LoaderVersionsState> = emptyMap(),
    /** Installed loader instances keyed by instance id. */
    val installedLoaders: Map<String, LoaderInstance> = emptyMap(),
    val installingLoaderId: String? = null,
    val loaderInstallProgress: LoaderInstallProgress? = null
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

    /** Loaders installed for a Minecraft version. */
    fun loadersFor(minecraftVersion: String): List<LoaderInstance> =
        installedLoaders.values.filter { it.metadata.minecraftVersion == minecraftVersion }
}

/**
 * Loads the manifest, exposes filtering/search and drives vanilla
 * installation/removal plus loader (Fabric) installation, repair and
 * removal. All networking goes through [VersionRepository] and
 * [LoaderRepository]; the UI never touches HTTP or files directly.
 */
class VersionViewModel(
    private val repository: VersionRepository,
    private val loaderRepository: LoaderRepository,
    private val launcherLogRepository: LauncherLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VersionsUiState())
    val uiState: StateFlow<VersionsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var installJob: Job? = null
    private var loaderInstallJob: Job? = null
    private val fetchJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.observeInstalledStates().collect { states ->
                _uiState.update { it.copy(installStates = states) }
            }
        }
        viewModelScope.launch {
            loaderRepository.observeInstalledLoaders().collect { loaders ->
                _uiState.update {
                    it.copy(installedLoaders = loaders.associateBy { it.instanceId })
                }
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

    fun remove(version: MinecraftVersion) {
        viewModelScope.launch {
            repository.remove(version.id)
        }
    }

    fun clearInstallError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    /** Loads the compatible loader versions for a Minecraft version once. */
    fun fetchLoaderVersions(minecraftVersion: String) {
        if (_uiState.value.loaderVersions.containsKey(minecraftVersion)) return
        if (fetchJobs[minecraftVersion]?.isActive == true) return
        _uiState.update {
            it.copy(loaderVersions = it.loaderVersions + (minecraftVersion to LoaderVersionsState.Loading))
        }
        fetchJobs[minecraftVersion] = viewModelScope.launch {
            loaderRepository.fetchLoaderVersions(LoaderType.FABRIC, minecraftVersion)
                .fold(
                    onSuccess = { versions ->
                        _uiState.update {
                            it.copy(
                                loaderVersions = it.loaderVersions +
                                    (minecraftVersion to LoaderVersionsState.Loaded(versions))
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update {
                            it.copy(
                                loaderVersions = it.loaderVersions +
                                    (minecraftVersion to LoaderVersionsState.Error)
                            )
                        }
                    }
                )
        }
    }

    /** Drops a cached loader version list so it can be fetched again. */
    fun retryLoaderVersions(minecraftVersion: String) {
        _uiState.update {
            it.copy(loaderVersions = it.loaderVersions - minecraftVersion)
        }
        fetchLoaderVersions(minecraftVersion)
    }

    fun installLoader(minecraftVersion: String, loaderVersion: String) {
        runLoaderInstall(loaderRepository.install(LoaderType.FABRIC, minecraftVersion, loaderVersion))
    }

    fun repairLoader(instanceId: String) {
        runLoaderInstall(loaderRepository.repair(LoaderType.FABRIC, instanceId))
    }

    fun removeLoader(instanceId: String) {
        viewModelScope.launch {
            loaderRepository.remove(LoaderType.FABRIC, instanceId)
        }
    }

    fun clearLoaderError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    private fun runInstall(flow: Flow<InstallProgress>, isRepair: Boolean) {
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            try {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Last line of defense: no exception may terminate the install
                // coroutine. Reset the install state and surface a recoverable
                // error instead of crashing the app.
                logInstallCrash(e)
                _uiState.update {
                    it.copy(
                        installingId = null,
                        installProgress = null,
                        errorMessageRes = if (isRepair) {
                            R.string.versions_repair_failed
                        } else {
                            R.string.versions_install_failed
                        }
                    )
                }
            }
        }
    }

    private fun runLoaderInstall(flow: Flow<LoaderInstallProgress>) {
        if (loaderInstallJob?.isActive == true) return
        loaderInstallJob = viewModelScope.launch {
            try {
                flow.collect { progress ->
                    _uiState.update { state ->
                        when {
                            progress.error != null -> state.copy(
                                installingLoaderId = null,
                                loaderInstallProgress = null,
                                errorMessageRes = R.string.loader_install_failed
                            )
                            progress.stage == LoaderInstallStage.COMPLETE -> state.copy(
                                installingLoaderId = null,
                                loaderInstallProgress = null
                            )
                            else -> state.copy(
                                installingLoaderId = progress.instanceId.ifEmpty { state.installingLoaderId },
                                loaderInstallProgress = progress,
                                errorMessageRes = null
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Same protection as vanilla installs: a loader install must
                // never terminate the coroutine and crash the app.
                logInstallCrash(e)
                _uiState.update {
                    it.copy(
                        installingLoaderId = null,
                        loaderInstallProgress = null,
                        errorMessageRes = R.string.loader_install_failed
                    )
                }
            }
        }
    }

    /** Logs an escaping install exception with its full stack trace. */
    private suspend fun logInstallCrash(e: Throwable) {
        launcherLogRepository.writeLine(
            "Version install crashed: exception=${e::class.java.name} message=${e.message}"
        )
        e.stackTraceToString().lines().forEach { line ->
            launcherLogRepository.writeLine(line)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                VersionViewModel(
                    repository = application.versionRepository,
                    loaderRepository = application.loaderRepository,
                    launcherLogRepository = application.launcherLogRepository
                )
            }
        }
    }
}