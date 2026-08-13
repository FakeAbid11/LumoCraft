package com.lumocraft.app.domain.input

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Buttons exposed by Android game controllers. */
enum class ControllerButton {
    A, B, X, Y,
    LB, RB,
    BACK, START, GUIDE,
    LEFT_STICK, RIGHT_STICK,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT
}

/** Stick and trigger axes exposed by game controllers. */
enum class ControllerAxisKind {
    LEFT_X, LEFT_Y,
    RIGHT_X, RIGHT_Y,
    LEFT_TRIGGER, RIGHT_TRIGGER
}

/** One raw controller event. */
sealed interface ControllerEvent {
    data class Axis(val axis: ControllerAxisKind, val value: Float) : ControllerEvent
    data class Button(val button: ControllerButton, val pressed: Boolean) : ControllerEvent
}

/** Snapshot of all controller inputs. */
data class ControllerState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val buttons: Set<ControllerButton> = emptySet()
) {
    val dpadUp: Boolean get() = ControllerButton.DPAD_UP in buttons
    val dpadDown: Boolean get() = ControllerButton.DPAD_DOWN in buttons
    val dpadLeft: Boolean get() = ControllerButton.DPAD_LEFT in buttons
    val dpadRight: Boolean get() = ControllerButton.DPAD_RIGHT in buttons

    val active: Boolean
        get() = buttons.isNotEmpty() || leftX != 0f || leftY != 0f ||
            rightX != 0f || rightY != 0f || leftTrigger != 0f || rightTrigger != 0f
}

/**
 * Android game controller support: sticks, triggers, buttons and the
 * D-pad, exposed as a clean [ControllerState] plus per-event streams.
 * Platform code maps raw Android axis/button ids and feeds them in.
 */
interface ControllerManager {

    val state: StateFlow<ControllerState>

    /** Raw axis/button events as they happen. */
    val events: SharedFlow<ControllerEvent>

    /** Registers for device connect/disconnect detection. */
    fun register()

    fun unregister()

    fun onAxis(axis: ControllerAxisKind, value: Float)

    fun onButton(button: ControllerButton, pressed: Boolean)
}