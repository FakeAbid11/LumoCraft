package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.launch.LaunchErrorType
import com.lumocraft.app.domain.launch.LaunchFailure

/**
 * Turns a non-zero exit code and the recent console output into a typed
 * [LaunchFailure]. Patterns are matched against the tail of the session
 * log; the raw log file itself is always preserved for diagnosis.
 */
class CrashAnalyzer {

    fun analyze(exitCode: Int, recentLines: List<String>): LaunchFailure {
        val tail = recentLines.takeLast(MAX_SCAN_LINES).joinToString("\n").lowercase()
        return when {
            tail.contains("could not find or load main class") ||
                tail.contains("unable to initialize main class") ->
                LaunchFailure(LaunchErrorType.MAIN_CLASS_MISSING, excerpt(recentLines))

            tail.contains("error occurred during initialization of vm") ||
                tail.contains("could not reserve enough space for object heap") ||
                tail.contains("unrecognized option") ||
                tail.contains("unrecognized vm option") ->
                LaunchFailure(LaunchErrorType.JVM_INITIALIZATION_FAILURE, excerpt(recentLines))

            tail.contains("unsatisfiedlinkerror") ||
                tail.contains("failed to locate library") ||
                tail.contains("cannot open shared object file") ||
                tail.contains("failed to create window") ||
                tail.contains("x11 display") ->
                LaunchFailure(LaunchErrorType.NATIVE_LIBRARY_MISSING, excerpt(recentLines))

            else -> LaunchFailure(
                LaunchErrorType.GAME_CRASHED,
                excerpt(recentLines)
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