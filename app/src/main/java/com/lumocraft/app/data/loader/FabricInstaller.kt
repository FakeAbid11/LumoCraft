package com.lumocraft.app.data.loader

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.LibraryInstaller
import com.lumocraft.app.data.version.VerificationService
import com.lumocraft.app.domain.loader.LoaderInstallProgress
import com.lumocraft.app.domain.loader.LoaderInstallStage
import com.lumocraft.app.domain.loader.LoaderInstaller
import com.lumocraft.app.domain.loader.LoaderMetadata
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.InstalledVersionMetadata
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fabric install pipeline. Produces a fully prepared version directory:
 *
 *  <versions>/fabric-loader-<loader>-<mc>/
 *  ├── fabric-loader-<loader>-<mc>.json   (loader profile, inheritsFrom <mc>)
 *  └── metadata.json                      (standard version metadata)
 *
 * plus loader metadata under `loader/fabric/instances/`. The loader
 * profile declares the Fabric libraries (maven coordinates, resolved by
 * [com.lumocraft.app.data.version.VersionJson]) which are downloaded
 * into the shared `libraries/` folder through the existing
 * [LibraryInstaller]; the vanilla version's libraries are inherited at
 * launch time via the version JSON chain.
 *
 * The underlying Minecraft version must be installed first (its JSON is
 * the parent of the profile).
 */
