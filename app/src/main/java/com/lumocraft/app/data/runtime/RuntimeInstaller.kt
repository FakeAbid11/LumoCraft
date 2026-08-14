package com.lumocraft.app.data.runtime

import android.util.Log
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeProgress
import com.lumocraft.app.domain.runtime.RuntimeStage
import com.lumocraft.app.domain.runtime.RuntimeStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads, verifies and extracts a Java runtime archive into
 * `<launcherRoot>/runtime/<id>/`.
 *
 * - downloads via the shared [Downloader] (retry + backoff)
 * - verifies SHA-256 of the archive before extraction
 * - extracts safely via [ArchiveExtractor] (path traversal rejected)
 * - supports cancellation at every step
 */
class RuntimeInstaller(
    private val storage: StorageManager,
    private val downloader: Downloader,
    private val extractor: ArchiveExtractor,
) {

    suspend fun install(
        runtimeId: String,
        version: String,
        architecture: RuntimeArchitecture,
        vendor: String,
        archiveUrl: String,
        archiveSha256: String?,
        onProgress: suspend (RuntimeProgress) -> Unit,
    ): Result<RuntimeInfo> = withContext(Dispatchers.IO) {
        try {
            val runtimeDir = storage.runtimeDirectoryFor(runtimeId)
            val archiveFile = File(storage.runtimeDirectory(), "$runtimeId.tar.gz")

            onProgress(RuntimeProgress(runtimeId, RuntimeStage.PREPARING))
            runtimeDir.mkdirs()

            onProgress(RuntimeProgress(runtimeId, RuntimeStage.DOWNLOADING))
            val downloadResult = downloader.download(archiveUrl, archiveFile) { fraction ->
                fraction?.let { f ->
                    onProgress(RuntimeProgress(runtimeId, RuntimeStage.DOWNLOADING, f))
                }
            }
            if (downloadResult.isFailure) {
                return@withContext Result.failure(
                    downloadResult.exceptionOrNull() ?: IOException("Runtime download failed")
                )
            }

            if (archiveSha256 != null) {
                val actual = HashUtils.sha256(archiveFile)
                if (actual != archiveSha256) {
                    archiveFile.delete()
                    return@withContext Result.failure(
                        IOException("Runtime archive SHA-256 mismatch")
                    )
                }
            }

            onProgress(RuntimeProgress(runtimeId, RuntimeStage.EXTRACTING))
            val extractResult = extractor.extract(archiveFile, runtimeDir) { fraction ->
                onProgress(RuntimeProgress(runtimeId, RuntimeStage.EXTRACTING, fraction))
            }
            if (extractResult.isFailure) {
                archiveFile.delete()
                return@withContext Result.failure(
                    extractResult.exceptionOrNull() ?: IOException("Runtime extraction failed")
                )
            }
            archiveFile.delete()

            val jvmRoot = findJvmRoot(runtimeDir)

            // 1. Restore executable bits on every launcher binary: Android
            //    does not preserve POSIX exec metadata through extraction,
            //    so this must be done explicitly rather than relying on
            //    the TAR mode bits.
            RuntimePermissions.restoreExecutableBits(jvmRoot)

            // 2. Verify bin/java is actually executable. A runtime whose
            //    launcher cannot run must never be marked INSTALLED.
            val javaBinary = File(jvmRoot, "bin/java")
            if (!RuntimePermissions.canExecute(javaBinary)) {
                Log.e(
                    TAG,
                    "RUNTIME_EXECUTABLE_FAILED path=${javaBinary.absolutePath} " +
                        "canExecute=${javaBinary.canExecute()} result=installAborted"
                )
                return@withContext Result.failure(
                    IOException(
                        "Installed Java runtime is not executable: " +
                            javaBinary.absolutePath
                    )
                )
            }

            val releaseFile = File(jvmRoot, "release")
            val checksum = if (releaseFile.isFile) {
                HashUtils.sha256(releaseFile)
            } else {
                null
            }
            val info = RuntimeInfo(
                id = runtimeId,
                version = version,
                architecture = architecture,
                vendor = vendor,
                path = jvmRoot.absolutePath,
                installedAt = System.currentTimeMillis(),
                isDefault = false,
                status = RuntimeStatus.INSTALLED,
                checksum = checksum
            )
            onProgress(RuntimeProgress(runtimeId, RuntimeStage.COMPLETE, 1f))
            Result.success(info)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findJvmRoot(runtimeDir: File): File {
        val direct = File(runtimeDir, "bin/java")
        if (direct.isFile) return runtimeDir
        runtimeDir.listFiles()?.forEach { child ->
            if (child.isDirectory && File(child, "bin/java").isFile) {
                return child
            }
        }
        return runtimeDir
    }

    private companion object {
        const val TAG = "LumoCraft/RuntimeInstaller"
    }
}
