package com.lumocraft.app.data.loader

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.loader.LoaderInstallProgress
import com.lumocraft.app.domain.loader.LoaderInstallStage
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.loader.LoaderInstaller
import com.lumocraft.app.domain.loader.LoaderMetadata
import com.lumocraft.app.domain.loader.LoaderRepository
import com.lumocraft.app.domain.loader.LoaderStatus
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.loader.LoaderVersion
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * [LoaderRepository] combining per-type metadata sources, installers and
 * the loader registry (disk). Loader-type agnostic: every call dispatches
 * through [LoaderType], so later loader phases only register another
 * [LoaderMetadataSource] and [LoaderInstaller].
 *
 * Install states are re-read from disk when a pipeline finishes, so
 * PENDING/FAILED/CORRUPTED transitions always match the actual files.
 */
class DefaultLoaderRepository(
    private val storage: StorageManager,
    private val scanner: LoaderScanner,
    private val sources: List<LoaderMetadataSource>,
    private val installers: List<LoaderInstaller>,
    /**
     * Called whenever a loader's files change (install/repair/removal)
     * so the launch cache and smart verification rows can be invalidated
     * exactly when needed.
     */
    private val onFilesChanged: (suspend (versionId: String) -> Unit)? = null,
) : LoaderRepository {

    private val _installedLoaders = MutableStateFlow(emptyList<LoaderInstance>())

    override fun observeInstalledLoaders(): Flow<List<LoaderInstance>> = flow {
        refresh()
        emitAll(_installedLoaders)
    }

    override suspend fun fetchLoaderVersions(
        type: LoaderType,
        minecraftVersion: String,
    ): Result<List<LoaderVersion>> {
        val source = sources.firstOrNull { it.type == type }
            ?: return Result.failure(IOException("Loader '${type.id}' is not supported yet"))
        return source.loaderVersions(minecraftVersion)
    }

    override fun install(
        type: LoaderType,
        minecraftVersion: String,
        loaderVersion: String,
    ): Flow<LoaderInstallProgress> = pipeline { onProgress ->
        installer(type).install(minecraftVersion, loaderVersion, onProgress)
    }

    override fun repair(type: LoaderType, instanceId: String): Flow<LoaderInstallProgress> =
        pipeline { onProgress ->
            installer(type).repair(instanceId, onProgress)
        }

    override suspend fun remove(type: LoaderType, instanceId: String): Result<Unit> {
        val metadata = storage.readLoaderMetadata(type, instanceId)
            ?: return Result.failure(IOException("Loader instance '$instanceId' not found"))
        storage.removeVersionDirectory(instanceId)
        storage.removeLoaderMetadata(type, instanceId)
        onFilesChanged?.invoke(instanceId)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun resolveActiveLoader(versionId: String): LoaderInstance? {
        for (type in LoaderType.entries.filter { it != LoaderType.VANILLA }) {
            val metadata = storage.readLoaderMetadata(type, versionId) ?: continue
            return LoaderInstance(metadata, statusOf(metadata))
        }
        return null
    }

    private suspend fun statusOf(metadata: LoaderMetadata): LoaderStatus =
        scanner.scanInstances(metadata.type)
            .firstOrNull { it.instanceId == metadata.instanceId }
            ?.status
            ?: LoaderStatus.MISSING

    private fun pipeline(
        run: suspend (suspend (LoaderInstallProgress) -> Unit) -> Result<LoaderMetadata>,
    ): Flow<LoaderInstallProgress> = flow {
        try {
            val result = run { emit(it) }
            if (result.isFailure) {
                emit(
                    LoaderInstallProgress(
                        instanceId = "",
                        stage = LoaderInstallStage.COMPLETE,
                        error = result.exceptionOrNull()?.message
                    )
                )
            }
        } finally {
            refresh()
        }
    }

    private fun installer(type: LoaderType): LoaderInstaller =
        installers.firstOrNull { it.type == type }
            ?: throw IOException("Loader '${type.id}' is not supported yet")

    private suspend fun refresh() {
        _installedLoaders.value = LoaderType.entries
            .filter { it != LoaderType.VANILLA }
            .flatMap { type -> scanner.scanInstances(type) }
    }
}