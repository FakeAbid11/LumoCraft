package com.lumocraft.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.core.version.VersionManager
import com.lumocraft.app.domain.update.UpdateRepository
import com.lumocraft.app.domain.update.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the About / update-check section. */
data class AboutUiState(
    val versionDisplay: String = "",
    val checking: Boolean = false,
    val checked: Boolean = false,
    val status: UpdateStatus = UpdateStatus.UNKNOWN,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
)

/**
 * Version display plus a manual update check against the release
 * channel. Checking is always user-initiated — the launcher never
 * auto-downloads anything.
 */
class AboutViewModel(
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(versionDisplay = VersionManager.currentDisplayName())
    )
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    fun checkForUpdates() {
        if (_uiState.value.checking) return
        viewModelScope.launch {
            _uiState.update { it.copy(checking = true) }
            val result = updateRepository.checkForUpdates()
            _uiState.update {
                it.copy(
                    checking = false,
                    checked = true,
                    status = result.status,
                    latestVersion = result.latest?.versionName,
                    releaseUrl = result.latest?.releaseUrl
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                AboutViewModel(application.updateRepository)
            }
        }
    }
}
