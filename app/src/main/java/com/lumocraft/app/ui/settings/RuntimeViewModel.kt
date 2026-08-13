package com.lumocraft.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.domain.runtime.JvmConfiguration
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeProgress
import com.lumocraft.app.domain.runtime.RuntimeRepository
import com.lumocraft.app.domain.runtime.RuntimeStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuntimeUiState(
    val runtimes: List<RuntimeInfo> = emptyList(),
    val architecture: RuntimeArchitecture? = null,
    val jvmConfig: JvmConfiguration = JvmConfiguration(),
    val activeRuntimeId: String? = null,
    val progress: RuntimeProgress? = null,
    val errorMessage: String? = null
) {
    val defaultRuntime: RuntimeInfo? get() = runtimes.firstOrNull { it.isDefault }
}

class RuntimeViewModel(private val repository: RuntimeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    private var installJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeRuntimes().collect { runtimes ->
                _uiState.update { it.copy(runtimes = runtimes) }
            }
        }
        _uiState.update {
            it.copy(
                architecture = repository.detectArchitecture(),
                jvmConfig = repository.loadJvmConfiguration()
            )
        }
    }

    fun install(runtimeId: String) {
        runOperation(repository.install(runtimeId))
    }

    fun repair(runtimeId: String) {
        runOperation(repository.repair(runtimeId))
    }

    fun remove(runtimeId: String) {
        viewModelScope.launch {
            repository.remove(runtimeId)
        }
    }

    fun verify(runtimeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeRuntimeId = runtimeId) }
            val result = repository.verify(runtimeId)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { report ->
                        state.copy(
                            activeRuntimeId = null,
                            errorMessage = if (report.ok) null else "Runtime verification failed"
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            activeRuntimeId = null,
                            errorMessage = error.message
                        )
                    }
                )
            }
        }
    }

    fun setDefault(runtimeId: String) {
        viewModelScope.launch {
            repository.setDefault(runtimeId)
        }
    }

    fun setMaxMemory(mb: Int) {
        val config = _uiState.value.jvmConfig.copy(maxMemoryMB = mb)
        _uiState.update { it.copy(jvmConfig = config) }
        repository.saveJvmConfiguration(config)
    }

    fun setMinMemory(mb: Int) {
        val config = _uiState.value.jvmConfig.copy(minMemoryMB = mb)
        _uiState.update { it.copy(jvmConfig = config) }
        repository.saveJvmConfiguration(config)
    }

    fun setGcMode(mode: JvmConfiguration.GcMode) {
        val config = _uiState.value.jvmConfig.copy(gcMode = mode)
        _uiState.update { it.copy(jvmConfig = config) }
        repository.saveJvmConfiguration(config)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun runOperation(flow: Flow<RuntimeProgress>) {
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            flow.collect { progress ->
                _uiState.update { state ->
                    when {
                        progress.error != null -> state.copy(
                            activeRuntimeId = null,
                            progress = null,
                            errorMessage = progress.error
                        )
                        progress.stage == RuntimeStage.COMPLETE -> state.copy(
                            activeRuntimeId = null,
                            progress = null
                        )
                        else -> state.copy(
                            activeRuntimeId = progress.runtimeId,
                            progress = progress
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
                RuntimeViewModel(application.runtimeRepository)
            }
        }
    }
}