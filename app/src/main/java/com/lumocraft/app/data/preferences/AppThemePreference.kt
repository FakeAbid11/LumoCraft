package com.lumocraft.app.data.preferences

import android.content.Context
import com.lumocraft.app.domain.model.ThemeMode

/**
 * Lightweight SharedPreferences-backed settings store.
 * Later stages may replace this with DataStore without touching callers.
 */
class AppThemePreference(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "lumocraft_settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
