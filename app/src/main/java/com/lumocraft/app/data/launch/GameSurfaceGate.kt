package com.lumocraft.app.data.launch

import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates the "surface before LWJGL init" ordering between the UI and
 * the launch pipeline.
 *
 * The game JVM runs *in-process*, so its patched LWJGL must find a live
 * `Surface` (handed to the native bridge via
 * [net.kdt.pojavlaunch.utils.JREUtils.attachSurface]) before it creates its
 * GL context. The pipeline therefore parks just before starting the JVM and
 * waits here until [GameActivity][com.lumocraft.app.ui.game.GameActivity]
 * reports its surface is ready.
 *
 * The wait is **opt-in and non-fatal**:
 * - Callers that will host a surface announce it with [expectSurface]; the
 *   pipeline only waits when a surface [isSurfaceExpected]. Console-only /
 *   headless launches (and unit tests, which never set the gate) skip the
 *   wait entirely, so behaviour is unchanged when the rendering bridge is
 *   absent.
 * - [awaitSurface] is time-bounded and returns `null` on timeout rather than
 *   failing, so a surface that never arrives degrades to a best-effort start
 *   instead of hanging the launch.
 *
 * A single instance is shared through the application container: the pipeline
 * awaits, the activity provides. It is reused across retries via [clear].
 */
class GameSurfaceGate {

    private val _surface = MutableStateFlow<Surface?>(null)

    /** The live render surface, or null until one is provided / after [clear]. */
    val surface: StateFlow<Surface?> = _surface.asStateFlow()

    @Volatile
    private var expected: Boolean = false

    /** True once a consumer has committed to hosting a surface for this launch. */
    val isSurfaceExpected: Boolean get() = expected

    /**
     * Declares that a surface is coming (the game view is being shown), so the
     * pipeline knows to wait for it. Called before launching the game view.
     */
    fun expectSurface() {
        expected = true
    }

    /** Publishes the live surface, unblocking any pending [awaitSurface]. */
    fun provideSurface(surface: Surface) {
        _surface.value = surface
    }

    /** Resets the gate for the next launch (surface torn down / session ended). */
    fun clear() {
        expected = false
        _surface.value = null
    }

    /**
     * Suspends until a surface is provided or [timeoutMs] elapses. Returns the
     * surface, or null on timeout. A surface already present returns
     * immediately.
     */
    suspend fun awaitSurface(timeoutMs: Long): Surface? =
        withTimeoutOrNull(timeoutMs) { _surface.filterNotNull().first() }
}
