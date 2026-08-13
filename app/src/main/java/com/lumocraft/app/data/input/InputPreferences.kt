package com.lumocraft.app.data.input

import android.content.Context
import com.lumocraft.app.domain.input.ControlButton
import com.lumocraft.app.domain.input.InputSettings

/**
 * SharedPreferences store for launcher-wide input settings and the
 * active profile id. Profiles themselves live in JSON files next to
 * the launcher data ([com.lumocraft.app.data.input.JsonInputRepository]).
 */
class InputPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): InputSettings = InputSettings(
        cursorSpeed = prefs.getFloat(KEY_CURSOR_SPEED, 1f).coerceIn(MIN_CURSOR_SPEED, MAX_CURSOR_SPEED),
        buttonOpacity = prefs.getFloat(KEY_BUTTON_OPACITY, ControlButton.DEFAULT_BUTTON_OPACITY)
            .coerceIn(MIN_OPACITY, 1f),
        fadeIdleControls = prefs.getBoolean(KEY_FADE_IDLE, true)
    )

    fun saveSettings(settings: InputSettings) {
        prefs.edit()
            .putFloat(KEY_CURSOR_SPEED, settings.cursorSpeed)
            .putFloat(KEY_BUTTON_OPACITY, settings.buttonOpacity)
            .putBoolean(KEY_FADE_IDLE, settings.fadeIdleControls)
            .apply()
    }

    fun loadActiveProfileId(): String? = prefs.getString(KEY_ACTIVE_PROFILE, null)

    fun saveActiveProfileId(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
    }

    private companion object {
        const val PREFS_NAME = "lumocraft_settings"
        const val KEY_CURSOR_SPEED = "input_cursor_speed"
        const val KEY_BUTTON_OPACITY = "input_button_opacity"
        const val KEY_FADE_IDLE = "input_fade_idle"
        const val KEY_ACTIVE_PROFILE = "input_active_profile"

        const val MIN_CURSOR_SPEED = 0.25f
        const val MAX_CURSOR_SPEED = 3f
        const val MIN_OPACITY = 0.1f
    }
}