package com.lumocraft.app.domain.launch

/** Immutable snapshot of a launch session, exposed through StateFlow. */
data class LaunchProgress(
    val state: LaunchState = LaunchState.IDLE,
    val message: String? = null,
    val exitCode: Int? = null,
    val failure: LaunchFailure? = null
) {
    val isRunning: Boolean get() = state in RUNNING_STATES

    private companion object {
        val RUNNING_STATES = setOf(
            LaunchState.PREPARING,
            LaunchState.VALIDATING,
            LaunchState.BUILDING_CLASSPATH,
            LaunchState.BUILDING_ARGUMENTS,
            LaunchState.STARTING_JAVA,
            LaunchState.RUNNING,
            LaunchState.STOPPING
        )
    }
}