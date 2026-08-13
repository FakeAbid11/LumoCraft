package com.lumocraft.app.domain.input

/**
 * Launcher-wide input settings, independent of any single profile.
 * [buttonOpacity] is the default applied to every control; profile
 * toggles live on [InputProfile].
 */
data class InputSettings(
    val cursorSpeed: Float = 1f,
    val buttonOpacity: Float = ControlButton.DEFAULT_BUTTON_OPACITY,
    val fadeIdleControls: Boolean = true
)