package com.lumocraft.app.domain.launch

/**
 * Recognized launch failures. The UI maps each type to a user-friendly
 * message; [LaunchFailure.detail] always carries the raw evidence
 * (missing file paths, log excerpt) for diagnosis. The full session log
 * file is preserved regardless of the failure type.
 */
enum class LaunchErrorType {
    ACCOUNT_MISSING,
    RUNTIME_MISSING,
    VERSION_MISSING,
    LIBRARIES_MISSING,
    ASSETS_MISSING,
    CLIENT_JAR_MISSING,
    MAIN_CLASS_MISSING,
    NATIVE_LIBRARY_MISSING,
    INVALID_CLASSPATH,
    JVM_INITIALIZATION_FAILURE,
    GAME_CRASHED,
    CANCELLED,
    UNKNOWN
}

/** A terminal launch failure: a typed cause plus raw evidence. */
data class LaunchFailure(
    val type: LaunchErrorType,
    val detail: String? = null
)