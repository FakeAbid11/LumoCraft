package com.lumocraft.app.domain.launch

/** Failure while building or validating a launch; [message] is displayable. */
class LaunchException(message: String) : Exception(message)