package com.lumocraft.app.data.runtime

import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pure-JVM tests: a fake runtime tree is created under a temp folder and
 * [RuntimeVerifier] is exercised against it. No Minecraft is launched.
 */
class RuntimeVerifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val verifier = RuntimeVerifier()

    private fun fakeRuntime(
        version: String = "17",
        withModules: Boolean = true,
        withServer: Boolean = true,
        withJmods: Boolean = true,
        withExecutableBit: Boolean = true,
        releaseContent: String? = null,
    ): Pair<File, RuntimeInfo> {
        val root = tmp.newFolder("runtime")
        File(root, "bin").mkdirs()
        File(root, "lib").mkdirs()
        File(root, "lib/server").mkdirs()
        File(root, "lib/modules").writeText("jrt-image")
        File(root, "jmods").mkdirs()
        File(root, "jmods/java.base.jmod").writeText("jmod")
        listOf("bin/java", "bin/javac", "bin/keytool").forEach { name ->
            val file = File(root, name)
            file.writeText("#!/bin/sh\necho java\n")
            if (withExecutableBit) file.setExecutable(true)
        }
        File(root, "lib/server/libjvm.so").writeText("so")
        File(root, "release").writeText(releaseContent ?: "JAVA_VERSION=\"17.0.11+9\"\n")
        if (!withModules) File(root, "lib/modules").delete()
        if (!withServer) File(root, "lib/server/libjvm.so").delete()
        if (!withJmods) File(root, "jmods").deleteRecursively()
        val info = RuntimeInfo(
            id = "java17",
            version = version,
            architecture = RuntimeArchitecture.ARM64_V8A,
            vendor = "temurin",
            path = root.absolutePath,
            installedAt = System.currentTimeMillis(),
            isDefault = true,
            status = RuntimeStatus.INSTALLED,
            checksum = null
        )
        return root to info
    }

    @Test
    fun `complete runtime verifies ok`() = runBlocking {
        val (_, info) = fakeRuntime()
        val report = verifier.verify(info)
        assertTrue(report.binariesOk)
        assertTrue(report.metadataOk)
        assertTrue(report.checksumOk)
        assertTrue(report.modulesOk)
        assertTrue(report.serverOk)
        assertTrue(report.jmodsOk)
        assertTrue(report.rootOk)
        assertTrue(report.ok)
    }

    @Test
    fun `missing modules fails the runtime`() = runBlocking {
        val (_, info) = fakeRuntime(withModules = false)
        val report = verifier.verify(info)
        assertFalse(report.modulesOk)
        assertFalse(report.ok)
        assertTrue("lib/modules" in report.missingFiles)
    }

    @Test
    fun `missing server library fails the runtime`() = runBlocking {
        val (_, info) = fakeRuntime(withServer = false)
        val report = verifier.verify(info)
        assertFalse(report.serverOk)
        assertFalse(report.ok)
        assertTrue("lib/server/libjvm.so" in report.missingFiles)
    }

    @Test
    fun `missing jmods fails the runtime`() = runBlocking {
        val (_, info) = fakeRuntime(withJmods = false)
        val report = verifier.verify(info)
        assertFalse(report.jmodsOk)
        assertFalse(report.ok)
    }

    @Test
    fun `missing bin java fails binaries and root consistency`() = runBlocking {
        val (root, info) = fakeRuntime()
        File(root, "bin/java").delete()
        val report = verifier.verify(info)
        assertFalse(report.binariesOk)
        assertFalse(report.rootOk)
        assertFalse(report.ok)
        assertTrue("bin/java" in report.missingFiles)
    }

    @Test
    fun `nonexecutable binary fails the runtime`() = runBlocking {
        val (_, info) = fakeRuntime(withExecutableBit = false)
        val report = verifier.verify(info)
        assertFalse(report.binariesOk)
        assertFalse(report.ok)
    }

    @Test
    fun `missing directory reports the path`() = runBlocking {
        val info = RuntimeInfo(
            id = "gone",
            version = "17",
            architecture = RuntimeArchitecture.ARM64_V8A,
            vendor = "temurin",
            path = File(tmp.root, "does-not-exist").absolutePath,
            installedAt = 0L,
            isDefault = false,
            status = RuntimeStatus.INSTALLED,
            checksum = null
        )
        val report = verifier.verify(info)
        assertFalse(report.ok)
        assertTrue(report.missingFiles.single().contains("does-not-exist"))
    }

    @Test
    fun `checksum mismatch is reported with detail`() = runBlocking {
        val (root, info) = fakeRuntime()
        val withChecksum = info.copy(checksum = "0".repeat(64))
        val report = verifier.verify(withChecksum)
        assertFalse(report.checksumOk)
        assertFalse(report.ok)
        assertEquals("release", report.corruptFiles.single())
        assertTrue(report.checksumDetail.orEmpty().contains("expected"))
    }

    @Test
    fun `matching checksum passes`() = runBlocking {
        val (root, info) = fakeRuntime()
        val sha = com.lumocraft.app.data.network.HashUtils.sha256(File(root, "release"))
        val report = verifier.verify(info.copy(checksum = sha))
        assertTrue(report.checksumOk)
        assertNull(report.checksumDetail)
    }

    @Test
    fun `version mismatch in release file fails metadata`() = runBlocking {
        val (_, info) = fakeRuntime(version = "21", releaseContent = "JAVA_VERSION=\"17.0.11+9\"")
        val report = verifier.verify(info)
        assertFalse(report.metadataOk)
        assertFalse(report.ok)
    }
}