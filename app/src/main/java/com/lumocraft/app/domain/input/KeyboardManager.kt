package com.lumocraft.app.domain.input

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Modifier keys currently held. */
data class KeyModifiers(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
)

/** One key press/release/repeat event. */
data class KeyStroke(
    val keyCode: Int,
    val scanCode: Int,
    val down: Boolean,
    val repeat: Boolean = false,
    val modifiers: KeyModifiers = KeyModifiers()
)

/** Snapshot of the keyboard: connection, pressed keys, modifiers. */
data class KeyboardState(
    val connected: Boolean = false,
    val pressedKeys: Set<Int> = emptySet(),
    val modifiers: KeyModifiers = KeyModifiers()
) {
    val hasAnyPressed: Boolean get() = pressedKeys.isNotEmpty()
}

/**
 * Hardware and soft keyboard support: key state tracking, key repeat
 * and modifier keys. Minecraft integration is deliberately out of
 * scope — consumers read [state] and [keyEvents].
 */
interface KeyboardManager {

    val state: StateFlow<KeyboardState>

    /** Press/release/repeat events, including repeats while held. */
    val keyEvents: SharedFlow<KeyStroke>

    /**
     * Feeds one key event. [repeat] marks an auto-repeat of a held key.
     * Returns true when the key was handled.
     */
    fun onKeyEvent(keyCode: Int, scanCode: Int, down: Boolean, repeat: Boolean = false): Boolean

    fun isPressed(keyCode: Int): Boolean

    /** Marks the hardware keyboard as connected/disconnected. */
    fun setConnected(connected: Boolean)
}