package com.lumocraft.app.ui.input

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.R
import com.lumocraft.app.data.input.AndroidControllerManager
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

    DisposableEffect(Unit) {
        manager.controller.register()
        manager.setOverlayVisible(true)
        onDispose {
            manager.controller.unregister()
            manager.setOverlayVisible(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14161A))
    ) {
        GameSurfaceBackdrop(modifier = Modifier.fillMaxSize())
        InputOverlay(
            manager = manager,
            modifier = Modifier.fillMaxSize()
        )
        AndroidView(
            factory = { context -> GamepadInputView(context, manager) },
            modifier = Modifier.matchParentSize()
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

/**
 * Transparent interop view that captures what the Compose pointer
 * pipeline cannot: gamepad keys (focused view receives key events)
 * and generic motion events (joystick axes). Touches return false and
 * fall through to the overlay underneath.
 */
private class GamepadInputView(
    context: Context,
    private val manager: InputManager,
) : View(context) {

    private val controller = manager.controller as? AndroidControllerManager

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        controller?.handleMotionEvent(event)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (controller?.handleKeyEvent(event) == true) return true
        manager.keyboard.onKeyEvent(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            down = event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_REPEAT,
            repeat = event.action == KeyEvent.ACTION_REPEAT
        )
        return true
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