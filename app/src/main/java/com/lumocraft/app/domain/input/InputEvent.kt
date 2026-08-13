package com.lumocraft.app.domain.input

/**
 * Clean events the input system exposes to consumers (the future
 * Minecraft bridge). Mouse events carry pure deltas/buttons; touch
 * actions are already mapped to [InputAction]s.
 */
sealed interface InputEvent {

    /** Cursor moved by a relative delta (pixels). */
    data class MouseMoved(val dx: Float, val dy: Float) : InputEvent

    /** Cursor button state change. */
    data class MouseButton(
        val button: com.lumocraft.app.domain.input.MouseButton,
        val pressed: Boolean
    ) : InputEvent

    /** Scrolled by an accumulated wheel delta. */
    data class MouseScrolled(val delta: Float) : InputEvent

    /** A touch control was pressed or released. */
    data class ActionTriggered(val action: InputAction, val pressed: Boolean) : InputEvent
}