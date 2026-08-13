package com.lumocraft.app.domain.account

import kotlinx.coroutines.flow.Flow

/** Thrown when adding/renaming an account to a username that already exists. */
class DuplicateUsernameException : IllegalArgumentException()

/** Thrown when trying to delete the last remaining account. */
class CannotDeleteLastAccountException : IllegalStateException()

/**
 * Single source of truth for local launcher profiles.
 *
 * Implementations decide the persistence backend (SharedPreferences today,
 * DataStore later). Consumers only depend on this interface, so the
 * selected account stays available to future features (launch, save
 * directories, version preferences) without architecture changes.
 */
interface AccountRepository {

    /** Emits the current account list; always at most one account is selected. */
    fun observeAccounts(): Flow<List<OfflineAccount>>

    /** The currently selected account, or null when no accounts exist. */
    fun getSelectedAccount(): OfflineAccount?

    /**
     * Creates a new account. The first account is selected automatically.
     * Fails with [DuplicateUsernameException] when the username is taken
     * (comparison is case-insensitive).
     */
    fun addAccount(username: String): Result<OfflineAccount>

    /**
     * Renames an account, keeping its id and creation date.
     * Fails with [DuplicateUsernameException] when the new username is
     * taken by a different account.
     */
    fun renameAccount(accountId: String, username: String): Result<Unit>

    /**
     * Deletes an account. If the selected account is removed, another one
     * is selected automatically. Fails with [CannotDeleteLastAccountException]
     * when it is the last remaining account.
     */
    fun deleteAccount(accountId: String): Result<Unit>

    /** Marks a single account as selected; selection of others is cleared. */
    fun selectAccount(accountId: String)
}
