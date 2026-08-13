package com.lumocraft.app.data.input

import com.lumocraft.app.data.launch.LauncherLogRepository
import com.lumocraft.app.domain.input.ButtonLayout
import com.lumocraft.app.domain.input.ControlKind
import com.lumocraft.app.domain.input.DragState
import com.lumocraft.app.domain.input.Gesture
import com.lumocraft.app.domain.input.InputAction
import com.lumocraft.app.domain.input.InputConfiguration
import com.lumocraft.app.domain.input.InputEvent
import com.lumocraft.app.domain.input.InputManager
import com.lumocraft.app.domain.input.InputProfile
import com.lumocraft.app.domain.input.InputRepository
import com.lumocraft.app.domain.input.InputSettings
import com.lumocraft.app.domain.input.JoystickState
import com.lumocraft.app.domain.input.LayoutGeometry
import com.lumocraft.app.domain.input.MouseButton
import com.lumocraft.app.domain.input.MouseMode
import com.lumocraft.app.domain.input.TouchConfig
import com.lumocraft.app.domain.input.TouchEventMapper
import com.lumocraft.app.domain.input.VirtualMouseManager
import com.lumocraft.app.domain.input.KeyboardManager
import com.lumocraft.app.domain.input.ControllerManager
import com.lumocraft.app.domain.input.defaultButtonLayout
import com.lumocraft.app.domain.input.defaultInputProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Orchestrates the whole input framework: owns the active profile,
 * routes gestures into controls / virtual mouse / actions, exposes
 * clean event streams and logs connection + loading events. All
 * business logic lives here, never in Compose.
 */
