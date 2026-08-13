package com.lumocraft.app.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.pointerInteropFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.domain.input.InputManager

/**
 * Full-screen live preview of the input system: the overlay, the touch
 * pipeline, the virtual mouse, hardware keyboard and game controller
 * all work here exactly as they will above a real game surface.
 */
@Composable
fun ControlsPreviewScreen(
    onEditLayout: () -> Unit,
    onExit: () -> Unit,
    viewModel: ControlsPreviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ControlsPreviewViewModel.Factory
    ),
) {
    val manager = viewModel.manager
    val overlayVisible by manager.overlayVisible.collectAsState()
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        manager.controller.register()
        manager.setOverlayVisible(true)
        onDispose {
            manager.controller.unregister()
            manager.setOverlayVisible(false)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14161A))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                val handled = native != null && manager.controller.handleKeyEvent(native)
                if (!handled) {
                    manager.keyboard.setConnected(true)
                    manager.keyboard.onKeyEvent(
                        keyCode = event.nativeKeyCode,
                        scanCode = native?.scanCode ?: 0,
                        down = event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyRepeat,
                        repeat = event.type == KeyEventType.KeyRepeat
                    )
                }
                true
            }
            .pointerInteropFilter { event ->
                manager.controller.handleMotionEvent(event)
            }
    ) {
        GameSurfaceBackdrop(modifier = Modifier.fillMaxSize())
        InputOverlay(
            manager = manager,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onExit) {
                Text(stringResource(R.string.input_preview_exit))
            }
            FilledTonalButton(onClick = onEditLayout) {
                Text(stringResource(R.string.input_preview_edit_layout))
            }
            FilledTonalButton(onClick = { manager.setOverlayVisible(!overlayVisible) }) {
                Text(
                    stringResource(
                        if (overlayVisible) R.string.input_preview_pause
                        else R.string.input_preview_resume
                    )
                )
            }
        }
    }
}

/** Thin holder that resolves the shared [InputManager]. */
class ControlsPreviewViewModel(
    val manager: InputManager,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as LumoCraftApplication
                ControlsPreviewViewModel(application.inputManager)
            }
        }
    }
}