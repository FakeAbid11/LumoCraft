package com.lumocraft.app.data.version

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallStage
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.InstalledVersionMetadata
import com.lumocraft.app.domain.version.MinecraftVersion
import com.lumocraft.app.domain.version.VersionManifest
import com.lumocraft.app.domain.version.VersionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow

/**
 * [VersionRepository] combining the manifest service (network) with the
 * installer pipeline and local metadata (disk). The UI only ever sees this
 * interface. Install states are re-read from disk when a pipeline finishes
 * (or is cancelled), so PENDING/Failed/CORRUPTED transitions always match
 * the actual metadata files.
 */
class DefaultVersionRepository(
    private val manifestService: ManifestService,
    private val installer: VersionInstaller,
    private val storage: StorageManager,
) : VersionRepository {

    private val _installedStates = MutableStateFlow(storage.readInstallStates())

    override fun observeInstalledStates() = _installedStates.asStateFlow()

    override suspend fun fetchManifest(): Result<VersionManifest> =
        manifestService.fetchManifest()

    override fun install(version: MinecraftVersion): Flow<InstallProgress> =
        pipeline(version) { onProgress -> installer.install(version, onProgress) }

    override fun repair(version: MinecraftVersion): Flow<InstallProgress> =
        pipeline(version) { onProgress -> installer.repair(version, onProgress) }

    override suspend fun remove(versionId: String): Result<Unit> {
        val removed = storage.removeVersionDirectory(versionId)
        runCatching { installer.onFilesChanged?.invoke(versionId) }
        _installedStates.value = storage.readInstallStates()
        return if (removed) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Version '$versionId' not found"))
        }
    }

    private fun pipeline(
        version: MinecraftVersion,
        run: suspend (suspend (InstallProgress) -> Unit) -> Result<InstalledVersionMetadata>,
    ): Flow<InstallProgress> = channelFlow {
        try {
            val result = run { send(it) }
            if (result.isFailure) {
                send(
                    InstallProgress(
                        versionId = version.id,
                        stage = InstallStage.COMPLETE,
                        error = result.exceptionOrNull()?.message
                    )
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            send(
                InstallProgress(
                    versionId = version.id,
                    stage = InstallStage.COMPLETE,
                    error = e.message
                )
            )
        } finally {
            // Re-sync with disk (may be PENDING/FAILED/CORRUPTED after the run).
            runCatching { storage.readInstallStates() }
                .onSuccess { _installedStates.value = it }
        }
    }
}