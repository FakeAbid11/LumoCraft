package com.lumocraft.app.ui.input

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.changedToCancel
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.lumocraft.app.domain.input.ButtonLayout
import com.lumocraft.app.domain.input.InputManager
import com.lumocraft.app.domain.input.InputSettings
import com.lumocraft.app.domain.input.RawTouch
import com.lumocraft.app.domain.input.TouchActionKind
import kotlinx.coroutines.delay

/**
 * The input overlay: renders controls and the virtual cursor above the
 * game surface and feeds raw touches into the touch pipeline when
 * interactive. Geometry comes from the active profile layout (or a
 * [layoutOverride] while the editor previews a draft). Drawing is
 * canvas-based; pointer updates never recompose the layout.
 */
@Composable
fun InputOverlay(
    manager: InputManager,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    layoutOverride: ButtonLayout? = null,
    showCursor: Boolean = true,
) {
    val profile by manager.activeProfile.collectAsState()
    val settings by manager.settings.collectAsState()
    val actions by manager.activeActions.collectAsState()
    val joystick by manager.joystick.collectAsState()
    val overlayVisible by manager.overlayVisible.collectAsState()
    val mouseState by manager.virtualMouse.state.collectAsState()

    val layout = layoutOverride ?: profile.buttonLayout
    val textMeasurer = rememberControlTextMeasurer()

    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val lastActivity = remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val controlAlpha = rememberControlFade(settings, interactive) { lastActivity.longValue }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                surfaceSize = size
                if (interactive) manager.setSurfaceSize(size.width, size.height)
            }
            .then(
                if (interactive) {
                    Modifier.pointerInput(manager) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                lastActivity.longValue = SystemClock.uptimeMillis()
                                feed(event, manager)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        if (overlayVisible && surfaceSize != IntSize.Zero) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawControls(
                    textMeasurer = textMeasurer,
                    layout = layout,
                    actions = actions,
                    joystick = joystick,
                    surface = surfaceSize,
                    alpha = controlAlpha.value
                )
            }
        }
        if (showCursor && mouseState.visible && surfaceSize != IntSize.Zero) {
            VirtualCursor(manager.virtualMouse, Modifier.matchParentSize())
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.feed(
    event: PointerEvent,
    manager: InputManager,
) {
    val now = SystemClock.uptimeMillis()
    for (change in event.changes) {
        val touch = when {
            change.changedToDown() -> RawTouch(change.position.x, change.position.y, TouchActionKind.DOWN, change.id.value, now)
            change.changedToUp() -> RawTouch(change.position.x, change.position.y, TouchActionKind.UP, change.id.value, now)
            change.changedToCancel() -> RawTouch(change.position.x, change.position.y, TouchActionKind.CANCEL, change.id.value, now)
            change.pressed -> RawTouch(change.position.x, change.position.y, TouchActionKind.MOVE, change.id.value, now)
            else -> null
        }
        if (touch != null) {
            for (gesture in manager.touchMapper.feed(touch)) {
                manager.handleGesture(gesture)
            }
        }
    }
    event.changes.forEach { it.consume() }
}

/**
 * Fades the controls after a period of inactivity. Uses a single
 * [Animatable]; the fade target is re-evaluated on a slow poll so the
 * loop stays idle while the user is active.
 */
@Composable
private fun rememberControlFade(
    settings: InputSettings,
    interactive: Boolean,
    lastActivity: () -> Long,
): State<Float> {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(settings.fadeIdleControls, interactive) {
        while (true) {
            val idle = SystemClock.uptimeMillis() - lastActivity()
            val faded = interactive && settings.fadeIdleControls && idle > FADE_IDLE_MS
            alpha.animateTo(
                targetValue = if (faded) FADE_MIN_ALPHA else 1f,
                animationSpec = tween(FADE_DURATION_MS)
            )
            delay(FADE_POLL_MS)
        }
    }
    return alpha
}

/** The neutral backdrop a preview surface draws behind the overlay. */
@Composable
fun GameSurfaceBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF23262D),
                1f to Color(0xFF14161A)
            )
        )
        val gap = size.minDimension * 0.09f
        val lineColor = Color.White.copy(alpha = 0.04f)
        var y = 0f
        while (y < size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
            y += gap
        }
        var x = 0f
        while (x < size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f)
            x += gap
        }
    }
}

private const val FADE_IDLE_MS = 2_500L
private const val FADE_DURATION_MS = 300
private const val FADE_POLL_MS = 250L
private const val FADE_MIN_ALPHA = 0.35f