package com.lumocraft.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.domain.account.AccountRepository
import com.lumocraft.app.domain.account.OfflineAccount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Immutable UI state derived from the repository. */
data class AccountsUiState(
    val accounts: List<OfflineAccount> = emptyList(),
    val selectedAccount: OfflineAccount? = null
) {
    val totalCount: Int get() = accounts.size
}

/**
 * Holds no persistence logic itself — it only maps the repository stream
 * into [AccountsUiState] and forwards user actions.
 */
class AccountViewModel(private val repository: AccountRepository) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = repository.observeAccounts()
        .map { accounts ->
            AccountsUiState(
                accounts = accounts,
                selectedAccount = accounts.firstOrNull { it.isSelected }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountsUiState()
        )

    fun addAccount(username: String): Result<OfflineAccount> =
        repository.addAccount(username)

    fun renameAccount(accountId: String, username: String): Result<Unit> =
        repository.renameAccount(accountId, username)

    fun deleteAccount(accountId: String): Result<Unit> =
        repository.deleteAccount(accountId)

    fun selectAccount(accountId: String) {
        repository.selectAccount(accountId)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                AccountViewModel(application.accountRepository)
            }
        }
    }
}