class FabricInstaller(
    private val storage: StorageManager,
    private val downloader: Downloader,
    private val metadataService: FabricMetadataService,
    private val libraryInstaller: LibraryInstaller,
    private val verificationService: VerificationService,
    private val onFilesChanged: (suspend (versionId: String) -> Unit)? = null,
) : LoaderInstaller {

    override val type: LoaderType = LoaderType.FABRIC

    override suspend fun install(
        minecraftVersion: String,
        loaderVersion: String,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): Result<LoaderMetadata> = withContext(Dispatchers.IO) {
        val pending = LoaderMetadata(
            instanceId = "",
            type = LoaderType.FABRIC,
            minecraftVersion = minecraftVersion,
            loaderVersion = loaderVersion,
            installerVersion = INSTALLER_VERSION,
            installedAt = System.currentTimeMillis(),
            state = InstallState.PENDING
        )
        listener(LoaderInstallStage.PREPARING, onProgress)(1f, 0, 0, 0, 0)
        storage.prepareDirectories()

        if (!isVanillaInstalled(minecraftVersion)) {
            return@withContext fail(
                pending,
                "Install Minecraft $minecraftVersion before installing Fabric"
            )
        }

        val profile = fetchProfile(minecraftVersion, loaderVersion, onProgress)
            ?: return@withContext fail(pending, "Fabric metadata could not be fetched")

        val instanceId = profile.instanceId
        val metadata = pending.copy(instanceId = instanceId)
        runCatching { storage.writeMetadata(metadata.versionMetadata()) }

        val jsonResult = writeProfileJson(profile, onProgress)
        if (jsonResult.isFailure) {
            return@withContext fail(metadata, jsonResult.exceptionOrNull()?.message)
        }

        val librariesResult = installLibraries(profile, emptySet(), onProgress)
        if (librariesResult.isFailure) {
            return@withContext fail(metadata, librariesResult.exceptionOrNull()?.message)
        }

        val verified = verify(instanceId, metadata, onProgress)
            ?: return@withContext Result.failure(IOException("Verification failed"))
        runCatching { storage.writeMetadata(verified.versionMetadata()) }
        runCatching { storage.writeLoaderMetadata(verified) }
        runCatching { onFilesChanged?.invoke(instanceId) }
        onProgress(LoaderInstallProgress(instanceId, LoaderInstallStage.COMPLETE, 1f, 0, 0, 0, 0))
        Result.success(verified)
    }

    override suspend fun repair(
        instanceId: String,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): Result<LoaderMetadata> = withContext(Dispatchers.IO) {
        val existing = storage.readLoaderMetadata(LoaderType.FABRIC, instanceId)
        if (existing == null) {
            return@withContext Result.failure(IOException("Fabric instance '$instanceId' not found"))
        }
        listener(LoaderInstallStage.PREPARING, onProgress)(1f, 0, 0, 0, 0)
        storage.prepareDirectories()

        val report = verificationService.scan(instanceId)
        if (report.ok) {
            val restored = existing.copy(state = InstallState.INSTALLED)
            runCatching { storage.writeLoaderMetadata(restored) }
            runCatching { storage.writeMetadata(restored.versionMetadata()) }
            runCatching { onFilesChanged?.invoke(instanceId) }
            onProgress(LoaderInstallProgress(instanceId, LoaderInstallStage.COMPLETE, 1f, 0, 0, 0, 0))
            return@withContext Result.success(restored)
        }

        // Rebuild the profile: the stored version JSON, or a fresh fetch.
        val profile = profileFromDisk(instanceId, existing) ?: fetchProfile(
            existing.minecraftVersion,
            existing.loaderVersion,
            onProgress
        ) ?: return@withContext Result.failure(IOException("Fabric metadata could not be fetched"))

        if (profile.instanceId != instanceId) {
            return@withContext Result.failure(IOException("Fabric instance id mismatch"))
        }

        val jsonResult = writeProfileJson(profile, onProgress)
        if (jsonResult.isFailure) {
            return@withContext fail(existing, jsonResult.exceptionOrNull()?.message)
        }

        val forcePaths = (report.missingLibraries + report.corruptLibraries)
            .map { it.path }
            .toSet()
        val librariesResult = installLibraries(profile, forcePaths, onProgress)
        if (librariesResult.isFailure) {
            return@withContext fail(existing, librariesResult.exceptionOrNull()?.message)
        }

        val verified = verify(instanceId, existing, onProgress)
            ?: return@withContext Result.failure(IOException("Verification failed"))
        runCatching { storage.writeLoaderMetadata(verified) }
        runCatching { onFilesChanged?.invoke(instanceId) }
        onProgress(LoaderInstallProgress(instanceId, LoaderInstallStage.COMPLETE, 1f, 0, 0, 0, 0))
        Result.success(verified)
    }

    private suspend fun fetchProfile(
        minecraftVersion: String,
        loaderVersion: String,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): FabricProfile? {
        listener(LoaderInstallStage.METADATA, onProgress)(null, 0, 0, 0, 0)
        return metadataService.profile(minecraftVersion, loaderVersion)
            .getOrElse { return null }
            .also { listener(LoaderInstallStage.METADATA, onProgress)(1f, 1, 0, 0, 0) }
    }

    private fun profileFromDisk(instanceId: String, metadata: LoaderMetadata): FabricProfile? {
        val jsonFile = storage.versionJsonFile(instanceId)
        if (!jsonFile.isFile) return null
        val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull() ?: return null
        val libraries = buildList {
            val array = json.optJSONArray("libraries") ?: return@buildList
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val name = entry.optString("name")
                if (name.isEmpty()) continue
                add(
                    FabricLibrary(
                        name = name,
                        url = entry.optString("url"),
                        sha1 = entry.optString("sha1").takeIf { it.isNotEmpty() },
                        size = entry.optLong("size", -1L).takeIf { it >= 0 }
                    )
                )
            }
        }
        return FabricProfile(
            loaderMaven = "",
            intermediaryMaven = "",
            loaderVersion = metadata.loaderVersion,
            minecraftVersion = metadata.minecraftVersion,
            mainClass = json.optString("mainClass"),
            libraries = libraries,
            gameArguments = buildList {
                val game = json.optJSONObject("arguments")?.optJSONArray("game") ?: JSONArray()
                for (i in 0 until game.length()) {
                    game.opt(i)?.let { if (it is String) add(it) }
                }
            }
        )
    }

    /** Writes the loader profile as the instance version JSON. */
    private suspend fun writeProfileJson(
        profile: FabricProfile,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): Result<Unit> {
        listener(LoaderInstallStage.METADATA, onProgress)(null, 0, 0, 0, 0)
        return runCatching {
            val gameArguments = buildList {
                addAll(profile.gameArguments)
                if ("--fabric.gameVersion" !in this) {
                    add("--fabric.gameVersion")
                    add(profile.minecraftVersion)
                }
                if ("--fabric.loaderVersion" !in this) {
                    add("--fabric.loaderVersion")
                    add(profile.loaderVersion)
                }
            }
            val libraries = JSONArray()
            profile.libraries.forEach { lib ->
                libraries.put(
                    JSONObject()
                        .put("name", lib.name)
                        .put("url", lib.url)
                        .apply {
                            lib.sha1?.let { put("sha1", it) }
                            lib.size?.let { put("size", it) }
                        }
                )
            }
            val json = JSONObject()
                .put("id", profile.instanceId)
                .put("inheritsFrom", profile.minecraftVersion)
                .put("type", "release")
                .put("mainClass", profile.mainClass)
                .put("libraries", libraries)
                .put("arguments", JSONObject().put("game", JSONArray(gameArguments)))
            val file = storage.versionJsonFile(profile.instanceId)
            file.parentFile?.mkdirs()
            file.writeText(json.toString())
            listener(LoaderInstallStage.METADATA, onProgress)(1f, 1, 0, 0, 0)
        }
    }

    private suspend fun installLibraries(
        profile: FabricProfile,
        forcePaths: Set<String>,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): Result<Unit> {
        if (profile.libraries.isEmpty()) {
            listener(LoaderInstallStage.LIBRARIES, onProgress)(1f, 0, 0, 0, 0)
            return Result.success(Unit)
        }
        val json = profileJsonForLibraries(profile)
        return libraryInstaller.install(
            json = json,
            forcePaths = forcePaths,
            onProgress = { fraction, done, remaining, bytes, total ->
                onProgress(
                    LoaderInstallProgress(
                        instanceId = profile.instanceId,
                        stage = LoaderInstallStage.LIBRARIES,
                        stageFraction = fraction,
                        filesCompleted = done,
                        filesRemaining = remaining,
                        downloadedBytes = bytes,
                        totalBytes = total
                    )
                )
            }
        )
    }

    /** The profile as a JSONObject so the shared library installer applies. */
    private fun profileJsonForLibraries(profile: FabricProfile): JSONObject {
        val libraries = JSONArray()
        profile.libraries.forEach { lib ->
            libraries.put(
                JSONObject()
                    .put("name", lib.name)
                    .put("url", lib.url)
                    .apply {
                        lib.sha1?.let { put("sha1", it) }
                        lib.size?.let { put("size", it) }
                    }
            )
        }
        return JSONObject().put("libraries", libraries)
    }

    private suspend fun verify(
        instanceId: String,
        metadata: LoaderMetadata,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): LoaderMetadata? {
        listener(LoaderInstallStage.VERIFICATION, onProgress)(null, 0, 0, 0, 0)
        val report = verificationService.scan(instanceId)
        if (!report.ok) {
            runCatching { storage.writeLoaderMetadata(metadata.copy(state = InstallState.CORRUPTED)) }
            runCatching { onFilesChanged?.invoke(instanceId) }
            onProgress(
                LoaderInstallProgress(
                    instanceId = instanceId,
                    stage = LoaderInstallStage.VERIFICATION,
                    error = "Verification failed"
                )
            )
            return null
        }
        listener(LoaderInstallStage.VERIFICATION, onProgress)(1f, report.totalLibraries, 0, 0, 0)
        return metadata.copy(state = InstallState.INSTALLED)
    }

    private fun isVanillaInstalled(minecraftVersion: String): Boolean =
        storage.readMetadata(minecraftVersion)?.state == InstallState.INSTALLED

    private fun fail(
        metadata: LoaderMetadata,
        message: String?,
    ): Result<LoaderMetadata> {
        if (metadata.instanceId.isNotEmpty()) {
            runCatching { storage.writeLoaderMetadata(metadata.copy(state = InstallState.FAILED)) }
        }
        return Result.failure(IOException(message ?: "Fabric installation failed"))
    }

    private fun listener(
        stage: LoaderInstallStage,
        onProgress: suspend (LoaderInstallProgress) -> Unit,
    ): LoaderStageListener = { fraction, done, remaining, bytes, total ->
        onProgress(
            LoaderInstallProgress(
                instanceId = "",
                stage = stage,
                stageFraction = fraction,
                filesCompleted = done,
                filesRemaining = remaining,
                downloadedBytes = bytes,
                totalBytes = total
            )
        )
    }

    private fun LoaderMetadata.versionMetadata(): InstalledVersionMetadata =
        InstalledVersionMetadata(
            version = instanceId,
            installedAt = installedAt,
            source = "https://meta.fabricmc.net",
            installerVersion = AppConfig.INSTALLER_VERSION,
            state = state
        )

    private companion object {
        const val INSTALLER_VERSION = "fabric-installer-1"
    }
}

/** Function type matching the shared stage listener used by installers. */
typealias LoaderStageListener = suspend (
    fraction: Float?,
    done: Int,
    remaining: Int,
    bytes: Long,
    total: Long
) -> Unit