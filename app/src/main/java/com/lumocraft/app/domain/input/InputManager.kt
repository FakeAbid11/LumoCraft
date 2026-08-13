package com.lumocraft.app.domain.input

/** Current joystick deflection, for the pad visual. */
data class JoystickState(
    val controlId: String? = null,
    /** -1..1 */
    val x: Float = 0f,
    /** -1..1 */
    val y: Float = 0f
) {
    val active: Boolean get() = controlId != null
}

/**
 * Immutable snapshot of the active input setup, handed to a game
 * session so it can honor sensitivity, mouse mode and toggles.
 */
data class InputConfiguration(
    val profileId: String,
    val profileName: String,
    val sensitivity: Float,
    val invertY: Boolean,
    val mouseMode: MouseMode,
    val cursorSpeed: Float,
    val buttonOpacity: Float,
    val controllerEnabled: Boolean,
    val keyboardEnabled: Boolean,
    val controlCount: Int
)

/**
 * Facade over every input subsystem: profiles, the touch pipeline,
 * the virtual mouse, keyboard, controller and the overlay state.
 * This is the single seam later phases (key bindings, Fabric
 * integration, accessibility) extend without breaking callers.
 */
interface InputManager {

    val profiles: StateFlow<List<InputProfile>>
    val activeProfileId: StateFlow<String>
    val activeProfile: StateFlow<InputProfile>
    val settings: StateFlow<InputSettings>

    /** Overlay visibility (paused or hidden). */
    val overlayVisible: StateFlow<Boolean>

    /** Actions currently held by touch controls. */
    val activeActions: StateFlow<Set<InputAction>>

    /** Joystick deflection, for the pad visual. */
    val joystick: StateFlow<JoystickState>

    /** Recognized gestures for future consumers (macros, bindings). */
    val gestures: SharedFlow<Gesture>

    /** Clean mouse/touch events for the future Minecraft bridge. */
    val inputEvents: SharedFlow<InputEvent>

    val touchMapper: TouchEventMapper
    val virtualMouse: VirtualMouseManager
    val keyboard: KeyboardManager
    val controller: ControllerManager

    /** Loads profiles/settings and starts observers. Called once. */
    suspend fun initialize()

    /** Size of the overlay surface, in pixels. */
    fun setSurfaceSize(width: Int, height: Int)

    fun selectProfile(profileId: String)

    /** Copies a profile and selects the copy; returns the new id. */
    fun duplicateProfile(profileId: String): String

    fun deleteProfile(profileId: String)

    fun saveProfile(profile: InputProfile)

    fun updateActiveProfile(transform: (InputProfile) -> InputProfile)

    fun setLayout(layout: ButtonLayout)

    fun resetLayout()

    fun setOverlayVisible(visible: Boolean)

    fun setSettings(transform: (InputSettings) -> InputSettings)

    /** Routes one recognized gesture into controls/mouse/actions. */
    fun handleGesture(gesture: Gesture)

    /** Snapshot for a game session. */
    fun configuration(): InputConfiguration
}