package com.lumocraft.app.data.version

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.version.InstallProgress
import com.lumocraft.app.domain.version.InstallStage
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.InstalledVersionMetadata
import com.lumocraft.app.domain.version.MinecraftVersion
import com.lumocraft.app.domain.version.VerificationReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Vanilla install pipeline. Produces a fully prepared version directory:
 * version JSON, libraries, asset index + objects, logging configuration
 * and metadata, followed by a full verification pass.
 *
 * Every stage skips files that are already present and verified, which is
 * what makes [repair] cheap: it scans first and re-runs the pipeline with
 * only the broken paths forced.
 */
class VersionInstaller(
    private val storage: StorageManager,
    private val downloader: Downloader,
    private val libraryInstaller: LibraryInstaller,
    private val assetInstaller: AssetInstaller,
    private val verificationService: VerificationService,
) {

    suspend fun install(
        version: MinecraftVersion,
        onProgress: suspend (InstallProgress) -> Unit,
    ): Result<InstalledVersionMetadata> =
        runPipeline(version, force = null, writePending = true, onProgress)

    suspend fun repair(
        version: MinecraftVersion,
        onProgress: suspend (InstallProgress) -> Unit,
    ): Result<InstalledVersionMetadata> = withContext(Dispatchers.IO) {
        val id = version.id
        val verificationListener = listener(id, InstallStage.VERIFICATION, onProgress)
        storage.prepareDirectories()

        // 1. Scan: redownload only what is actually broken.
        verificationListener(null, 0, 0, 0, 0)
        val report = verificationService.scan(id)
        if (report.ok) {
            val metadata = storage.readMetadata(id)
            val verified = metadata?.copy(state = InstallState.INSTALLED)
                ?: InstalledVersionMetadata(
                    version = id,
                    installedAt = System.currentTimeMillis(),
                    source = version.url,
                    installerVersion = AppConfig.INSTALLER_VERSION,
                    state = InstallState.INSTALLED
                )
            storage.writeMetadata(verified)
            verificationListener(1f, report.totalLibraries + report.totalAssets, 0, 0, 0)
            onProgress(InstallProgress(id, InstallStage.COMPLETE, 1f, 0, 0, 0, 0))
            return@withContext Result.success(verified)
        }

        runPipeline(version, force = forcePlan(report), writePending = false, onProgress)
    }

    private fun forcePlan(report: VerificationReport) = RepairPlan(
        forceVersionJson = !report.versionJsonOk,
        forceLoggingConfig = !report.loggingConfigOk,
        libraryPaths = (report.missingLibraries + report.corruptLibraries)
            .map { it.path }
            .toSet(),
        assetHashes = report.missingAssets.toSet() + report.corruptAssets.toSet()
    )

    private suspend fun runPipeline(
        version: MinecraftVersion,
        force: RepairPlan?,
        writePending: Boolean,
        onProgress: suspend (InstallProgress) -> Unit,
    ): Result<InstalledVersionMetadata> = withContext(Dispatchers.IO) {
        val id = version.id
        val pending = InstalledVersionMetadata(
            version = id,
            installedAt = System.currentTimeMillis(),
            source = version.url,
            installerVersion = AppConfig.INSTALLER_VERSION,
            state = InstallState.PENDING
        )
        if (writePending) {
            storage.writeMetadata(pending)
        }
        storage.prepareDirectories()
        listener(id, InstallStage.PREPARING, onProgress)(1f, 0, 0, 0, 0)

        // VERSION_JSON
        val jsonFile = downloadVersionJson(version, force?.forceVersionJson == true, onProgress)
        if (jsonFile.isFailure) {
            return@withContext fail(id, pending, jsonFile.exceptionOrNull()?.message)
        }
        val json = try {
            JSONObject(jsonFile.getOrThrow().readText())
        } catch (e: Exception) {
            return@withContext fail(id, pending, "Downloaded version JSON is not valid")
        }

        // LIBRARIES
        val libraries = libraryInstaller.install(
            json,
            force?.libraryPaths ?: emptySet(),
            listener(id, InstallStage.LIBRARIES, onProgress)
        )
        if (libraries.isFailure) {
            return@withContext fail(id, pending, libraries.exceptionOrNull()?.message)
        }

        // ASSET_INDEX
        val index = assetInstaller.downloadIndex(
            json,
            listener(id, InstallStage.ASSET_INDEX, onProgress)
        )
        if (index.isFailure) {
            return@withContext fail(id, pending, index.exceptionOrNull()?.message)
        }

        // ASSETS
        val assets = assetInstaller.downloadObjects(
            index.getOrThrow(),
            force?.assetHashes ?: emptySet(),
            listener(id, InstallStage.ASSETS, onProgress)
        )
        if (assets.isFailure) {
            return@withContext fail(id, pending, assets.exceptionOrNull()?.message)
        }

        // LOGGING_CONFIG
        val logging = installLoggingConfig(id, json, force?.forceLoggingConfig == true, onProgress)
        if (logging.isFailure) {
            return@withContext fail(id, pending, logging.exceptionOrNull()?.message)
        }

        // VERIFICATION
        val verificationListener = listener(id, InstallStage.VERIFICATION, onProgress)
        verificationListener(null, 0, 0, 0, 0)
        val report = verificationService.scan(id)
        if (!report.ok) {
            storage.writeMetadata(pending.copy(state = InstallState.CORRUPTED))
            onProgress(InstallProgress(id, InstallStage.VERIFICATION, error = "Verification failed"))
            return@withContext Result.failure(IllegalStateException("Verification failed"))
        }
        val installed = pending.copy(state = InstallState.INSTALLED)
        storage.writeMetadata(installed)
        verificationListener(1f, report.totalLibraries + report.totalAssets, 0, 0, 0)
        onProgress(InstallProgress(id, InstallStage.COMPLETE, 1f, 0, 0, 0, 0))
        Result.success(installed)
    }

    private suspend fun downloadVersionJson(
        version: MinecraftVersion,
        force: Boolean,
        onProgress: suspend (InstallProgress) -> Unit,
    ): Result<File> {
        val id = version.id
        val destination = storage.versionJsonFile(id)
        if (!force && destination.isFile &&
            (version.sha1 == null || HashUtils.sha1(destination) == version.sha1)
        ) {
            onProgress(InstallProgress(id, InstallStage.VERSION_JSON, 1f, 1, 0, 0, 0))
            return Result.success(destination)
        }
        val tracker = DownloadTracker(1, version.size ?: 0L, listener(id, InstallStage.VERSION_JSON, onProgress))
        var lastFraction = 0f
        val result = downloader.downloadVerified(
            url = version.url,
            destination = destination,
            expectedSha1 = version.sha1,
            expectedSize = version.size,
            force = force
        ) { fraction ->
            fraction?.let { f ->
                val delta = ((f - lastFraction) * (version.size ?: 0L)).toLong()
                lastFraction = f
                tracker.addBytes(delta)
            }
        }
        if (result.isFailure) {
            tracker.flush()
        } else {
            tracker.countDone(0)
        }
        return result
    }

    private suspend fun installLoggingConfig(
        id: String,
        json: JSONObject,
        force: Boolean,
        onProgress: suspend (InstallProgress) -> Unit,
    ): Result<Unit> {
        val ref = VersionJson.loggingConfig(json)
        if (ref == null) {
            onProgress(InstallProgress(id, InstallStage.LOGGING_CONFIG, 1f, 0, 0, 0, 0))
            return Result.success(Unit)
        }
        val destination = storage.loggingConfigFile(id, ref.id)
        if (!force && destination.isFile &&
            (ref.sha1 == null || HashUtils.sha1(destination) == ref.sha1)
        ) {
            onProgress(InstallProgress(id, InstallStage.LOGGING_CONFIG, 1f, 1, 0, 0, 0))
            return Result.success(Unit)
        }
        val tracker = DownloadTracker(1, ref.size ?: 0L, listener(id, InstallStage.LOGGING_CONFIG, onProgress))
        var lastFraction = 0f
        val result = downloader.downloadVerified(
            url = ref.url,
            destination = destination,
            expectedSha1 = ref.sha1,
            expectedSize = ref.size,
            force = force
        ) { fraction ->
            fraction?.let { f ->
                val delta = ((f - lastFraction) * (ref.size ?: 0L)).toLong()
                lastFraction = f
                tracker.addBytes(delta)
            }
        }
        if (result.isFailure) {
            tracker.flush()
            return result.map { Unit }
        }
        tracker.countDone(0)
        return Result.success(Unit)
    }

    private fun listener(
        id: String,
        stage: InstallStage,
        onProgress: suspend (InstallProgress) -> Unit,
    ): StageListener = { fraction, done, remaining, bytes, total ->
        onProgress(InstallProgress(id, stage, fraction, done, remaining, bytes, total))
    }

    private fun fail(
        id: String,
        pending: InstalledVersionMetadata,
        message: String?,
    ): Result<InstalledVersionMetadata> {
        storage.writeMetadata(pending.copy(state = InstallState.FAILED))
        return Result.failure(IllegalStateException(message ?: "Installation failed"))
    }

    private data class RepairPlan(
        val forceVersionJson: Boolean,
        val forceLoggingConfig: Boolean,
        val libraryPaths: Set<String>,
        val assetHashes: Set<String>
    )
}