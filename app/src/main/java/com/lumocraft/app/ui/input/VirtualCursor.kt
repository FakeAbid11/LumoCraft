package com.lumocraft.app.ui.input

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.lumocraft.app.domain.input.VirtualMouseManager

/**
 * Cursor overlay. The position is written from the manager's fast-path
 * listener into a [androidx.compose.runtime.MutableState] read inside
 * the draw scope, so movement only invalidates the draw phase — no
 * recomposition, no frame loop, smooth on low-end devices.
 */
@Composable
fun VirtualCursor(
    manager: VirtualMouseManager,
    modifier: Modifier = Modifier,
) {
    val mouseState by manager.state.collectAsState()
    val position = remember { mutableStateOf(Offset(manager.positionX, manager.positionY)) }

    LaunchedEffect(manager) {
        manager.setUiListener {
            position.value = Offset(manager.positionX, manager.positionY)
        }
    }
    DisposableEffect(manager) {
        onDispose { manager.setUiListener(null) }
    }

    Canvas(modifier = modifier) {
        val center = position.value
        val outer = size.minDimension * 0.012f
        val inner = outer * 0.55f
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = outer,
            center = center
        )
        drawCircle(
            color = Color(0xFF1A1A1E).copy(alpha = 0.9f),
            radius = inner,
            center = center
        )
        if (mouseState.leftPressed || mouseState.rightPressed || mouseState.middlePressed) {
            drawCircle(
                color = Color(0xFF64D2FF).copy(alpha = 0.9f),
                radius = inner * 0.7f,
                center = center
            )
        }
    }
}