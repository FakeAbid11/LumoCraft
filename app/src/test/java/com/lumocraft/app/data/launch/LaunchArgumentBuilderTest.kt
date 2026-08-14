package com.lumocraft.app.data.launch

import androidx.test.core.app.ApplicationProvider
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.account.OfflineAccount
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.native.RendererProfile
import com.lumocraft.app.domain.runtime.JvmConfiguration
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM argument generation for a small version JSON: token placeholders
 * resolve to launcher paths, JVM configuration flags are injected and
 * the JNI environment reaches the command line as -D flags.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchArgumentBuilderTest {

    private fun storageWithVersion(): StorageManager {
        val storage = StorageManager(ApplicationProvider.getApplicationContext())
        storage.prepareDirectories()
        val versionDir = storage.versionDirectory("1.21")
        versionDir.mkdirs()
        val json = JSONObject()
            .put("id", "1.21")
            .put("mainClass", "net.minecraft.client.main.Main")
            .put("arguments", JSONObject()
                .put("game", JSONArray().put("--username").put("${'$'}{auth_player_name}").put("--version").put("${'$'}{version_name}"))
                .put("jvm", JSONArray().put("-Dminecraft.version=${'$'}{version_name}")))
            .put("assets", "1.21")
        storage.versionJsonFile("1.21").writeText(json.toString())
        return storage
    }

    private fun context(storage: StorageManager): LaunchContext {
        val runtime = RuntimeInfo(
            id = "java17",
            version = "17",
            architecture = RuntimeArchitecture.ARM64_V8A,
            vendor = "temurin",
            path = File(storage.launcherRoot(), "runtime/java17").absolutePath,
            installedAt = 0L,
            isDefault = true,
            status = RuntimeStatus.INSTALLED,
            checksum = null
        )
        return LaunchContext(
            account = OfflineAccount(
                id = "1",
                username = "Steve",
                createdAt = 0L,
                isSelected = true
            ),
            versionId = "1.21",
            runtime = runtime,
            gameDirectory = storage.launcherRoot(),
            jvmConfiguration = JvmConfiguration(
                maxMemoryMB = 2048,
                minMemoryMB = 512,
                gcMode = JvmConfiguration.GcMode.G1,
                extraArguments = listOf("-Dcustom.flag=true")
            )
        )
    }

    @Test
    fun `tokens resolve to launcher values`() = runBlocking {
        val storage = storageWithVersion()
        val args = LaunchArgumentBuilder(storage).build(
            context = context(storage),
            classpath = "/cp/a.jar",
            environment = LaunchEnvironment(storage),
            nativesDirectory = storage.versionDirectory("1.21").let { File(it, "natives/arm64") },
            jniEnvironment = mapOf(
                "java.library.path" to "/natives",
                "org.lwjgl.librarypath" to "/natives"
            ),
            rendererProfile = RendererProfile()
        ).getOrThrow()

        assertEquals("net.minecraft.client.main.Main", args.mainClass)
        assertTrue(args.gameArguments.contains("--username"))
        assertTrue(args.gameArguments.contains("Steve"))
        assertTrue(args.gameArguments.contains("--version"))
        assertTrue(args.gameArguments.contains("1.21"))
        assertTrue(args.jvmArguments.contains("-Dminecraft.version=1.21"))
    }

    @Test
    fun `jvm configuration flags are included`() = runBlocking {
        val storage = storageWithVersion()
        val args = LaunchArgumentBuilder(storage).build(
            context = context(storage),
            classpath = "cp",
            environment = LaunchEnvironment(storage),
            nativesDirectory = File(storage.launcherRoot(), "natives")
        ).getOrThrow()

        assertTrue(args.jvmArguments.contains("-Xmx2048M"))
        assertTrue(args.jvmArguments.contains("-Xms512M"))
        assertTrue(args.jvmArguments.contains("-XX:+UseG1GC"))
        assertTrue(args.jvmArguments.contains("-Dcustom.flag=true"))
    }

    @Test
    fun `jni environment reaches the command line`() = runBlocking {
        val storage = storageWithVersion()
        val args = LaunchArgumentBuilder(storage).build(
            context = context(storage),
            classpath = "cp",
            environment = LaunchEnvironment(storage),
            nativesDirectory = File(storage.launcherRoot(), "natives"),
            jniEnvironment = mapOf(
                "java.library.path" to "/natives",
                "org.lwjgl.librarypath" to "/natives"
            )
        ).getOrThrow()

        assertTrue(args.jvmArguments.contains("-Djava.library.path=/natives"))
        assertTrue(args.jvmArguments.contains("-Dorg.lwjgl.librarypath=/natives"))
        assertTrue(args.jvmArguments.contains("-Dos.name=Linux"))
    }

    @Test
    fun `missing version json fails gracefully`() = runBlocking {
        val storage = StorageManager(ApplicationProvider.getApplicationContext())
        storage.prepareDirectories()
        val result = LaunchArgumentBuilder(storage).build(
            context = context(storage).copy(versionId = "does-not-exist"),
            classpath = "cp",
            environment = LaunchEnvironment(storage),
            nativesDirectory = File(storage.launcherRoot(), "natives")
        )
        assertTrue(result.isFailure)
    }
}