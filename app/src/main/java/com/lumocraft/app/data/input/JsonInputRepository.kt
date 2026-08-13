package com.lumocraft.app.data.input

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.input.ButtonLayout
import com.lumocraft.app.domain.input.ControlButton
import com.lumocraft.app.domain.input.ControlKind
import com.lumocraft.app.domain.input.DEFAULT_PROFILE_ID
import com.lumocraft.app.domain.input.InputAction
import com.lumocraft.app.domain.input.InputProfile
import com.lumocraft.app.domain.input.InputRepository
import com.lumocraft.app.domain.input.InputSettings
import com.lumocraft.app.domain.input.MouseMode
import com.lumocraft.app.domain.input.defaultInputProfile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores every input profile as `<launcherRoot>/input/profiles/<id>.json`
 * using org.json — no extra dependencies. The active profile id and
 * launcher input settings live in [InputPreferences].
 */
class JsonInputRepository(
    private val storage: StorageManager,
    private val preferences: InputPreferences,
) : InputRepository {

    private val _profiles = MutableStateFlow<List<InputProfile>>(emptyList())
    override val profiles: StateFlow<List<InputProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow(DEFAULT_PROFILE_ID)
    override val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _settings = MutableStateFlow(InputSettings())
    override val settings: StateFlow<InputSettings> = _settings.asStateFlow()

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = storage.inputProfilesDirectory()
            dir.mkdirs()
            val loaded = dir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.mapNotNull { readProfile(it) }
                ?.sortedBy { it.name }
                ?: emptyList()

            val profiles = if (loaded.isEmpty()) {
                val seed = defaultInputProfile()
                writeProfileFile(seed)
                listOf(seed)
            } else {
                loaded
            }

            val savedId = preferences.loadActiveProfileId()
            val active = savedId?.takeIf { id -> profiles.any { it.id == id } }
                ?: profiles.first().id
            preferences.saveActiveProfileId(active)

            _profiles.value = profiles
            _activeProfileId.value = active
            _settings.value = preferences.loadSettings()
        }
    }

    override suspend fun saveProfile(profile: InputProfile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            writeProfileFile(profile)
            val next = _profiles.value.map { if (it.id == profile.id) profile else it }
            if (next.none { it.id == profile.id }) {
                _profiles.value = next + profile
            } else {
                _profiles.value = next
            }
        }
    }

    override suspend fun deleteProfile(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _profiles.value
            if (current.size <= 1 || current.none { it.id == profileId }) return@runCatching
            profileFile(profileId).delete()
            _profiles.value = current.filterNot { it.id == profileId }
            if (_activeProfileId.value == profileId) {
                val next = _profiles.value.first()
                _activeProfileId.value = next.id
                preferences.saveActiveProfileId(next.id)
            }
        }
    }

    override suspend fun selectProfile(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (_profiles.value.any { it.id == profileId }) {
                _activeProfileId.value = profileId
                preferences.saveActiveProfileId(profileId)
            }
        }
    }

    override suspend fun saveSettings(settings: InputSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            preferences.saveSettings(settings)
            _settings.value = settings
        }
    }

    override suspend fun saveLayout(profileId: String, layout: ButtonLayout): Result<Unit> {
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return Result.success(Unit)
        return saveProfile(profile.copy(buttonLayout = layout))
    }

    private fun profilesDirectory(): File = storage.inputProfilesDirectory()

    private fun profileFile(profileId: String): File =
        File(profilesDirectory(), "${profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun writeProfileFile(profile: InputProfile) {
        val layout = profile.buttonLayout
        val buttons = JSONArray()
        for (button in layout.buttons) {
            buttons.put(
                JSONObject()
                    .put(KEY_ID, button.id)
                    .put(KEY_ACTION, button.action.name)
                    .put(KEY_LABEL, button.label)
                    .put(KEY_X, button.x.toDouble())
                    .put(KEY_Y, button.y.toDouble())
                    .put(KEY_WIDTH, button.width.toDouble())
                    .put(KEY_HEIGHT, button.height.toDouble())
                    .put(KEY_OPACITY, button.opacity.toDouble())
                    .put(KEY_KIND, button.kind.name)
            )
        }
        val json = JSONObject()
            .put(KEY_ID, profile.id)
            .put(KEY_NAME, profile.name)
            .put(KEY_SENSITIVITY, profile.sensitivity.toDouble())
            .put(KEY_INVERT_Y, profile.invertY)
            .put(KEY_MOUSE_MODE, profile.mouseMode.name)
            .put(KEY_CONTROLLER_ENABLED, profile.controllerEnabled)
            .put(KEY_KEYBOARD_ENABLED, profile.keyboardEnabled)
            .put(
                KEY_LAYOUT,
                JSONObject()
                    .put(KEY_VERSION, layout.version)
                    .put(KEY_BUTTONS, buttons)
            )
        profileFile(profile.id).apply {
            parentFile?.mkdirs()
            writeText(json.toString())
        }
    }

    private fun readProfile(file: File): InputProfile? = runCatching {
        val json = JSONObject(file.readText())
        val layoutJson = json.optJSONObject(KEY_LAYOUT) ?: return@runCatching null
        val buttonsJson = layoutJson.optJSONArray(KEY_BUTTONS) ?: JSONArray()
        val buttons = (0 until buttonsJson.length()).mapNotNull { i ->
            val b = buttonsJson.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                ControlButton(
                    id = b.getString(KEY_ID),
                    action = InputAction.valueOf(b.getString(KEY_ACTION)),
                    label = b.optString(KEY_LABEL),
                    x = b.optDouble(KEY_X, 0.0).toFloat(),
                    y = b.optDouble(KEY_Y, 0.0).toFloat(),
                    width = b.optDouble(KEY_WIDTH, 0.1).toFloat(),
                    height = b.optDouble(KEY_HEIGHT, 0.1).toFloat(),
                    opacity = b.optDouble(KEY_OPACITY, ControlButton.DEFAULT_BUTTON_OPACITY.toDouble()).toFloat(),
                    kind = runCatching { ControlKind.valueOf(b.optString(KEY_KIND)) }
                        .getOrDefault(ControlKind.BUTTON)
                )
            }.getOrNull()
        }
        InputProfile(
            id = json.getString(KEY_ID),
            name = json.optString(KEY_NAME, json.getString(KEY_ID)),
            sensitivity = json.optDouble(KEY_SENSITIVITY, 1.0).toFloat(),
            invertY = json.optBoolean(KEY_INVERT_Y, false),
            mouseMode = runCatching { MouseMode.valueOf(json.optString(KEY_MOUSE_MODE)) }
                .getOrDefault(MouseMode.RELATIVE),
            buttonLayout = ButtonLayout(
                version = layoutJson.optInt(KEY_VERSION, ButtonLayout.LAYOUT_VERSION),
                buttons = buttons
            ),
            controllerEnabled = json.optBoolean(KEY_CONTROLLER_ENABLED, true),
            keyboardEnabled = json.optBoolean(KEY_KEYBOARD_ENABLED, true)
        )
    }.getOrNull()

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_INVERT_Y = "invertY"
        const val KEY_MOUSE_MODE = "mouseMode"
        const val KEY_CONTROLLER_ENABLED = "controllerEnabled"
        const val KEY_KEYBOARD_ENABLED = "keyboardEnabled"
        const val KEY_LAYOUT = "layout"
        const val KEY_VERSION = "version"
        const val KEY_BUTTONS = "buttons"
        const val KEY_ACTION = "action"
        const val KEY_LABEL = "label"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_OPACITY = "opacity"
        const val KEY_KIND = "kind"
    }
}