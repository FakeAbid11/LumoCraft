package com.lumocraft.app.data.input

import com.lumocraft.app.domain.input.MouseButton
import com.lumocraft.app.domain.input.VirtualMouseManager
import com.lumocraft.app.domain.input.VirtualMouseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reference virtual mouse. Position lives in plain fields (read per
 * frame, never boxed), while buttons/lock/scroll stay in a low-rate
 * [StateFlow]. Callbacks keep the fast path allocation-free.
 */
class DefaultVirtualMouseManager : VirtualMouseManager {

    private val _state = MutableStateFlow(VirtualMouseState())
    override val state: StateFlow<VirtualMouseState> = _state.asStateFlow()

    override var positionX: Float = 0f
        private set
    override var positionY: Float = 0f
        private set

    private var boundsWidth = 0f
    private var boundsHeight = 0f
    private var scrollAccumulator = 0f
    private var uiListener: (() -> Unit)? = null
    private var moveListener: ((dx: Float, dy: Float) -> Unit)? = null

    override fun setUiListener(listener: (() -> Unit)?) {
        uiListener = listener
    }

    override fun setMoveListener(listener: ((dx: Float, dy: Float) -> Unit)?) {
        moveListener = listener
    }

    override fun setBounds(width: Float, height: Float) {
        boundsWidth = width
        boundsHeight = height
        if (positionX == 0f && positionY == 0f && width > 0f && height > 0f) {
            positionX = width / 2f
            positionY = height / 2f
            notifyUi()
        } else {
            positionX = positionX.coerceIn(0f, width)
            positionY = positionY.coerceIn(0f, height)
        }
    }

    override fun moveBy(dx: Float, dy: Float, scale: Float, invertY: Boolean) {
        val scaledY = dy * scale * (if (invertY) -1f else 1f)
        if (_state.value.locked) {
            moveListener?.invoke(dx * scale, scaledY)
            return
        }
        positionX = (positionX + dx * scale).coerceIn(0f, boundsWidth)
        positionY = (positionY + scaledY).coerceIn(0f, boundsHeight)
        notifyUi()
    }

    override fun setPosition(x: Float, y: Float) {
        positionX = x.coerceIn(0f, boundsWidth)
        positionY = y.coerceIn(0f, boundsHeight)
        notifyUi()
    }

    override fun press(button: MouseButton) {
        _state.value = when (button) {
            MouseButton.LEFT -> _state.value.copy(leftPressed = true)
            MouseButton.RIGHT -> _state.value.copy(rightPressed = true)
            MouseButton.MIDDLE -> _state.value.copy(middlePressed = true)
        }
    }

    override fun release(button: MouseButton) {
        _state.value = when (button) {
            MouseButton.LEFT -> _state.value.copy(leftPressed = false)
            MouseButton.RIGHT -> _state.value.copy(rightPressed = false)
            MouseButton.MIDDLE -> _state.value.copy(middlePressed = false)
        }
    }

    override fun setLocked(locked: Boolean) {
        _state.value = _state.value.copy(locked = locked, visible = !locked)
        notifyUi()
    }

    override fun setVisible(visible: Boolean) {
        _state.value = _state.value.copy(visible = visible)
    }

    override fun addScroll(delta: Float) {
        scrollAccumulator += delta
        _state.value = _state.value.copy(scrollAccumulator = scrollAccumulator)
    }

    override fun consumeScroll(): Float {
        val value = scrollAccumulator
        scrollAccumulator = 0f
        _state.value = _state.value.copy(scrollAccumulator = 0f)
        return value
    }

    private fun notifyUi() {
        uiListener?.invoke()
    }
}