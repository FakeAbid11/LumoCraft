package com.lumocraft.app.data.account

import android.content.Context
import com.lumocraft.app.domain.account.AccountRepository
import com.lumocraft.app.domain.account.CannotDeleteLastAccountException
import com.lumocraft.app.domain.account.DuplicateUsernameException
import com.lumocraft.app.domain.account.OfflineAccount
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * [AccountRepository] backed by SharedPreferences.
 *
 * The whole account list is serialized to a single JSON string (org.json,
 * built into Android — no extra dependency). Every mutation updates the
 * in-memory [MutableStateFlow] and persists synchronously, so callers
 * observe instant updates and the state survives process death.
 *
 * Migration to DataStore later only requires a new implementation of
 * [AccountRepository]; the interface and all callers stay untouched.
 */
class SharedPreferencesAccountRepository(context: Context) : AccountRepository {

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(loadAccounts())

    override fun observeAccounts() = _accounts.asStateFlow()

    override fun getSelectedAccount(): OfflineAccount? =
        _accounts.value.firstOrNull { it.isSelected }

    override fun addAccount(username: String): Result<OfflineAccount> {
        val accounts = _accounts.value
        if (accounts.any { it.username.equals(username, ignoreCase = true) }) {
            return Result.failure(DuplicateUsernameException())
        }
        val account = OfflineAccount(
            id = UUID.randomUUID().toString(),
            username = username,
            createdAt = System.currentTimeMillis(),
            isSelected = accounts.isEmpty()
        )
        update { it + account }
        return Result.success(account)
    }

    override fun renameAccount(accountId: String, username: String): Result<Unit> {
        val accounts = _accounts.value
        if (accounts.none { it.id == accountId }) {
            return Result.failure(IllegalArgumentException("Account not found"))
        }
        if (accounts.any { it.id != accountId && it.username.equals(username, ignoreCase = true) }) {
            return Result.failure(DuplicateUsernameException())
        }
        update { list -> list.map { if (it.id == accountId) it.copy(username = username) else it } }
        return Result.success(Unit)
    }

    override fun deleteAccount(accountId: String): Result<Unit> {
        val accounts = _accounts.value
        if (accounts.size <= 1) {
            return Result.failure(CannotDeleteLastAccountException())
        }
        val target = accounts.firstOrNull { it.id == accountId }
            ?: return Result.failure(IllegalArgumentException("Account not found"))
        update { list ->
            val remaining = list.filterNot { it.id == accountId }
            if (target.isSelected) {
                remaining.mapIndexed { index, account ->
                    if (index == 0) account.copy(isSelected = true) else account
                }
            } else {
                remaining
            }
        }
        return Result.success(Unit)
    }

    override fun selectAccount(accountId: String) {
        if (_accounts.value.none { it.id == accountId }) return
        update { list -> list.map { it.copy(isSelected = it.id == accountId) } }
    }

    private fun update(transform: (List<OfflineAccount>) -> List<OfflineAccount>) {
        val updated = transform(_accounts.value)
        prefs.edit().putString(KEY_ACCOUNTS, toJson(updated)).apply()
        _accounts.value = updated
    }

    private fun loadAccounts(): List<OfflineAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return fromJson(raw)
    }

    private fun toJson(accounts: List<OfflineAccount>): String {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put(KEY_ID, account.id)
                    .put(KEY_USERNAME, account.username)
                    .put(KEY_CREATED_AT, account.createdAt)
                    .put(KEY_IS_SELECTED, account.isSelected)
            )
        }
        return array.toString()
    }

    private fun fromJson(raw: String): List<OfflineAccount> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    OfflineAccount(
                        id = obj.getString(KEY_ID),
                        username = obj.getString(KEY_USERNAME),
                        createdAt = obj.getLong(KEY_CREATED_AT),
                        isSelected = obj.getBoolean(KEY_IS_SELECTED)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS_NAME = "lumocraft_accounts"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ID = "id"
        const val KEY_USERNAME = "username"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_IS_SELECTED = "isSelected"
    }
}
