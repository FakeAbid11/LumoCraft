package com.lumocraft.app.data.version

import androidx.test.core.app.ApplicationProvider
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.InstalledVersionMetadata
import com.lumocraft.app.domain.version.MinecraftVersion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DefaultVersionRepository] orchestration without network: the install
 * states flow mirrors disk metadata and removal cleans up. The install
 * pipeline itself is exercised end-to-end elsewhere; here the repository
 * contract is validated against real storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultVersionRepositoryTest {

    private fun storageWithInstalled(): StorageManager {
        val storage = StorageManager(ApplicationProvider.getApplicationContext())
        storage.prepareDirectories()
        storage.writeMetadata(
            InstalledVersionMetadata(
                version = "1.21",
                installedAt = 42L,
                source = "https://example.com/1.21.json",
                installerVersion = 1,
                state = InstallState.INSTALLED
            )
        )
        storage.writeMetadata(
            InstalledVersionMetadata(
                version = "1.20.6",
                installedAt = 41L,
                source = "https://example.com/1.20.6.json",
                installerVersion = 1,
                state = InstallState.FAILED
            )
        )
        return storage
    }

    private fun repository(storage: StorageManager): DefaultVersionRepository {
        val downloader = Downloader(HttpClient())
        return DefaultVersionRepository(
            manifestService = ManifestService(HttpClient()),
            installer = VersionInstaller(
                storage = storage,
                downloader = downloader,
                libraryInstaller = LibraryInstaller(storage, downloader),
                assetInstaller = AssetInstaller(storage, downloader),
                verificationService = VerificationService(storage)
            ),
            storage = storage
        )
    }

    @Test
    fun `observeInstalledStates reflects disk metadata`() = runBlocking {
        val storage = storageWithInstalled()
        val states = repository(storage).observeInstalledStates().first()
        assertEquals(InstallState.INSTALLED, states["1.21"])
        assertEquals(InstallState.FAILED, states["1.20.6"])
    }

    @Test
    fun `remove deletes the version directory and re-syncs states`() = runBlocking {
        val storage = storageWithInstalled()
        val repo = repository(storage)
        assertTrue(storage.versionDirectory("1.21").exists())
        assertTrue(repo.remove("1.21").isSuccess)
        assertFalse(storage.versionDirectory("1.21").exists())
        val states = repo.observeInstalledStates().first()
        assertFalse(states.containsKey("1.21"))
        assertEquals(InstallState.FAILED, states["1.20.6"])
    }

    @Test
    fun `remove of an unknown version fails gracefully`() = runBlocking {
        val repo = repository(storageWithInstalled())
        assertTrue(repo.remove("does-not-exist").isFailure)
    }

    @Test
    fun `install pipeline returns a progress flow that cannot crash`() = runBlocking {
        // No network in tests: the flow must end in a failure progress
        // (or an error progress) instead of throwing.
        val repo = repository(storageWithInstalled())
        val version = MinecraftVersion(
            id = "1.21",
            type = com.lumocraft.app.domain.version.VersionType.RELEASE,
            url = "http://127.0.0.1:1/1.21.json",
            releaseTime = 1_718_240_000_000L,
            time = 1_718_240_000_000L,
            sha1 = null,
            size = null
        )
        val progresses = repo.install(version).toList()
        assertTrue(progresses.isNotEmpty())
        // Either a terminal failure or an error progress — never a throw.
        assertTrue(progresses.last().error != null)
    }
}