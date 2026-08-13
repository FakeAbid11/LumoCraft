package com.lumocraft.app.data.performance

import android.content.Context
import com.lumocraft.app.domain.performance.JvmProfile

/**
 * Persists the manual JVM profile override (null = automatic). The
 * override survives restarts and takes precedence over the
 * device-derived recommendation at launch.
 */
class PerformancePreference(context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun jvmProfileOverride(): JvmProfile? {
        val name = prefs.getString(KEY_JVM_PROFILE_OVERRIDE, null) ?: return null
        return JvmProfile.fromName(name)
    }

    fun setJvmProfileOverride(profile: JvmProfile?) {
        prefs.edit().apply {
            if (profile == null) remove(KEY_JVM_PROFILE_OVERRIDE)
            else putString(KEY_JVM_PROFILE_OVERRIDE, profile.name)
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "lumocraft_performance"
        const val KEY_JVM_PROFILE_OVERRIDE = "jvm_profile_override"
    }
}