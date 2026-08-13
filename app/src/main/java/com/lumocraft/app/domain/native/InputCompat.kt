package com.lumocraft.app.domain.native

/**
 * Input compatibility foundation — architecture only, no controls are
 * implemented yet. Later phases plug concrete implementations into
 * [InputEngine] (touch mapping, virtual mouse, keyboard, controller)
 * without touching the launcher or the pipeline.
 */

/** Raw pointer event from the Android side. */
data class TouchEvent(
    val x: Float,
    val y: Float,
    val action: TouchAction,
    val pointerId: Int
)

enum class TouchAction { DOWN, MOVE, UP, CANCEL }

/** Abstract game actions a touch mapping can produce. */
enum class InputAction {
    MOVE_FORWARD, MOVE_BACK, STRAFE_LEFT, STRAFE_RIGHT,
    JUMP, SNEAK, SPRINT, INVENTORY, ESCAPE,
    MOUSE_LEFT, MOUSE_RIGHT, MOUSE_MIDDLE,
    SCROLL_UP, SCROLL_DOWN
}

/** Maps raw pointer events to game actions (touch controls). */
interface TouchMapper {
    fun map(event: TouchEvent): List<InputAction>
}

/** Virtual mouse: absolute position plus button state. */
interface VirtualMouseController {
    var position: Pair<Float, Float>
    var leftPressed: Boolean
    var rightPressed: Boolean
}

/** Key events for hardware/keyboard input. */
interface KeyboardHandler {
    fun onKey(keyCode: Int, scancode: Int, down: Boolean)
}

/** Gamepad axis and button events. */
interface ControllerHandler {
    fun onAxis(axis: Int, value: Float)
    fun onButton(button: Int, down: Boolean)
}

/**
 * Registry for input implementations. Inactive by default — every slot
 * stays null until a later phase supplies a real implementation.
 */
class InputEngine {
    var touchMapper: TouchMapper? = null
    var virtualMouse: VirtualMouseController? = null
    var keyboardHandler: KeyboardHandler? = null
    var controllerHandler: ControllerHandler? = null

    val hasAnyInput: Boolean
        get() = touchMapper != null || virtualMouse != null ||
            keyboardHandler != null || controllerHandler != null
}