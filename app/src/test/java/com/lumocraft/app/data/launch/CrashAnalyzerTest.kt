package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.launch.LaunchErrorType
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashAnalyzerTest {

    private val analyzer = CrashAnalyzer()

    @Test
    fun `missing main class is recognized`() {
        val failure = analyzer.analyze(1, listOf("Error: Could not find or load main class net.minecraft.client.main.Main"))
        assertEquals(LaunchErrorType.MAIN_CLASS_MISSING, failure.type)
    }

    @Test
    fun `classnotfoundexception maps to main class missing`() {
        val failure = analyzer.analyze(1, listOf("java.lang.ClassNotFoundException: net.minecraft.client.main.Main"))
        assertEquals(LaunchErrorType.MAIN_CLASS_MISSING, failure.type)
    }

    @Test
    fun `jvm initialization failures are recognized`() {
        val cases = listOf(
            "Error occurred during initialization of VM",
            "Could not reserve enough space for object heap",
            "Invalid maximum heap size: -Xmx99999M",
            "Unrecognized option: -Dfoo",
            "Unrecognized VM option 'UseZGC'"
        )
        cases.forEach { line ->
            assertEquals("unmapped: $line", LaunchErrorType.JVM_INITIALIZATION_FAILURE, analyzer.analyze(1, listOf(line)).type)
        }
    }

    @Test
    fun `out of memory maps to jvm initialization failure`() {
        val failure = analyzer.analyze(1, listOf("java.lang.OutOfMemoryError: Java heap space"))
        assertEquals(LaunchErrorType.JVM_INITIALIZATION_FAILURE, failure.type)
    }

    @Test
    fun `native library problems are recognized`() {
        val cases = listOf(
            "java.lang.UnsatisfiedLinkError: no lwjgl in java.library.path",
            "Failed to locate library: libglfw.so",
            "cannot open shared object file: liblwjgl.so",
            "Could not create GLFW window",
            "Exception in thread \"main\" org.lwjgl.glfw.GLFWErrorCallback",
            "failed to create window"
        )
        cases.forEach { line ->
            assertEquals("unmapped: $line", LaunchErrorType.NATIVE_LIBRARY_MISSING, analyzer.analyze(1, listOf(line)).type)
        }
    }

    @Test
    fun `unknown failures map to game crashed`() {
        val failure = analyzer.analyze(1, listOf("Something unexpected happened"))
        assertEquals(LaunchErrorType.GAME_CRASHED, failure.type)
    }

    @Test
    fun `storage and permission problems are recognized`() {
        val cases = listOf(
            "Failed to create directory: /data/minecraft/versions",
            "java.io.IOException: No space left on device",
            "Directory is not writable: /data/minecraft",
            "java.io.FileNotFoundException: /data/minecraft/metadata.json (Permission denied)"
        )
        val expected = listOf(
            LaunchErrorType.STORAGE_UNAVAILABLE,
            LaunchErrorType.STORAGE_UNAVAILABLE,
            LaunchErrorType.STORAGE_UNAVAILABLE,
            LaunchErrorType.PERMISSION_DENIED
        )
        cases.zip(expected).forEach { (line, type) ->
            assertEquals("unmapped: $line", type, analyzer.analyze(1, listOf(line)).type)
        }
    }

    @Test
    fun `metadata, network and http problems are recognized`() {
        val cases = listOf(
            "INSTALL version=1.8.9 stage=PREPARING metadataWrite failed",
            "java.net.UnknownHostException: launchermeta.mojang.com",
            "java.net.ConnectException: Connection refused",
            "HttpStatusException: HTTP 404 Not Found",
            "SHA-1 mismatch for https://launcher.mojang.com/v1/objects/x.json",
            "java.util.zip.ZipException: Unexpected end of ZLIB input stream"
        )
        val expected = listOf(
            LaunchErrorType.METADATA_WRITE_FAILURE,
            LaunchErrorType.NETWORK_UNAVAILABLE,
            LaunchErrorType.NETWORK_UNAVAILABLE,
            LaunchErrorType.HTTP_FAILURE,
            LaunchErrorType.CORRUPTED_DOWNLOAD,
            LaunchErrorType.CORRUPTED_DOWNLOAD
        )
        cases.zip(expected).forEach { (line, type) ->
            assertEquals("unmapped: $line", type, analyzer.analyze(1, listOf(line)).type)
        }
    }

    @Test
    fun `excerpt keeps the tail of the log`() {
        val lines = (1..100).map { "line $it" }
        val failure = analyzer.analyze(1, lines)
        val detail = failure.detail.orEmpty()
        assertEquals("line 61", detail.lines().first())
        assertEquals("line 100", detail.lines().last())
        assertEquals(40, detail.lines().size)
    }
}