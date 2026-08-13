package com.lumocraft.app.data.input

import android.util.SparseArray
import com.lumocraft.app.domain.input.KeyModifiers
import com.lumocraft.app.domain.input.KeyStroke
import com.lumocraft.app.domain.input.KeyboardManager
import com.lumocraft.app.domain.input.KeyboardState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware and soft keyboard support: pressed-key tracking, key repeat
 * and modifier state. Android key codes are used as-is; repeat comes
 * from the platform (Compose/View auto-repeat) via [repeat].
 */
class AndroidKeyboardManager : KeyboardManager {

    private val _state = MutableStateFlow(KeyboardState())
    override val state: StateFlow<KeyboardState> = _state.asStateFlow()

    private val _keyEvents = MutableSharedFlow<KeyStroke>(
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val keyEvents: SharedFlow<KeyStroke> = _keyEvents.asSharedFlow()

    private val pressedKeys = SparseArray<Boolean>()
    private var modifiers = KeyModifiers()

    override fun onKeyEvent(keyCode: Int, scanCode: Int, down: Boolean, repeat: Boolean): Boolean {
        if (down) {
            val alreadyPressed = pressedKeys.get(keyCode, false)
            pressedKeys.put(keyCode, true)
            if (!alreadyPressed || repeat) {
                _keyEvents.tryEmit(
                    KeyStroke(keyCode, scanCode, down = true, repeat = repeat, modifiers = modifiers)
                )
            }
        } else {
            val wasPressed = pressedKeys.get(keyCode, false)
            pressedKeys.delete(keyCode)
            if (wasPressed) {
                _keyEvents.tryEmit(
                    KeyStroke(keyCode, scanCode, down = false, repeat = false, modifiers = modifiers)
                )
            }
        }
        updateModifier(keyCode, down)
        emitState()
        return true
    }

    override fun isPressed(keyCode: Int): Boolean = pressedKeys.get(keyCode, false)

    override fun setConnected(connected: Boolean) {
        _state.value = _state.value.copy(connected = connected)
    }

    private fun updateModifier(keyCode: Int, down: Boolean) {
        modifiers = when (keyCode) {
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT,
            android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> modifiers.copy(shift = down)
            android.view.KeyEvent.KEYCODE_CTRL_LEFT,
            android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> modifiers.copy(ctrl = down)
            android.view.KeyEvent.KEYCODE_ALT_LEFT,
            android.view.KeyEvent.KEYCODE_ALT_RIGHT -> modifiers.copy(alt = down)
            android.view.KeyEvent.KEYCODE_META_LEFT,
            android.view.KeyEvent.KEYCODE_META_RIGHT -> modifiers.copy(meta = down)
            else -> modifiers
        }
    }

    private fun emitState() {
        _state.value = KeyboardState(
            connected = _state.value.connected,
            pressedKeys = pressedKeys.toKeySet(),
            modifiers = modifiers
        )
    }

    private fun SparseArray<Boolean>.toKeySet(): Set<Int> {
        val keys = HashSet<Int>(size())
        for (i in 0 until size()) keys.add(keyAt(i))
        return keys
    }

    private companion object {
        const val EXTRA_BUFFER = 32
    }
}