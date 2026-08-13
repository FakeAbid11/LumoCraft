package com.lumocraft.app.data.preferences

import android.content.Context
import com.lumocraft.app.domain.native.RendererProfile
import com.lumocraft.app.domain.native.RendererType
import com.lumocraft.app.domain.native.ResolutionScale
import org.json.JSONObject

/**
 * Persists the [RendererProfile] as a small JSON document in
 * SharedPreferences. Same pattern as [com.lumocraft.app.data.preferences.AppThemePreference].
 */
class RendererPreference(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfile(): RendererProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return RendererProfile.preset(RendererType.COMPATIBILITY)
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return RendererProfile()
        return RendererProfile(
            renderer = runCatching { RendererType.valueOf(json.optString("renderer")) }
                .getOrDefault(RendererType.COMPATIBILITY),
            resolutionScale = runCatching { ResolutionScale.valueOf(json.optString("resolutionScale")) }
                .getOrDefault(ResolutionScale.PERCENT_75),
            fpsLimit = if (json.has("fpsLimit") && !json.isNull("fpsLimit")) {
                json.optInt("fpsLimit").takeIf { it > 0 }
            } else {
                null
            },
            vsync = json.optBoolean("vsync", false),
            mipmaps = json.optInt("mipmaps", 0).coerceAtLeast(0)
        )
    }

    fun saveProfile(profile: RendererProfile) {
        val json = JSONObject().apply {
            put("renderer", profile.renderer.name)
            put("resolutionScale", profile.resolutionScale.name)
            profile.fpsLimit?.let { put("fpsLimit", it) }
            put("vsync", profile.vsync)
            put("mipmaps", profile.mipmaps)
        }
        prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "lumocraft_settings"
        const val KEY_PROFILE = "renderer_profile"
    }
}