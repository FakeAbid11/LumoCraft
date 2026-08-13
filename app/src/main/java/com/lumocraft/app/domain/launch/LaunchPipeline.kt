package com.lumocraft.app.domain.launch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrates a game session from a ready [LaunchContext]: validation,
 * classpath resolution, argument building, native extraction, the Java
 * process, log streaming and crash analysis.
 *
 * Fully independent from the UI — the UI only observes [state] and
 * [logLines]. The interface is intentionally small so later phases
 * (Fabric, Forge, custom JVM/game args, renderer options) can swap the
 * implementation without touching callers.
 */
interface LaunchPipeline {

    /** Current launch progress; starts at [LaunchState.IDLE]. */
    val state: StateFlow<LaunchProgress>

    /** Replaying stream of session log lines (launcher + game output). */
    val logLines: Flow<String>

    /** Starts a new session. No-op while another session is running. */
    fun launch(context: LaunchContext)

    /** Stops the running session (kills the Java process if started). */
    fun cancel()
}