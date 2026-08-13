package com.lumocraft.app.data.preferences

import android.content.Context

/** Persists whether the first-launch onboarding has been completed. */
class OnboardingPreference(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun markComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "lumocraft_settings"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
