package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchFailure

/**
 * Turns a non-zero exit code and the recent console output into a typed
 * [LaunchFailure]. Patterns are matched against the tail of the session
 * log; the raw log file itself is always preserved for diagnosis.
 *
 * Matching is ordered most-specific-first so that e.g. an OOM raised
 * during JVM startup is reported as a JVM initialization failure and not
 * as a generic game crash.
 */
class CrashAnalyzer {

    fun analyze(exitCode: Int, recentLines: List<String>): LaunchFailure {
        val tail = recentLines.takeLast(MAX_SCAN_LINES).joinToString("\n").lowercase()
        val excerpt = excerpt(recentLines)
        return when {
            // Main class / classpath problems.
            tail.contains("could not find or load main class") ||
                tail.contains("unable to initialize main class") ||
                tail.contains("classnotfoundexception") ||
                tail.contains("nosuchmethoderror") ||
                tail.contains("no main manifest attribute") ->
                LaunchFailure(LaunchErrorType.MAIN_CLASS_MISSING, excerpt)

            // JVM startup / configuration problems (memory settings, flags).
            tail.contains("error occurred during initialization of vm") ||
                tail.contains("could not reserve enough space for object heap") ||
                tail.contains("invalid maximum heap size") ||
                tail.contains("invalid initial heap size") ||
                tail.contains("unrecognized option") ||
                tail.contains("unrecognized vm option") ||
                tail.contains("unable to allocate") ||
                tail.contains("insufficient memory for the java runtime") ->
                LaunchFailure(LaunchErrorType.JVM_INITIALIZATION_FAILURE, excerpt)

            // Out-of-memory during the game itself.
            tail.contains("java.lang.outofmemoryerror") ||
                tail.contains("out of memory") ||
                tail.contains("heap space") ||
                tail.contains("direct buffer memory") ||
                tail.contains("metaspace") ->
                LaunchFailure(LaunchErrorType.JVM_INITIALIZATION_FAILURE, excerpt)

            // Native library problems (LWJGL, GLFW, JNA).
            tail.contains("unsatisfiedlinkerror") ||
                tail.contains("unsatisfied link") ||
                tail.contains("failed to locate library") ||
                tail.contains("failed to load library") ||
                tail.contains("could not load library") ||
                tail.contains("cannot open shared object file") ||
                tail.contains("no lwjgl in java.library.path") ||
                tail.contains("liblwjgl") ||
                tail.contains("glfw error") ||
                tail.contains("glfw window") ||
                tail.contains("glfw") ||
                tail.contains("failed to create window") ||
                tail.contains("x11 display") ||
                tail.contains("no suitable device") ||
                tail.contains("no x11 display") ->
                LaunchFailure(LaunchErrorType.NATIVE_LIBRARY_MISSING, excerpt)

            // Storage problems (launcher layout, disk full, read-only).
            tail.contains("failed to create directory") ||
                tail.contains("cannot create directory") ||
                tail.contains("storage not ready") ||
                tail.contains("no space left on device") ||
                tail.contains("read-only file system") ||
                tail.contains("directory is not writable") ->
                LaunchFailure(LaunchErrorType.STORAGE_UNAVAILABLE, excerpt)

            // File system access problems.
            tail.contains("permission denied") ||
                tail.contains("access denied") ||
                tail.contains("operation not permitted") ->
                LaunchFailure(LaunchErrorType.PERMISSION_DENIED, excerpt)

            // Install/launch metadata writes.
            tail.contains("metadatawrite failed") ||
                tail.contains("failed to write install metadata") ||
                tail.contains("failed to write metadata") ->
                LaunchFailure(LaunchErrorType.METADATA_WRITE_FAILURE, excerpt)

            // Network problems (DNS, unreachable hosts, timeouts).
            tail.contains("unknownhostexception") ||
                tail.contains("unable to resolve host") ||
                tail.contains("failed to connect") ||
                tail.contains("connection refused") ||
                tail.contains("connection timed out") ||
                tail.contains("connectexception") ||
                tail.contains("network is unreachable") ||
                tail.contains("no route to host") ||
                tail.contains("network unavailable") ||
                tail.contains("socketexception") ->
                LaunchFailure(LaunchErrorType.NETWORK_UNAVAILABLE, excerpt)

            // HTTP failures from download endpoints.
            tail.contains("httpstatus") ||
                tail.contains("http 4") ||
                tail.contains("http 5") ||
                tail.contains("http error") ->
                LaunchFailure(LaunchErrorType.HTTP_FAILURE, excerpt)

            // Corrupted or truncated downloads.
            tail.contains("sha-1 mismatch") ||
                tail.contains("sha1 mismatch") ||
                tail.contains("size mismatch for") ||
                tail.contains("corrupted download") ||
                tail.contains("checksum mismatch") ||
                tail.contains("invalid or corrupt jarfile") ||
                tail.contains("zip error") ||
                tail.contains("unexpected end of zip") ||
                tail.contains("unexpected end of zlib") ->
                LaunchFailure(LaunchErrorType.CORRUPTED_DOWNLOAD, excerpt)

            else -> LaunchFailure(
                LaunchErrorType.GAME_CRASHED,
                excerpt
            )
        }
    }

    private fun excerpt(lines: List<String>): String =
        lines.takeLast(EXCERPT_LINES).joinToString("\n")

    private companion object {
        const val MAX_SCAN_LINES = 300
        const val EXCERPT_LINES = 40
    }
}