package com.lumocraft.app.data.runtime

import androidx.test.core.app.ApplicationProvider
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import com.lumocraft.app.domain.runtime.RuntimeStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DefaultRuntimeRepository] orchestration: disk state re-syncs,
 * verification flags a broken runtime as CORRUPTED and removal works.
 * The real [RuntimeVerifier] runs against a fake runtime tree — no
 * network, no Minecraft launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultRuntimeRepositoryTest {

    private fun storageWithRuntime(): StorageManager {
        val storage = StorageManager(ApplicationProvider.getApplicationContext())
        storage.prepareDirectories()
        val runtimeDir = storage.runtimeDirectoryFor("java17")
        File(runtimeDir, "bin").mkdirs()
        File(runtimeDir, "lib/server").mkdirs()
        File(runtimeDir, "jmods").mkdirs()
        listOf("bin/java", "bin/javac", "bin/keytool").forEach { name ->
            val file = File(runtimeDir, name)
            file.writeText("#!/bin/sh\n")
            file.setExecutable(true)
        }
        File(runtimeDir, "lib/modules").writeText("jrt")
        File(runtimeDir, "lib/server/libjvm.so").writeText("so")
        File(runtimeDir, "jmods/java.base.jmod").writeText("jmod")
        File(runtimeDir, "release").writeText("JAVA_VERSION=\"17.0.11+9\"\n")
        storage.runtimeMetadataFile().parentFile.mkdirs()
        storage.runtimeMetadataFile().writeText(
            JSONObject().put("runtimes", org.json.JSONArray().put(
                JSONObject()
                    .put("id", "java17")
                    .put("version", "17")
                    .put("arch", "arm64-v8a")
                    .put("vendor", "temurin")
                    .put("path", runtimeDir.absolutePath)
                    .put("installedAt", 0L)
                    .put("isDefault", true)
                    .put("status", RuntimeStatus.INSTALLED.name)
                    .put("checksum", "")
            )).toString()
        )
        return storage
    }

    private fun repository(storage: StorageManager): DefaultRuntimeRepository =
        DefaultRuntimeRepository(
            storage = storage,
            installer = RuntimeInstaller(storage, Downloader(HttpClient()), ArchiveExtractor()),
            verifier = RuntimeVerifier()
        )

    @Test
    fun `getDefaultRuntime returns the installed runtime`() = runBlocking {
        val storage = storageWithRuntime()
        val repo = repository(storage)
        val runtime = repo.getDefaultRuntime()
        assertTrue(runtime != null)
        assertEquals("java17", runtime?.id)
        assertEquals(RuntimeArchitecture.ARM64_V8A, runtime?.architecture)
    }

    @Test
    fun `verify marks a broken runtime as corrupt`() = runBlocking {
        val storage = storageWithRuntime()
        val repo = repository(storage)
        // Break the runtime: remove lib/modules.
        File(storage.runtimeDirectoryFor("java17"), "lib/modules").delete()
        val report = repo.verify("java17").getOrThrow()
        assertFalse(report.ok)
        val observed = repo.observeRuntimes().first().first { it.id == "java17" }
        assertEquals(RuntimeStatus.CORRUPTED, observed.status)
    }

    @Test
    fun `verify of a healthy runtime passes and stays installed`() = runBlocking {
        val storage = storageWithRuntime()
        val repo = repository(storage)
        val report = repo.verify("java17").getOrThrow()
        assertTrue(report.ok)
        assertEquals(RuntimeStatus.INSTALLED, repo.observeRuntimes().first().first { it.id == "java17" }.status)
    }

    @Test
    fun `remove deletes the runtime directory and metadata`() = runBlocking {
        val storage = storageWithRuntime()
        val repo = repository(storage)
        assertTrue(storage.runtimeDirectoryFor("java17").isDirectory)
        assertTrue(repo.remove("java17").isSuccess)
        assertFalse(storage.runtimeDirectoryFor("java17").exists())
        assertTrue(repo.observeRuntimes().first().none { it.id == "java17" })
    }

    @Test
    fun `jvm configuration round-trips through disk`() = runBlocking {
        val storage = storageWithRuntime()
        val repo = repository(storage)
        repo.saveJvmConfiguration(
            com.lumocraft.app.domain.runtime.JvmConfiguration(
                maxMemoryMB = 2048,
                minMemoryMB = 512,
                gcMode = com.lumocraft.app.domain.runtime.JvmConfiguration.GcMode.ZGC,
                extraArguments = listOf("-Dx=y")
            )
        )
        val loaded = repo.loadJvmConfiguration()
        assertEquals(2048, loaded.maxMemoryMB)
        assertEquals(512, loaded.minMemoryMB)
        assertEquals(com.lumocraft.app.domain.runtime.JvmConfiguration.GcMode.ZGC, loaded.gcMode)
        assertEquals(listOf("-Dx=y"), loaded.extraArguments)
    }

    @Test
    fun `getDefaultRuntime returns null for a missing runtime`() = runBlocking {
        val storage = StorageManager(ApplicationProvider.getApplicationContext())
        storage.prepareDirectories()
        val repo = repository(storage)
        assertNull(repo.getDefaultRuntime())
    }
}