package com.lumocraft.app.domain.input

import kotlin.math.roundToInt

/**
 * Pure geometry helpers shared by the overlay, the layout editor and
 * the input manager. Everything stays normalized (0..1) except the
 * final pixel conversions.
 */
object LayoutGeometry {

    /** Pixel bounds of a control on a [width]x[height] surface. */
    fun toPixels(button: ControlButton, width: Float, height: Float): ButtonBounds {
        val w = button.width * width
        val h = button.height * height
        return ButtonBounds(
            left = button.x * width - w / 2f,
            top = button.y * height - h / 2f,
            width = w,
            height = h
        )
    }

    /** First control containing the point, or null for free area. */
    fun hitTest(
        layout: ButtonLayout,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): ControlButton? {
        for (button in layout.buttons) {
            val b = toPixels(button, width, height)
            if (x >= b.left && x <= b.left + b.width &&
                y >= b.top && y <= b.top + b.height
            ) {
                return button
            }
        }
        return null
    }

    /**
     * Joystick vector from the pad center to the pointer, normalized to
     * [-1,1] and clamped to the pad radius.
     */
    fun joystickVector(
        button: ControlButton,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerX: Float,
        pointerY: Float
    ): Pair<Float, Float> {
        val bounds = toPixels(button, surfaceWidth, surfaceHeight)
        val centerX = bounds.left + bounds.width / 2f
        val centerY = bounds.top + bounds.height / 2f
        val radius = maxOf(bounds.width, bounds.height) / 2f
        if (radius <= 0f) return 0f to 0f
        return ((pointerX - centerX) / radius).coerceIn(-1f, 1f) to
            ((pointerY - centerY) / radius).coerceIn(-1f, 1f)
    }

    fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)

    /**
     * Snaps a normalized position to the editor grid when close enough,
     * otherwise leaves it free — "snaps reasonably, stays movable".
     */
    fun snapOrFree(value: Float, grid: Float = EDITOR_GRID, radius: Float = EDITOR_SNAP_RADIUS): Float {
        val snapped = (value / grid).roundToInt() * grid
        return if (kotlin.math.abs(value - snapped) <= radius) snapped else value
    }

    fun grid(value: Float, grid: Float = EDITOR_GRID): Float = (value / grid).roundToInt() * grid

    const val EDITOR_GRID = 1f / 24f
    const val EDITOR_SNAP_RADIUS = 1f / 160f
}

/** Pixel-space rectangle of a control. */
data class ButtonBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)