package com.lumocraft.app.domain.input

/**
 * A saved input configuration. Multiple profiles are persisted locally;
 * the active one drives the overlay, the virtual mouse and — through
 * [InputConfiguration] — future game sessions.
 */
data class InputProfile(
    val id: String,
    val name: String,
    /** Mouse look / cursor scaling factor. */
    val sensitivity: Float = 1f,
    val invertY: Boolean = false,
    val mouseMode: MouseMode = MouseMode.RELATIVE,
    val buttonLayout: ButtonLayout = defaultButtonLayout(),
    val controllerEnabled: Boolean = true,
    val keyboardEnabled: Boolean = true
)