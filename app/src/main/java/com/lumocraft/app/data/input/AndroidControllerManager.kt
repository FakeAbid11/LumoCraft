package com.lumocraft.app.data.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.lumocraft.app.domain.input.ControllerAxisKind
import com.lumocraft.app.domain.input.ControllerButton
import com.lumocraft.app.domain.input.ControllerEvent
import com.lumocraft.app.domain.input.ControllerManager
import com.lumocraft.app.domain.input.ControllerState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * Android game controller support. Registers for device connect/
 * disconnect, maps raw MotionEvent axes and KeyEvent buttons into
 * clean [ControllerAxisKind]/[ControllerButton] state and events.
 */
class AndroidControllerManager(context: Context) : ControllerManager {

    private val _state = MutableStateFlow(ControllerState())
    override val state: StateFlow<ControllerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ControllerEvent>(
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<ControllerEvent> = _events.asSharedFlow()

    private val androidInputManager =
        context.getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager

    private val deviceListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    private var axes = Axes()
    private var buttons = HashSet<ControllerButton>()
    private var lastDeviceName: String? = null

    override fun register() {
        androidInputManager.registerInputDeviceListener(deviceListener, null)
        refresh()
    }

    override fun unregister() {
        androidInputManager.unregisterInputDeviceListener(deviceListener)
    }

    override fun onAxis(axis: ControllerAxisKind, value: Float) {
        axes = axes.with(axis, value)
        _events.tryEmit(ControllerEvent.Axis(axis, value))
        emitState()
    }

    override fun onButton(button: ControllerButton, pressed: Boolean) {
        if (pressed) buttons.add(button) else buttons.remove(button)
        _events.tryEmit(ControllerEvent.Button(button, pressed))
        emitState()
    }

    /** Feeds one Android motion event; returns true when consumed. */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!_state.value.connected) return false
        val source = event.source
        val isGamepadSource = source and InputDevice.SOURCE_JOYSTICK != 0 ||
            source and InputDevice.SOURCE_DPAD != 0
        if (!isGamepadSource) return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false

        val leftX = event.getAxisValue(MotionEvent.AXIS_X)
        val leftY = event.getAxisValue(MotionEvent.AXIS_Y)
        val rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        val rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
        val leftTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rightTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_THROTTLE))
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        axes = axes.copy(
            leftX = applyDeadZone(leftX),
            leftY = applyDeadZone(leftY),
            rightX = applyDeadZone(rightX),
            rightY = applyDeadZone(rightY),
            leftTrigger = applyDeadZone(leftTrigger),
            rightTrigger = applyDeadZone(rightTrigger)
        )
        setDpad(hatX, hatY)
        emitState()
        return true
    }

    /** Feeds one Android key event (gamepad buttons); true when consumed. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!_state.value.connected) return false
        val button = mapButton(event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) return true
        onButton(button, event.action == KeyEvent.ACTION_DOWN)
        return true
    }

    private fun refresh() {
        var device: InputDevice? = null
        for (id in androidInputManager.inputDeviceIds) {
            val candidate = androidInputManager.getInputDevice(id) ?: continue
            val sources = candidate.sources
            if (sources and InputDevice.SOURCE_GAMEPAD != 0 ||
                sources and InputDevice.SOURCE_JOYSTICK != 0
            ) {
                device = candidate
                break
            }
        }
        val connected = device != null
        if (connected) {
            lastDeviceName = device?.name
        }
        _state.value = _state.value.copy(connected = connected, deviceName = lastDeviceName)
        if (!connected) {
            axes = Axes()
            buttons.clear()
        }
    }

    private fun setDpad(hatX: Float, hatY: Float) {
        setButtonState(ControllerButton.DPAD_LEFT, hatX < -DPAD_THRESHOLD)
        setButtonState(ControllerButton.DPAD_RIGHT, hatX > DPAD_THRESHOLD)
        setButtonState(ControllerButton.DPAD_UP, hatY < -DPAD_THRESHOLD)
        setButtonState(ControllerButton.DPAD_DOWN, hatY > DPAD_THRESHOLD)
    }

    private fun setButtonState(button: ControllerButton, pressed: Boolean) {
        val contains = button in buttons
        if (pressed && !contains) buttons.add(button)
        if (!pressed && contains) buttons.remove(button)
    }

    private fun emitState() {
        _state.value = _state.value.copy(
            leftX = axes.leftX,
            leftY = axes.leftY,
            rightX = axes.rightX,
            rightY = axes.rightY,
            leftTrigger = axes.leftTrigger,
            rightTrigger = axes.rightTrigger,
            buttons = buttons.toSet()
        )
    }

    private fun applyDeadZone(value: Float): Float =
        if (abs(value) < DEAD_ZONE) 0f else value

    private fun mapButton(keyCode: Int): ControllerButton? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerButton.A
        KeyEvent.KEYCODE_BUTTON_B -> ControllerButton.B
        KeyEvent.KEYCODE_BUTTON_X -> ControllerButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerButton.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerButton.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerButton.RB
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerButton.BACK
        KeyEvent.KEYCODE_BUTTON_START -> ControllerButton.START
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerButton.GUIDE
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerButton.LEFT_STICK
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerButton.RIGHT_STICK
        KeyEvent.KEYCODE_DPAD_UP -> ControllerButton.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerButton.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerButton.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerButton.DPAD_RIGHT
        else -> null
    }

    private data class Axes(
        val leftX: Float = 0f,
        val leftY: Float = 0f,
        val rightX: Float = 0f,
        val rightY: Float = 0f,
        val leftTrigger: Float = 0f,
        val rightTrigger: Float = 0f
    ) {
        fun with(axis: ControllerAxisKind, value: Float): Axes = when (axis) {
            ControllerAxisKind.LEFT_X -> copy(leftX = value)
            ControllerAxisKind.LEFT_Y -> copy(leftY = value)
            ControllerAxisKind.RIGHT_X -> copy(rightX = value)
            ControllerAxisKind.RIGHT_Y -> copy(rightY = value)
            ControllerAxisKind.LEFT_TRIGGER -> copy(leftTrigger = value)
            ControllerAxisKind.RIGHT_TRIGGER -> copy(rightTrigger = value)
        }
    }

    private companion object {
        const val DEAD_ZONE = 0.12f
        const val DPAD_THRESHOLD = 0.5f
        const val EXTRA_BUFFER = 32
    }
}