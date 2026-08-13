package com.lumocraft.app.domain.input

/** Kind of a raw pointer action. */
enum class TouchActionKind { DOWN, MOVE, UP, CANCEL }

/** Raw pointer sample from the platform, in surface pixels. */
data class RawTouch(
    val x: Float,
    val y: Float,
    val action: TouchActionKind,
    val pointerId: Int,
    val timestampMs: Long
)

/** Tunables for gesture recognition. */
data class TouchConfig(
    /** Max movement (px) before a press stops being a tap. */
    val tapSlop: Float = 24f,
    /** Hold time (ms) that turns a press into a long press. */
    val longPressMs: Long = 500,
    /** Window (ms) in which a second tap forms a double tap. */
    val doubleTapWindowMs: Long = 300,
    /** Minimum end speed (px/s) for a swipe. */
    val swipeVelocityThreshold: Float = 500f,
    /** Window (ms) in which a second pointer counts as a multi-touch. */
    val multiTouchIntervalMs: Long = 300
)

/**
 * Normalizes raw touch input, tracks pointers, converts coordinates and
 * recognizes gestures. Fully platform independent: feeds come in as
 * [RawTouch], clean [Gesture]s come out. Single and multi touch,
 * tap/double tap/long press/drag/swipe are supported.
 */
interface TouchEventMapper {

    /** Feeds one pointer sample and returns the gestures it produced. */
    fun feed(event: RawTouch): List<Gesture>

    /** Drops every tracked pointer (surface lost/cleared). */
    fun reset()

    fun updateConfig(config: TouchConfig)
}