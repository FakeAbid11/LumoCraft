package com.lumocraft.app.domain.input

/** What kind of surface a control occupies. */
enum class ControlKind {
    /** Single press/hold button. */
    BUTTON,

    /** Directional pad driven by drags (movement). */
    JOYSTICK
}

/**
 * One control in a [ButtonLayout]. All geometry is normalized to the
 * overlay surface (0..1), so layouts are resolution independent.
 */
data class ControlButton(
    val id: String,
    val action: InputAction,
    val label: String,
    /** Normalized center X (0..1). */
    val x: Float,
    /** Normalized center Y (0..1). */
    val y: Float,
    /** Normalized width (0..1). */
    val width: Float,
    /** Normalized height (0..1). */
    val height: Float,
    val opacity: Float = DEFAULT_BUTTON_OPACITY,
    val kind: ControlKind = ControlKind.BUTTON
) {
    companion object {
        const val DEFAULT_BUTTON_OPACITY = 0.55f
    }
}