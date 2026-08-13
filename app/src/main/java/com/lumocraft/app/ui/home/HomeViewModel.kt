package com.lumocraft.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.data.launch.LaunchValidator
import com.lumocraft.app.data.preferences.VersionPreference
import com.lumocraft.app.domain.account.AccountRepository
import com.lumocraft.app.domain.account.OfflineAccount
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchValidationReport
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeRepository
import com.lumocraft.app.domain.runtime.RuntimeStatus
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.VersionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Immutable state for the Home screen. */
data class HomeUiState(
    val selectedAccount: OfflineAccount? = null,
    val installedVersions: List<String> = emptyList(),
    val selectedVersionId: String? = null,
    val runtime: RuntimeInfo? = null,
    val readiness: LaunchValidationReport = LaunchValidationReport()
) {
    val canPlay: Boolean get() = readiness.ok
}

/**
 * Aggregates accounts, installed versions, the selected version and the
 * default runtime into the readiness report that gates the Play button.
 * [buildLaunchContext] produces the [LaunchContext] the pipeline consumes.
 */
class HomeViewModel(
    private val application: LumoCraftApplication,
    accountRepository: AccountRepository,
    versionRepository: VersionRepository,
    runtimeRepository: RuntimeRepository,
    private val versionPreference: VersionPreference,
    private val launchValidator: LaunchValidator,
) : ViewModel() {

    /** Re-emitted when the user picks a version (prefs are not a flow). */
    private val selectionTick = MutableStateFlow(Unit)

    val uiState: StateFlow<HomeUiState> = combine(
        accountRepository.observeAccounts(),
        versionRepository.observeInstalledStates(),
        runtimeRepository.observeRuntimes(),
        selectionTick
    ) { accounts, states, runtimes, _ ->
        val installed = states.filterValues { it == InstallState.INSTALLED }.keys.sorted()
        val saved = versionPreference.loadSelectedVersionId()
        val selected = when {
            saved in installed -> saved
            else -> installed.lastOrNull()
        }
        val runtime = runtimes.firstOrNull { it.isDefault } ?: runtimes.firstOrNull()
        val account = accounts.firstOrNull { it.isSelected }

        val report = when {
            runtime == null || selected == null -> LaunchValidationReport(
                accountOk = account != null,
                runtimeOk = runtime != null,
                versionOk = selected != null
            )
            else -> launchValidator.validateReadiness(
                LaunchContext(
                    account = account ?: OfflineAccount(
                        id = "",
                        username = "",
                        createdAt = 0L,
                        isSelected = false
                    ),
                    versionId = selected,
                    runtime = runtime,
                    gameDirectory = application.storageManager.launcherRoot()
                )
            )
        }
        HomeUiState(
            selectedAccount = account,
            installedVersions = installed,
            selectedVersionId = selected,
            runtime = runtime,
            readiness = report
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun selectVersion(versionId: String) {
        versionPreference.saveSelectedVersionId(versionId)
        selectionTick.value = Unit
    }

    /** Builds the launch request; null when not ready (see [HomeUiState.canPlay]). */
    fun buildLaunchContext(): LaunchContext? {
        val state = uiState.value
        val account = state.selectedAccount ?: return null
        val versionId = state.selectedVersionId ?: return null
        val runtime = state.runtime ?: return null
        if (runtime.status != RuntimeStatus.INSTALLED) return null
        return LaunchContext(
            account = account,
            versionId = versionId,
            runtime = runtime,
            gameDirectory = application.storageManager.launcherRoot(),
            jvmConfiguration = application.runtimeRepository.loadJvmConfiguration()
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                HomeViewModel(
                    application = application,
                    accountRepository = application.accountRepository,
                    versionRepository = application.versionRepository,
                    runtimeRepository = application.runtimeRepository,
                    versionPreference = VersionPreference(application),
                    launchValidator = application.launchValidator
                )
            }
        }
    }
}