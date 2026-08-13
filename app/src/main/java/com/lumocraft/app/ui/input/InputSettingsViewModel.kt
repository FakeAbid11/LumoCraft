package com.lumocraft.app.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.domain.input.InputManager
import com.lumocraft.app.domain.input.InputProfile
import com.lumocraft.app.domain.input.InputSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Immutable state for the Input settings section. */
data class InputSettingsUiState(
    val profiles: List<InputProfile> = emptyList(),
    val activeProfile: InputProfile? = null,
    val settings: InputSettings = InputSettings(),
    val controllerConnected: Boolean = false,
    val controllerName: String? = null,
    val keyboardConnected: Boolean = false
)

/**
 * Bridges [InputManager] to the Input settings UI. Profile-level
 * values (sensitivity, invert Y, toggles) persist through the active
 * profile; cursor speed and button opacity are launcher settings.
 */
class InputSettingsViewModel(
    private val manager: InputManager,
) : ViewModel() {

    val uiState: StateFlow<InputSettingsUiState> = combine(
        manager.profiles,
        manager.activeProfile,
        manager.settings,
        manager.controller.state,
        manager.keyboard.state
    ) { profiles, profile, settings, controller, keyboard ->
        InputSettingsUiState(
            profiles = profiles,
            activeProfile = profile,
            settings = settings,
            controllerConnected = controller.connected,
            controllerName = controller.deviceName,
            keyboardConnected = keyboard.connected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InputSettingsUiState(
            profiles = manager.profiles.value,
            activeProfile = manager.activeProfile.value,
            settings = manager.settings.value
        )
    )

    fun selectProfile(profileId: String) = manager.selectProfile(profileId)

    fun duplicateProfile() {
        manager.activeProfileId.value.let(manager::duplicateProfile)
    }

    fun setSensitivity(value: Float) {
        manager.updateActiveProfile { it.copy(sensitivity = value) }
    }

    fun setInvertY(enabled: Boolean) {
        manager.updateActiveProfile { it.copy(invertY = enabled) }
    }

    fun setCursorSpeed(value: Float) {
        manager.setSettings { it.copy(cursorSpeed = value) }
    }

    fun setButtonOpacity(value: Float) {
        manager.setSettings { it.copy(buttonOpacity = value) }
        manager.updateActiveProfile { it.copy(buttonLayout = it.buttonLayout.withOpacity(value)) }
    }

    fun setControllerEnabled(enabled: Boolean) {
        manager.updateActiveProfile { it.copy(controllerEnabled = enabled) }
    }

    fun setKeyboardEnabled(enabled: Boolean) {
        manager.updateActiveProfile { it.copy(keyboardEnabled = enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                InputSettingsViewModel(application.inputManager)
            }
        }
    }
}