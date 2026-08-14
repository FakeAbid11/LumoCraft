package com.lumocraft.app.domain.launch

/** Lifecycle of a launch session, in execution order. */
enum class LaunchState {
    IDLE,
    PREPARING,
    VALIDATING,
    BUILDING_CLASSPATH,
    BUILDING_ARGUMENTS,
    STARTING_JAVA,
    RUNNING,
    STOPPING,
    FINISHED,
    FAILED
}