package com.lumocraft.app.domain.input

/** Lifecycle of a drag gesture. */
enum class DragState { STARTED, MOVED, ENDED, CANCELLED }

/** Cardinal direction of a [Gesture.Swipe]. */
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Reusable gesture models produced by the touch pipeline. Later phases
 * (gesture macros, custom bindings, accessibility) consume these
 * without touching the mapper. Coordinates are in surface pixels.
 */
sealed interface Gesture {
    val pointerId: Int

    /** Quick press without movement. */
    data class Tap(override val pointerId: Int, val x: Float, val y: Float) : Gesture

    /** Second tap within the double-tap window. */
    data class DoubleTap(override val pointerId: Int, val x: Float, val y: Float) : Gesture

    /** Press held past the long-press threshold. */
    data class LongPress(override val pointerId: Int, val x: Float, val y: Float) : Gesture

    /** Single-finger drag; [state] marks the lifecycle. */
    data class Drag(
        override val pointerId: Int,
        val x: Float,
        val y: Float,
        val startX: Float,
        val startY: Float,
        val dx: Float,
        val dy: Float,
        val state: DragState
    ) : Gesture

    /** Fast drag ending past the swipe threshold. */
    data class Swipe(
        override val pointerId: Int,
        val x: Float,
        val y: Float,
        val velocityX: Float,
        val velocityY: Float,
        val direction: SwipeDirection
    ) : Gesture

    /** Two-finger drag (scroll/pinch surface). */
    data class MultiDrag(
        override val pointerId: Int,
        val firstX: Float,
        val firstY: Float,
        val secondX: Float,
        val secondY: Float,
        val dx: Float,
        val dy: Float,
        val state: DragState
    ) : Gesture

    /** Two (or more) quick taps at the same spot. */
    data class MultiTap(
        override val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointerCount: Int
    ) : Gesture
}