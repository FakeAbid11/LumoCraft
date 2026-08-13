package com.lumocraft.app.domain.launch

/**
 * Failure while building or validating a launch; [message] is displayable.
 * [type] lets the UI show a specific error (natives missing, arch
 * mismatch, ...) instead of a generic one.
 */
class LaunchException(
    val type: LaunchErrorType = LaunchErrorType.UNKNOWN,
    message: String
) : Exception(message)