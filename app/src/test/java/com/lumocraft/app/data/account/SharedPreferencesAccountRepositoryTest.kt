package com.lumocraft.app.data.account

import androidx.test.core.app.ApplicationProvider
import com.lumocraft.app.domain.account.CannotDeleteLastAccountException
import com.lumocraft.app.domain.account.DuplicateUsernameException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesAccountRepositoryTest {

    private fun repository(clear: Boolean = true): SharedPreferencesAccountRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (clear) {
            context.getSharedPreferences("lumocraft_accounts", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        return SharedPreferencesAccountRepository(context)
    }

    @Test
    fun `first account is selected automatically`() {
        val repo = repository()
        val account = repo.addAccount("Steve").getOrThrow()
        assertEquals("Steve", account.username)
        assertEquals("Steve", repo.getSelectedAccount()?.username)
    }

    @Test
    fun `duplicate usernames are rejected case-insensitively`() {
        val repo = repository()
        repo.addAccount("Steve")
        assertTrue(repo.addAccount("steve").isFailure)
        assertTrue(repo.addAccount("STEVE").exceptionOrNull() is DuplicateUsernameException)
    }

    @Test
    fun `renaming validates uniqueness and persists`() {
        val repo = repository()
        val first = repo.addAccount("Steve").getOrThrow()
        repo.addAccount("Alex")
        assertTrue(repo.renameAccount(first.id, "Alex").isFailure)
        assertTrue(repo.renameAccount(first.id, "Steve2").isSuccess)
        assertNotNull(repo.observeAccounts().value.firstOrNull { it.username == "Steve2" })
    }

    @Test
    fun `selecting an account flips the selection`() {
        val repo = repository()
        val first = repo.addAccount("Steve").getOrThrow()
        val second = repo.addAccount("Alex").getOrThrow()
        repo.selectAccount(second.id)
        assertEquals("Alex", repo.getSelectedAccount()?.username)
        repo.selectAccount(first.id)
        assertEquals("Steve", repo.getSelectedAccount()?.username)
    }

    @Test
    fun `deleting the selected account promotes the first remaining`() {
        val repo = repository()
        repo.addAccount("Steve")
        repo.addAccount("Alex")
        val promoted = repo.addAccount("Bob").getOrThrow()
        repo.selectAccount(promoted.id)
        assertTrue(repo.deleteAccount(promoted.id).isSuccess)
        assertEquals("Steve", repo.getSelectedAccount()?.username)
    }

    @Test
    fun `last account cannot be deleted`() {
        val repo = repository()
        val only = repo.addAccount("Steve").getOrThrow()
        val result = repo.deleteAccount(only.id)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CannotDeleteLastAccountException)
    }

    @Test
    fun `deleting a non-selected account keeps the selection`() {
        val repo = repository()
        val first = repo.addAccount("Steve").getOrThrow()
        val second = repo.addAccount("Alex").getOrThrow()
        repo.selectAccount(first.id)
        assertTrue(repo.deleteAccount(second.id).isSuccess)
        assertEquals("Steve", repo.getSelectedAccount()?.username)
        assertEquals(1, repo.observeAccounts().value.size)
    }

    @Test
    fun `accounts survive repository recreation`() {
        val repo = repository()
        repo.addAccount("Steve")
        repo.addAccount("Alex")
        val fresh = repository(clear = false)
        assertEquals(2, fresh.observeAccounts().value.size)
        assertEquals("Steve", fresh.getSelectedAccount()?.username)
    }

    @Test
    fun `unknown ids are no-ops`() {
        val repo = repository()
        val first = repo.addAccount("Steve").getOrThrow()
        repo.selectAccount("missing")
        assertEquals(first.id, repo.getSelectedAccount()?.id)
        assertFalse(repo.deleteAccount("missing").isSuccess)
    }
}