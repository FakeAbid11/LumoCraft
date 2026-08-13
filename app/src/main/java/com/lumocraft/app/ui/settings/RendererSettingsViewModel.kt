package com.lumocraft.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.native.RendererProfile
import com.lumocraft.app.domain.native.RendererType
import com.lumocraft.app.domain.native.ResolutionScale
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Immutable state for the Renderer settings section. */
data class RendererUiState(
    val profile: RendererProfile = RendererProfile(),
    val nativeStatus: NativeStatus = NativeStatus.NOT_PREPARED,
    val architecture: RuntimeArchitecture? = null
)

/**
 * Bridges [NativeRuntimeManager] to the renderer settings UI. Every
 * change is persisted immediately through the manager.
 */
class RendererSettingsViewModel(
    private val nativeRuntimeManager: NativeRuntimeManager,
) : ViewModel() {

    private val _profile = MutableStateFlow(nativeRuntimeManager.rendererProfile())

    val uiState: StateFlow<RendererUiState> = combine(
        _profile,
        nativeRuntimeManager.status
    ) { profile, nativeStatus ->
        RendererUiState(
            profile = profile,
            nativeStatus = nativeStatus,
            architecture = nativeRuntimeManager.architecture()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RendererUiState(
            profile = _profile.value,
            architecture = nativeRuntimeManager.architecture()
        )
    )

    fun selectRenderer(type: RendererType) {
        update { it.copy(renderer = type) }
    }

    fun selectResolutionScale(scale: ResolutionScale) {
        update { it.copy(resolutionScale = scale) }
    }

    fun selectFpsLimit(limit: Int?) {
        update { it.copy(fpsLimit = limit) }
    }

    fun setVsync(enabled: Boolean) {
        update { it.copy(vsync = enabled) }
    }

    private fun update(transform: (RendererProfile) -> RendererProfile) {
        val next = transform(_profile.value)
        _profile.value = next
        nativeRuntimeManager.saveRendererProfile(next)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                RendererSettingsViewModel(application.nativeRuntimeManager)
            }
        }
    }
}