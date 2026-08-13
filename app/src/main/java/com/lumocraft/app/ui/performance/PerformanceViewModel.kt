package com.lumocraft.app.ui.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.domain.performance.CacheStats
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.performance.JvmProfile
import com.lumocraft.app.domain.performance.LaunchHistory
import com.lumocraft.app.domain.performance.PerformanceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the Performance dashboard. */
data class PerformanceUiState(
    val deviceProfile: DeviceProfile? = null,
    val jvmProfile: JvmProfile? = null,
    val automatic: Boolean = true,
    val cacheStats: CacheStats = CacheStats(),
    val history: LaunchHistory = LaunchHistory(),
    val busy: Boolean = false,
    val message: String? = null
)

/**
 * Bridges the [PerformanceManager] to the dashboard: device profile,
 * JVM profile selection (auto/manual), cache stats, launch history and
 * the Clear cache / Rebuild cache / Reset performance settings actions.
 * All heavy work (cache rebuilds, verification) runs on IO via the
 * performance manager.
 */
class PerformanceViewModel(
    private val performance: PerformanceManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerformanceUiState())
    val uiState: StateFlow<PerformanceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val stats = performance.cache().stats()
            val history = performance.profiler().summary()
            _uiState.update {
                it.copy(
                    deviceProfile = performance.deviceProfile(),
                    jvmProfile = performance.effectiveJvmProfile(),
                    automatic = performance.jvmProfileOverride() == null,
                    cacheStats = stats,
                    history = history
                )
            }
        }
    }

    fun selectJvmProfile(profile: JvmProfile?) {
        performance.setJvmProfileOverride(profile)
        refresh()
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = performance.clearCache()
            _uiState.update {
                it.copy(
                    busy = false,
                    message = if (result.isSuccess) "Cache cleared" else "Cache clear failed"
                )
            }
            refresh()
        }
    }

    fun rebuildCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = performance.rebuildCache()
            _uiState.update {
                it.copy(
                    busy = false,
                    message = if (result.isSuccess) "Cache rebuilt" else "Cache rebuild failed"
                )
            }
            refresh()
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            performance.resetPerformanceSettings()
            _uiState.update { it.copy(busy = false, message = "Performance settings reset") }
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                PerformanceViewModel(application.performanceManager)
            }
        }
    }
}