class DefaultInputManager(
    private val repository: InputRepository,
    override val touchMapper: TouchEventMapper,
    override val virtualMouse: VirtualMouseManager,
    override val keyboard: KeyboardManager,
    override val controller: ControllerManager,
    private val logs: LauncherLogRepository,
) : InputManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _overlayVisible = MutableStateFlow(true)
    private val _activeActions = MutableStateFlow<Set<InputAction>>(emptySet())
    private val _joystick = MutableStateFlow(JoystickState())
    private val _gestures = MutableSharedFlow<Gesture>(
        extraBufferCapacity = GESTURE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _inputEvents = MutableSharedFlow<InputEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val profiles: StateFlow<List<InputProfile>> = repository.profiles
    override val activeProfileId: StateFlow<String> = repository.activeProfileId
    override val settings: StateFlow<InputSettings> = repository.settings
    override val overlayVisible: StateFlow<Boolean> = _overlayVisible
    override val activeActions: StateFlow<Set<InputAction>> = _activeActions
    override val joystick: StateFlow<JoystickState> = _joystick
    override val gestures: SharedFlow<Gesture> = _gestures.asSharedFlow()
    override val inputEvents: SharedFlow<InputEvent> = _inputEvents.asSharedFlow()

    private val _activeProfile = combine(repository.profiles, repository.activeProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: defaultInputProfile()
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = defaultInputProfile()
    )
    override val activeProfile: StateFlow<InputProfile> = _activeProfile

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var heldControlId: String? = null
    private var joystickControlId: String? = null
    private var mouseDragging = false
    private var multiDragActive = false
    private var controllerConnectedLogged = false
    private var keyboardConnectedLogged = false

    override suspend fun initialize() {
        val result = repository.load()
        result.onSuccess {
            logs.writeSection("Input")
            val profile = _activeProfile.value
            logs.logProfileLoaded(profile.id, profile.name, profile.buttonLayout.buttons.size)
            logs.logLayoutLoaded(profile.id, profile.buttonLayout.buttons.size)
        }
        result.onFailure {
            logs.writeLine("Input: failed to load profiles — ${it.message}")
        }

        virtualMouse.setMoveListener { dx, dy ->
            _inputEvents.tryEmit(InputEvent.MouseMoved(dx, dy))
        }

        scope.launch {
            var last = virtualMouse.state.value
            virtualMouse.state.collect { current ->
                if (current.leftPressed != last.leftPressed) {
                    _inputEvents.tryEmit(InputEvent.MouseButton(MouseButton.LEFT, current.leftPressed))
                }
                if (current.rightPressed != last.rightPressed) {
                    _inputEvents.tryEmit(InputEvent.MouseButton(MouseButton.RIGHT, current.rightPressed))
                }
                if (current.middlePressed != last.middlePressed) {
                    _inputEvents.tryEmit(InputEvent.MouseButton(MouseButton.MIDDLE, current.middlePressed))
                }
                if (current.scrollAccumulator != 0f) {
                    _inputEvents.tryEmit(InputEvent.MouseScrolled(virtualMouse.consumeScroll()))
                }
                last = current
            }
        }

        scope.launch {
            controller.state.collect { state ->
                if (state.connected != controllerConnectedLogged) {
                    controllerConnectedLogged = state.connected
                    if (state.connected) {
                        logs.logControllerDetected(state.deviceName)
                    } else {
                        logs.logControllerDisconnected()
                    }
                }
            }
        }

        scope.launch {
            keyboard.state.collect { state ->
                if (state.connected != keyboardConnectedLogged) {
                    keyboardConnectedLogged = state.connected
                    if (state.connected) {
                        logs.logKeyboardConnected()
                    } else {
                        logs.logKeyboardDisconnected()
                    }
                }
            }
        }

        scope.launch {
            _activeProfile.collect { profile ->
                virtualMouse.setLocked(profile.mouseMode == MouseMode.LOCKED)
                touchMapper.updateConfig(TouchConfig())
                releaseAll()
            }
        }
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        virtualMouse.setBounds(width.toFloat(), height.toFloat())
    }

    override fun selectProfile(profileId: String) {
        scope.launch {
            repository.selectProfile(profileId).onSuccess {
                val profile = _activeProfile.value
                logs.logProfileLoaded(profile.id, profile.name, profile.buttonLayout.buttons.size)
                logs.logLayoutLoaded(profile.id, profile.buttonLayout.buttons.size)
            }
        }
    }

    override fun duplicateProfile(profileId: String): String {
        val source = profiles.value.firstOrNull { it.id == profileId } ?: return profileId
        val newId = "profile_${System.currentTimeMillis()}"
        val copy = source.copy(id = newId, name = "${source.name} (copy)")
        scope.launch {
            repository.saveProfile(copy)
            repository.selectProfile(newId)
        }
        return newId
    }

    override fun deleteProfile(profileId: String) {
        scope.launch { repository.deleteProfile(profileId) }
    }

    override fun saveProfile(profile: InputProfile) {
        scope.launch { repository.saveProfile(profile) }
    }

    override fun updateActiveProfile(transform: (InputProfile) -> InputProfile) {
        val updated = transform(_activeProfile.value)
        scope.launch { repository.saveProfile(updated) }
    }

    override fun setLayout(layout: ButtonLayout) {
        updateActiveProfile { it.copy(buttonLayout = layout) }
    }

    override fun resetLayout() {
        setLayout(defaultButtonLayout())
    }

    override fun setOverlayVisible(visible: Boolean) {
        _overlayVisible.value = visible
        if (!visible) releaseAll()
    }

    override fun setSettings(transform: (InputSettings) -> InputSettings) {
        val next = transform(settings.value)
        scope.launch { repository.saveSettings(next) }
    }

    override fun handleGesture(gesture: Gesture) {
        _gestures.tryEmit(gesture)
        when (gesture) {
            is Gesture.Drag -> handleDrag(gesture)
            is Gesture.Tap -> handleTap(gesture.x, gesture.y, rightClick = false)
            is Gesture.MultiTap -> handleTap(gesture.x, gesture.y, rightClick = true)
            is Gesture.MultiDrag -> handleMultiDrag(gesture)
            is Gesture.DoubleTap,
            is Gesture.LongPress,
            is Gesture.Swipe -> Unit // reserved: macros, camera reset, custom bindings
        }
    }

    override fun configuration(): InputConfiguration {
        val profile = _activeProfile.value
        val s = settings.value
        return InputConfiguration(
            profileId = profile.id,
            profileName = profile.name,
            sensitivity = profile.sensitivity,
            invertY = profile.invertY,
            mouseMode = profile.mouseMode,
            cursorSpeed = s.cursorSpeed,
            buttonOpacity = s.buttonOpacity,
            controllerEnabled = profile.controllerEnabled,
            keyboardEnabled = profile.keyboardEnabled,
            controlCount = profile.buttonLayout.buttons.size
        )
    }

    private fun handleDrag(gesture: Gesture.Drag) {
        val profile = _activeProfile.value
        when (gesture.state) {
            DragState.STARTED -> {
                val control = hitTest(gesture.x, gesture.y)
                when {
                    control != null && control.kind == ControlKind.JOYSTICK -> {
                        joystickControlId = control.id
                        updateJoystick(gesture)
                    }
                    control != null && control.action.isHoldable -> {
                        heldControlId = control.id
                        setAction(control.action, true)
                    }
                    else -> {
                        if (profile.mouseMode != MouseMode.TOUCH) {
                            mouseDragging = true
                            moveMouse(gesture.dx, gesture.dy, profile)
                        } else {
                            virtualMouse.setPosition(gesture.x, gesture.y)
                        }
                    }
                }
            }
            DragState.MOVED -> {
                if (joystickControlId != null) {
                    updateJoystick(gesture)
                } else if (mouseDragging) {
                    moveMouse(gesture.dx, gesture.dy, profile)
                }
            }
            DragState.ENDED, DragState.CANCELLED -> {
                joystickControlId?.let {
                    _joystick.value = JoystickState()
                    joystickControlId = null
                }
                heldControlId?.let { id ->
                    currentLayout().find(id)?.let { setAction(it.action, false) }
                    heldControlId = null
                }
                mouseDragging = false
                multiDragActive = false
            }
        }
    }

    private fun handleTap(x: Float, y: Float, rightClick: Boolean) {
        val profile = _activeProfile.value
        val control = hitTest(x, y)
        when {
            control == null -> {
                if (profile.mouseMode == MouseMode.TOUCH) {
                    virtualMouse.setPosition(x, y)
                }
                val button = if (rightClick) MouseButton.RIGHT else MouseButton.LEFT
                virtualMouse.press(button)
                virtualMouse.release(button)
            }
            control.kind == ControlKind.JOYSTICK -> Unit
            else -> {
                setAction(control.action, true)
                setAction(control.action, false)
            }
        }
    }

    private fun handleMultiDrag(gesture: Gesture.MultiDrag) {
        when (gesture.state) {
            DragState.STARTED -> {
                multiDragActive = true
                mouseDragging = false
            }
            DragState.MOVED -> {
                if (multiDragActive) {
                    virtualMouse.addScroll(-gesture.dy * SCROLL_FACTOR)
                }
            }
            DragState.ENDED, DragState.CANCELLED -> multiDragActive = false
        }
    }

    private fun updateJoystick(gesture: Gesture.Drag) {
        val id = joystickControlId ?: return
        val control = currentLayout().find(id) ?: return
        val (vx, vy) = LayoutGeometry.joystickVector(
            control,
            surfaceWidth.toFloat(),
            surfaceHeight.toFloat(),
            gesture.x,
            gesture.y
        )
        _joystick.value = JoystickState(control.id, vx, vy)
        setAction(InputAction.MOVE_FORWARD, vy < -JOYSTICK_THRESHOLD)
        setAction(InputAction.MOVE_BACK, vy > JOYSTICK_THRESHOLD)
        setAction(InputAction.STRAFE_LEFT, vx < -JOYSTICK_THRESHOLD)
        setAction(InputAction.STRAFE_RIGHT, vx > JOYSTICK_THRESHOLD)
    }

    private fun moveMouse(dx: Float, dy: Float, profile: InputProfile) {
        val scale = profile.sensitivity * settings.value.cursorSpeed * CURSOR_GAIN
        virtualMouse.moveBy(dx, dy, scale, profile.invertY)
    }

    private fun setAction(action: InputAction, pressed: Boolean) {
        val current = _activeActions.value
        if (pressed == (action in current)) return
        _activeActions.value = if (pressed) current + action else current - action
        _inputEvents.tryEmit(InputEvent.ActionTriggered(action, pressed))
    }

    private fun releaseAll() {
        _activeActions.value.toList().forEach { setAction(it, false) }
        _joystick.value = JoystickState()
        heldControlId = null
        joystickControlId = null
        mouseDragging = false
        multiDragActive = false
    }

    private fun currentLayout() = _activeProfile.value.buttonLayout

    private fun hitTest(x: Float, y: Float) =
        LayoutGeometry.hitTest(currentLayout(), x, y, surfaceWidth.toFloat(), surfaceHeight.toFloat())

    private companion object {
        const val GESTURE_BUFFER = 32
        const val EVENT_BUFFER = 64
        const val JOYSTICK_THRESHOLD = 0.3f
        const val SCROLL_FACTOR = 0.02f
        const val CURSOR_GAIN = 1f
    }
}