package com.lumocraft.app.domain.input

/**
 * Semantic game actions the input framework can produce. Controls bind
 * to these; the virtual mouse and future Minecraft bridge consume them.
 * Later phases (key bindings, Fabric integration) reuse this enum as-is.
 */
enum class InputAction {
    MOVE_FORWARD, MOVE_BACK, STRAFE_LEFT, STRAFE_RIGHT,
    JUMP, SNEAK, SPRINT,
    ATTACK, USE,
    INVENTORY, CHAT, PAUSE,
    MOUSE_LEFT, MOUSE_RIGHT, MOUSE_MIDDLE,
    SCROLL_UP, SCROLL_DOWN;

    /** Actions that stay pressed while the control is held. */
    val isHoldable: Boolean
        get() = this == ATTACK || this == USE ||
            this == JUMP || this == SNEAK || this == SPRINT ||
            this == MOVE_FORWARD || this == MOVE_BACK ||
            this == STRAFE_LEFT || this == STRAFE_RIGHT ||
            this == MOUSE_LEFT || this == MOUSE_RIGHT || this == MOUSE_MIDDLE

    /** The mouse button this action maps to, if any. */
    val mouseButton: MouseButton?
        get() = when (this) {
            ATTACK, MOUSE_LEFT -> MouseButton.LEFT
            USE, MOUSE_RIGHT -> MouseButton.RIGHT
            MOUSE_MIDDLE -> MouseButton.MIDDLE
            else -> null
        }
}
