package com.lumocraft.app.data.version

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.launch.LauncherLogRepository
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
    /**
     * Called whenever a version's files change (install/repair/verification
     * failure/removal) so the launch cache and smart verification rows can
     * be invalidated exactly when needed.
     */
    val onFilesChanged: (suspend (versionId: String) -> Unit)? = null,
    /**
     * Optional session logger for structured install-stage logs. Null in
     * tests and standalone use; every call is a no-op then.
     */
    private val logs: LauncherLogRepository? = null,
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
        logStageStart(id, InstallStage.VERIFICATION)
        verificationListener(null, 0, 0, 0, 0)
        val report = verificationService.scan(id)
        if (report.ok) {
            logStageEnd(id, InstallStage.VERIFICATION)
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
            runCatching { onFilesChanged?.invoke(id) }
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
        // Required order: every folder (.minecraft/, versions/, libraries/,
        // assets/, runtime/, logs/) must exist before any metadata write.
        storage.prepareDirectories()
        if (writePending) {
            runCatching { storage.writeMetadata(pending) }
        }

        logStageStart(id, InstallStage.PREPARING)
        listener(id, InstallStage.PREPARING, onProgress)(1f, 0, 0, 0, 0)
        logStageEnd(id, InstallStage.PREPARING)

        // VERSION_JSON
        logStageStart(id, InstallStage.VERSION_JSON)
        val jsonFile = downloadVersionJson(version, force?.forceVersionJson == true, onProgress)
        if (jsonFile.isFailure) {
            return@withContext fail(id, InstallStage.VERSION_JSON, pending, jsonFile.exceptionOrNull())
        }
        val json = try {
            JSONObject(jsonFile.getOrThrow().readText())
        } catch (e: Exception) {
            return@withContext fail(id, InstallStage.VERSION_JSON, pending, e)
        }
        logStageEnd(id, InstallStage.VERSION_JSON)

        // LIBRARIES
        logStageStart(id, InstallStage.LIBRARIES)
        val libraries = libraryInstaller.install(
            json,
            force?.libraryPaths ?: emptySet(),
            listener(id, InstallStage.LIBRARIES, onProgress)
        )
        if (libraries.isFailure) {
            return@withContext fail(id, InstallStage.LIBRARIES, pending, libraries.exceptionOrNull())
        }
        logStageEnd(id, InstallStage.LIBRARIES)

        // ASSET_INDEX
        logStageStart(id, InstallStage.ASSET_INDEX)
        val index = assetInstaller.downloadIndex(
            json,
            listener(id, InstallStage.ASSET_INDEX, onProgress)
        )
        if (index.isFailure) {
            return@withContext fail(id, InstallStage.ASSET_INDEX, pending, index.exceptionOrNull())
        }
        logStageEnd(id, InstallStage.ASSET_INDEX)

        // ASSETS
        logStageStart(id, InstallStage.ASSETS)
        val assets = assetInstaller.downloadObjects(
            index.getOrThrow(),
            force?.assetHashes ?: emptySet(),
            listener(id, InstallStage.ASSETS, onProgress)
        )
        if (assets.isFailure) {
            return@withContext fail(id, InstallStage.ASSETS, pending, assets.exceptionOrNull())
        }
        logStageEnd(id, InstallStage.ASSETS)

        // LOGGING_CONFIG
        logStageStart(id, InstallStage.LOGGING_CONFIG)
        val logging = installLoggingConfig(id, json, force?.forceLoggingConfig == true, onProgress)
        if (logging.isFailure) {
            return@withContext fail(id, InstallStage.LOGGING_CONFIG, pending, logging.exceptionOrNull())
        }
        logStageEnd(id, InstallStage.LOGGING_CONFIG)

        // VERIFICATION
        logStageStart(id, InstallStage.VERIFICATION)
        val verificationListener = listener(id, InstallStage.VERIFICATION, onProgress)
        verificationListener(null, 0, 0, 0, 0)
        val report = verificationService.scan(id)
        if (!report.ok) {
            logStageFailure(id, InstallStage.VERIFICATION, IllegalStateException("Verification failed"))
            runCatching { storage.writeMetadata(pending.copy(state = InstallState.CORRUPTED)) }
            runCatching { onFilesChanged?.invoke(id) }
            onProgress(InstallProgress(id, InstallStage.VERIFICATION, error = "Verification failed"))
            return@withContext Result.failure(IllegalStateException("Verification failed"))
        }
        logStageEnd(id, InstallStage.VERIFICATION)

        // COMPLETE
        logStageStart(id, InstallStage.COMPLETE)
        val installed = pending.copy(state = InstallState.INSTALLED)
        runCatching { storage.writeMetadata(installed) }
        verificationListener(1f, report.totalLibraries + report.totalAssets, 0, 0, 0)
        runCatching { onFilesChanged?.invoke(id) }
        onProgress(InstallProgress(id, InstallStage.COMPLETE, 1f, 0, 0, 0, 0))
        logStageEnd(id, InstallStage.COMPLETE)
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
            (version.sha1 == null || runCatching { HashUtils.sha1(destination) }.getOrNull() == version.sha1)
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
            (ref.sha1 == null || runCatching { HashUtils.sha1(destination) }.getOrNull() == ref.sha1)
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

    private suspend fun fail(
        id: String,
        stage: InstallStage,
        pending: InstalledVersionMetadata,
        cause: Throwable?,
    ): Result<InstalledVersionMetadata> {
        logStageFailure(id, stage, cause)
        runCatching { storage.writeMetadata(pending.copy(state = InstallState.FAILED)) }
        return Result.failure(IllegalStateException(cause?.message ?: "Installation failed"))
    }

    /** Structured install-stage log: one line before the stage runs. */
    private suspend fun logStageStart(id: String, stage: InstallStage) {
        logs?.writeLine("Install: version=$id stage=${stage.name} started")
    }

    /** Structured install-stage log: one line after the stage completes. */
    private suspend fun logStageEnd(id: String, stage: InstallStage) {
        logs?.writeLine("Install: version=$id stage=${stage.name} completed")
    }

    /** Structured install-stage log: stage failed with exception details. */
    private suspend fun logStageFailure(id: String, stage: InstallStage, cause: Throwable?) {
        logs?.writeLine(
            "Install failed: version=$id stage=${stage.name} " +
                "exception=${cause?.javaClass?.name ?: "unknown"} " +
                "message=${cause?.message ?: "no message"}"
        )
    }

    private data class RepairPlan(
        val forceVersionJson: Boolean,
        val forceLoggingConfig: Boolean,
        val libraryPaths: Set<String>,
        val assetHashes: Set<String>
    )
}