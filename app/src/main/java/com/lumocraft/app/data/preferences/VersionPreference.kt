package com.lumocraft.app.data.preferences

import android.content.Context

/** Persists which installed version the Home screen launches. */
class VersionPreference(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSelectedVersionId(): String? =
        prefs.getString(KEY_SELECTED_VERSION_ID, null)

    fun saveSelectedVersionId(versionId: String) {
        prefs.edit().putString(KEY_SELECTED_VERSION_ID, versionId).apply()
    }

    private companion object {
        const val PREFS_NAME = "lumocraft_settings"
        const val KEY_SELECTED_VERSION_ID = "selected_version_id"
    }
}