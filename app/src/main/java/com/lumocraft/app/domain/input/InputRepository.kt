package com.lumocraft.app.domain.input

import kotlinx.coroutines.flow.StateFlow

/**
 * Persistence for input profiles, the active profile id and launcher
 * input settings. Implementations are free to use files, prefs or both.
 */
interface InputRepository {

    val profiles: StateFlow<List<InputProfile>>
    val activeProfileId: StateFlow<String>
    val settings: StateFlow<InputSettings>

    /** Loads everything from disk, seeding defaults on first run. */
    suspend fun load(): Result<Unit>

    suspend fun saveProfile(profile: InputProfile): Result<Unit>

    /** No-op when the profile is the last one. */
    suspend fun deleteProfile(profileId: String): Result<Unit>

    suspend fun selectProfile(profileId: String): Result<Unit>

    suspend fun saveSettings(settings: InputSettings): Result<Unit>

    suspend fun saveLayout(profileId: String, layout: ButtonLayout): Result<Unit>
}