package com.lumocraft.app.domain.runtime

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for Java runtimes on this device.
 * The UI only observes [observeRuntimes]; install/remove/verify/repair
 * are driven through this interface. Phase 6 will call
 * [getDefaultRuntime] to obtain a fully verified runtime for launch.
 */
interface RuntimeRepository {

    /** Emits the current list of runtimes whenever it changes. */
    fun observeRuntimes(): Flow<List<RuntimeInfo>>

    /** The default (selected) runtime, or null when none is installed. */
    suspend fun getDefaultRuntime(): RuntimeInfo?

    /** Detects the device architecture. */
    fun detectArchitecture(): RuntimeArchitecture

    /** Installs a runtime, emitting progress snapshots. */
    fun install(runtimeId: String): Flow<RuntimeProgress>

    /** Removes a runtime from disk and metadata. */
    suspend fun remove(runtimeId: String): Result<Unit>

    /** Verifies a runtime's integrity. */
    suspend fun verify(runtimeId: String): Result<RuntimeVerificationReport>

    /** Scans and redownloads only missing/corrupted files. */
    fun repair(runtimeId: String): Flow<RuntimeProgress>

    /** Sets the default runtime. */
    suspend fun setDefault(runtimeId: String): Result<Unit>

    /** Persists JVM configuration. */
    fun saveJvmConfiguration(config: JvmConfiguration)

    /** Loads the persisted JVM configuration. */
    fun loadJvmConfiguration(): JvmConfiguration
}

/** Progress snapshot for runtime install/repair. */
data class RuntimeProgress(
    val runtimeId: String,
    val stage: RuntimeStage,
    val fraction: Float? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {
    val isFinished: Boolean get() = stage == RuntimeStage.COMPLETE
}

enum class RuntimeStage {
    PREPARING,
    DOWNLOADING,
    EXTRACTING,
    VERIFYING,
    COMPLETE
}