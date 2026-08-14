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