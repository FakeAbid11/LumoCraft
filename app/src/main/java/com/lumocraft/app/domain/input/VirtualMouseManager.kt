package com.lumocraft.app.domain.input

import kotlinx.coroutines.flow.StateFlow

/** Logical state of the virtual cursor (buttons, lock, visibility). */
data class VirtualMouseState(
    val leftPressed: Boolean = false,
    val rightPressed: Boolean = false,
    val middlePressed: Boolean = false,
    val locked: Boolean = false,
    val visible: Boolean = true,
    /** Pending wheel delta, drained via [VirtualMouseManager.consumeScroll]. */
    val scrollAccumulator: Float = 0f
)

/**
 * Launcher-level virtual mouse: cursor position, drag movement,
 * adjustable sensitivity, left/right/middle click and scroll emulation.
 * Position is kept outside the [StateFlow] on purpose — it updates on
 * every pointer move and would otherwise trigger needless UI work.
 */
interface VirtualMouseManager {

    /** Low-frequency state: buttons, lock, visibility, scroll. */
    val state: StateFlow<VirtualMouseState>

    /** Current cursor position in surface pixels. */
    val positionX: Float
    val positionY: Float

    /** Fast-path callback fired on every position change (UI redraw). */
    fun setUiListener(listener: (() -> Unit)?)

    /** Fast-path callback fired with relative deltas while locked. */
    fun setMoveListener(listener: ((dx: Float, dy: Float) -> Unit)?)

    /** Clamps the cursor to the surface. */
    fun setBounds(width: Float, height: Float)

    /** Moves the cursor by [dx],[dy] scaled by [scale] and [invertY]. */
    fun moveBy(dx: Float, dy: Float, scale: Float, invertY: Boolean)

    /** Places the cursor at a surface point (TOUCH mode). */
    fun setPosition(x: Float, y: Float)

    fun press(button: MouseButton)
    fun release(button: MouseButton)

    /** LOCKED mode: cursor hidden, drags become pure relative deltas. */
    fun setLocked(locked: Boolean)

    fun setVisible(visible: Boolean)

    /** Emulates scroll wheel input. */
    fun addScroll(delta: Float)

    /** Drains and clears the accumulated scroll delta. */
    fun consumeScroll(): Float
}