package com.lumocraft.app.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.lumocraft.app.domain.input.ButtonLayout
import com.lumocraft.app.domain.input.ControlButton
import com.lumocraft.app.domain.input.ControlKind
import com.lumocraft.app.domain.input.InputAction
import com.lumocraft.app.domain.input.JoystickState
import com.lumocraft.app.domain.input.LayoutGeometry

private val CONTROL_FILL = Color(0x3DFFFFFF)
private val CONTROL_FILL_PRESSED = Color(0x8A64D2FF)
private val CONTROL_BORDER = Color(0x59FFFFFF)
private val CONTROL_BORDER_ACTIVE = Color(0xCC64D2FF)
private val LABEL_COLOR = Color(0xE6FFFFFF)

/**
 * Draws every control of a layout into the overlay canvas. One canvas,
 * one pass — pressed states and the joystick knob are read from plain
 * state values, so pointer updates never trigger recomposition.
 */
fun DrawScope.drawControls(
    textMeasurer: TextMeasurer,
    layout: ButtonLayout,
    actions: Set<InputAction>,
    joystick: JoystickState,
    surface: IntSize,
    alpha: Float,
) {
    for (button in layout.buttons) {
        drawControl(textMeasurer, button, button.action in actions, surface, alpha)
    }
    if (joystick.active) {
        val control = layout.find(joystick.controlId ?: return)
        if (control != null) {
            drawJoystickKnob(control, joystick.x, joystick.y, surface, alpha)
        }
    }
}

private fun DrawScope.drawControl(
    textMeasurer: TextMeasurer,
    button: ControlButton,
    pressed: Boolean,
    surface: IntSize,
    alpha: Float,
) {
    val bounds = LayoutGeometry.toPixels(button, surface.width.toFloat(), surface.height.toFloat())
    val left = bounds.left
    val top = bounds.top
    val width = bounds.width
    val height = bounds.height
    val opacity = button.opacity * alpha
    if (opacity <= 0.01f) return

    val fill = if (pressed) CONTROL_FILL_PRESSED else CONTROL_FILL
    val border = if (pressed) CONTROL_BORDER_ACTIVE else CONTROL_BORDER
    val corner = CornerRadius(width * 0.28f, height * 0.28f)
    val rectSize = Size(width, height)

    when (button.kind) {
        ControlKind.BUTTON -> {
            drawRoundRect(color = fill.copy(alpha = opacity), topLeft = Offset(left, top), size = rectSize, cornerRadius = corner)
            drawRoundRect(color = border.copy(alpha = opacity), topLeft = Offset(left, top), size = rectSize, cornerRadius = corner, style = Stroke(width = strokeWidth))
            if (pressed) {
                drawRoundRect(color = Color.White.copy(alpha = 0.18f), topLeft = Offset(left, top), size = rectSize, cornerRadius = corner)
            }
        }
        ControlKind.JOYSTICK -> {
            val radius = minOf(width, height) / 2f
            val center = Offset(left + width / 2f, top + height / 2f)
            drawCircle(color = fill.copy(alpha = opacity), radius = radius, center = center)
            drawCircle(color = border.copy(alpha = opacity), radius = radius, center = center, style = Stroke(width = strokeWidth))
            drawCircle(color = Color.White.copy(alpha = 0.14f), radius = radius * 0.42f, center = center)
            val cross = radius * 0.75f
            val crossColor = Color.White.copy(alpha = 0.28f)
            drawLine(crossColor, Offset(center.x - cross, center.y), Offset(center.x + cross, center.y), strokeWidth)
            drawLine(crossColor, Offset(center.x, center.y - cross), Offset(center.x, center.y + cross), strokeWidth)
        }
    }

    drawCenteredLabel(textMeasurer, button.label, Offset(left + width / 2f, top + height / 2f), opacity, width)
}

private fun DrawScope.drawCenteredLabel(
    textMeasurer: TextMeasurer,
    label: String,
    center: Offset,
    opacity: Float,
    buttonWidth: Float,
) {
    val style = TextStyle(
        color = LABEL_COLOR.copy(alpha = opacity),
        fontSize = (buttonWidth / 4.6f).coerceIn(9f, 18f).sp
    )
    val measured = textMeasurer.measure(label, style)
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        style = style,
        topLeft = Offset(
            center.x - measured.size.width / 2f,
            center.y - measured.size.height / 2f
        )
    )
}

private fun DrawScope.drawJoystickKnob(
    control: ControlButton,
    vx: Float,
    vy: Float,
    surface: IntSize,
    alpha: Float,
) {
    val bounds = LayoutGeometry.toPixels(control, surface.width.toFloat(), surface.height.toFloat())
    val center = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
    val radius = minOf(bounds.width, bounds.height) / 2f
    val knobRadius = radius * 0.34f
    val travel = radius * 0.55f
    val opacity = (control.opacity * alpha).coerceAtLeast(0.3f)
    drawCircle(
        color = CONTROL_FILL_PRESSED.copy(alpha = opacity),
        radius = knobRadius,
        center = Offset(center.x + vx * travel, center.y + vy * travel)
    )
}

private val DrawScope.strokeWidth: Float
    get() = (size.minDimension * 0.0028f).coerceIn(1f, 3f)

@Composable
fun rememberControlTextMeasurer(): TextMeasurer = rememberTextMeasurer()