package com.lumocraft.app.data.storage

import androidx.test.core.app.ApplicationProvider
import com.lumocraft.app.domain.loader.LoaderMetadata
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.InstalledVersionMetadata
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the on-disk launcher layout: every path method resolves
 * under the launcher root, metadata round-trips through JSON and
 * traversal segments in library paths are rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageManagerTest {

    private fun storage(): StorageManager =
        StorageManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `all paths live under the launcher root`() {
        val storage = storage()
        val root = storage.launcherRoot()
        val paths = listOf(
            storage.versionsDirectory(),
            storage.assetsDirectory(),
            storage.indexesDirectory(),
            storage.objectsDirectory(),
            storage.librariesDirectory(),
            storage.logsDirectory(),
            storage.runtimeDirectory(),
            storage.inputDirectory(),
            storage.loaderDirectory(LoaderType.FABRIC),
            storage.loaderCacheDirectory(LoaderType.FABRIC),
            storage.runtimeMetadataFile(),
            storage.versionDirectory("1.21"),
            storage.versionJsonFile("1.21"),
            storage.metadataFile("1.21"),
            storage.assetIndexFile("1.21"),
            storage.libraryFile("a/b/c.jar"),
            storage.objectFile("0123456789abcdef"),
            storage.loggingConfigFile("1.21", "client.json")
        )
        paths.forEach { path ->
            assertTrue("$path escapes launcher root", path.absolutePath.startsWith(root.absolutePath))
        }
    }

    @Test
    fun `prepareDirectories creates the full layout`() {
        val storage = storage()
        storage.prepareDirectories()
        listOf(
            storage.versionsDirectory(),
            storage.librariesDirectory(),
            storage.indexesDirectory(),
            storage.objectsDirectory(),
            storage.logsDirectory(),
            storage.runtimeDirectory(),
            storage.inputProfilesDirectory(),
            storage.loaderInstancesDirectory(LoaderType.FABRIC),
            storage.loaderCacheDirectory(LoaderType.FABRIC)
        ).forEach { assertTrue("missing: $it", it.isDirectory) }
    }

    @Test
    fun `version metadata round-trips`() {
        val storage = storage()
        storage.prepareDirectories()
        val metadata = InstalledVersionMetadata(
            version = "1.21",
            installedAt = 123456789L,
            source = "https://example.com/1.21.json",
            installerVersion = 1,
            state = InstallState.INSTALLED
        )
        storage.writeMetadata(metadata)
        assertEquals(metadata, storage.readMetadata("1.21"))
        assertEquals(mapOf("1.21" to InstallState.INSTALLED), storage.readInstallStates())
    }

    @Test
    fun `corrupt metadata parses to null`() {
        val storage = storage()
        storage.prepareDirectories()
        storage.metadataFile("broken").parentFile.mkdirs()
        storage.metadataFile("broken").writeText("{ not json")
        assertNull(storage.readMetadata("broken"))
    }

    @Test
    fun `libraryFile rejects traversal segments`() {
        val storage = storage()
        val root = storage.librariesDirectory()
        val safe = storage.libraryFile("../escape/evil.jar")
        assertFalse(safe.absolutePath.contains(".."))
        assertTrue(safe.absolutePath.startsWith(root.absolutePath))
        assertEquals(File(root, "escape/evil.jar"), safe)
    }

    @Test
    fun `loader metadata round-trips`() {
        val storage = storage()
        storage.prepareDirectories()
        val metadata = LoaderMetadata(
            instanceId = "fabric-loader-0.15.11-1.21",
            type = LoaderType.FABRIC,
            minecraftVersion = "1.21",
            loaderVersion = "0.15.11",
            installerVersion = "fabric-installer-1",
            installedAt = 42L,
            state = InstallState.INSTALLED
        )
        storage.writeLoaderMetadata(metadata)
        assertEquals(metadata, storage.readLoaderMetadata(LoaderType.FABRIC, metadata.instanceId))
        assertEquals(mapOf(metadata.instanceId to metadata), storage.readAllLoaderMetadata(LoaderType.FABRIC))
    }

    @Test
    fun `version ids are sanitized for the filesystem`() {
        val storage = storage()
        val dir = storage.versionDirectory("1.21  snapshot/..")
        assertFalse(dir.name.contains('/'))
        assertFalse(dir.name.contains(' '))
        assertNotNull(storage.versionJsonFile("1.21-rc1"))
    }

    @Test
    fun `removeVersionDirectory deletes the whole version`() {
        val storage = storage()
        storage.prepareDirectories()
        val metadata = InstalledVersionMetadata(
            version = "1.21",
            installedAt = 1L,
            source = "x",
            installerVersion = 1,
            state = InstallState.INSTALLED
        )
        storage.writeMetadata(metadata)
        assertTrue(storage.versionDirectory("1.21").exists())
        assertTrue(storage.removeVersionDirectory("1.21"))
        assertFalse(storage.versionDirectory("1.21").exists())
    }
}