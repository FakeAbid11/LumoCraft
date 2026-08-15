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
 * One downloadable archive that makes up (part of) a runtime. Simple
 * runtimes are a single part; split runtimes (e.g. the Bionic JRE 21) are
 * an architecture-independent "universal" part plus a per-ABI part, all
 * extracted into the same runtime directory.
 *
 * @param sha256 lowercase hex SHA-256 the downloaded archive must match, or
 *   null to skip integrity verification for this part.
 */
data class RuntimeArchivePart(
    val url: String,
    val sha256: String?,
)

/**
 * Downloads, verifies and extracts a Java runtime archive into
 * `<launcherRoot>/runtime/<id>/`.
 *
 * - downloads via the shared [Downloader] (retry + backoff)
 * - verifies SHA-256 of each archive part before extraction
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
        parts: List<RuntimeArchivePart>,
        onProgress: suspend (RuntimeProgress) -> Unit,
    ): Result<RuntimeInfo> = withContext(Dispatchers.IO) {
        try {
            require(parts.isNotEmpty()) { "Runtime must have at least one archive part" }
            val runtimeDir = storage.runtimeDirectoryFor(runtimeId)

            onProgress(RuntimeProgress(runtimeId, RuntimeStage.PREPARING))
            runtimeDir.mkdirs()

            // Download → verify → extract each part into the same directory.
            // Progress is scaled so several parts still report a single 0..1
            // download bar and a single 0..1 extraction bar.
            parts.forEachIndexed { index, part ->
                // Android runtimes are distributed as .tar.xz; the extractor
                // selects the decompressor from this extension.
                val archiveFile = File(storage.runtimeDirectory(), "$runtimeId-part$index.tar.xz")
                val base = index.toFloat()
                val span = parts.size.toFloat()

                onProgress(RuntimeProgress(runtimeId, RuntimeStage.DOWNLOADING, base / span))
                val downloadResult = downloader.download(part.url, archiveFile) { fraction ->
                    fraction?.let { f ->
                        onProgress(
                            RuntimeProgress(
                                runtimeId, RuntimeStage.DOWNLOADING, (base + f) / span
                            )
                        )
                    }
                }
                if (downloadResult.isFailure) {
                    return@withContext Result.failure(
                        downloadResult.exceptionOrNull() ?: IOException("Runtime download failed")
                    )
                }

                if (part.sha256 != null) {
                    val actual = HashUtils.sha256(archiveFile)
                    if (!actual.equals(part.sha256, ignoreCase = true)) {
                        archiveFile.delete()
                        return@withContext Result.failure(
                            IOException("Runtime archive SHA-256 mismatch")
                        )
                    }
                }

                onProgress(RuntimeProgress(runtimeId, RuntimeStage.EXTRACTING, base / span))
                val extractResult = extractor.extract(archiveFile, runtimeDir) { fraction ->
                    onProgress(
                        RuntimeProgress(
                            runtimeId, RuntimeStage.EXTRACTING, (base + fraction) / span
                        )
                    )
                }
                if (extractResult.isFailure) {
                    archiveFile.delete()
                    return@withContext Result.failure(
                        extractResult.exceptionOrNull() ?: IOException("Runtime extraction failed")
                    )
                }
                archiveFile.delete()
            }

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
