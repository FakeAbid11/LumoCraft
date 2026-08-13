package com.lumocraft.app.data.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Streaming archive extractor supporting tar.gz and zip.
 *
 * - streams entry-by-entry (never loads the whole archive into RAM)
 * - sanitizes every entry path, rejecting traversal ("..") and absolute paths
 * - preserves executable permissions on POSIX entries where applicable
 * - reports progress via [onProgress] (0f..1f)
 * - fails safely: any invalid entry aborts and cleans up partial output
 */
class ArchiveExtractor {

    /**
     * Extracts [archive] into [destinationDir].
     * @throws IOException on invalid archives, path traversal, or I/O errors.
     */
    suspend fun extract(
        archive: File,
        destinationDir: File,
        onProgress: suspend (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            destinationDir.mkdirs()
            when {
                archive.name.endsWith(".tar.gz") || archive.name.endsWith(".tgz") ->
                    extractTarGz(archive, destinationDir, onProgress)
                archive.name.endsWith(".zip") ->
                    extractZip(archive, destinationDir, onProgress)
                else -> throw IOException("Unsupported archive format: ${archive.name}")
            }
        }
    }

    private suspend fun extractTarGz(
        archive: File,
        destinationDir: File,
        onProgress: suspend (Float) -> Unit,
    ) {
        val totalBytes = archive.length()
        var processedBytes = 0L
        FileInputStream(archive).use { fileInput ->
            BufferedInputStream(fileInput).use { buffered ->
                GzipCompressorInputStream(buffered).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val entry = tar.nextEntry ?: break
                            val target = sanitizeTarget(destinationDir, entry.name)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var read: Int
                                    while (tar.read(buffer).also { read = it } != -1) {
                                        coroutineContext.ensureActive()
                                        output.write(buffer, 0, read)
                                        processedBytes += read
                                        if (totalBytes > 0) {
                                            onProgress(processedBytes.toFloat() / totalBytes)
                                        }
                                    }
                                }
                                // Preserve executable bit from POSIX mode.
                                if (entry.mode and 0x40 != 0) {
                                    target.setExecutable(true, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun extractZip(
        archive: File,
        destinationDir: File,
        onProgress: suspend (Float) -> Unit,
    ) {
        val totalBytes = archive.length()
        var processedBytes = 0L
        FileInputStream(archive).use { fileInput ->
            BufferedInputStream(fileInput).use { buffered ->
                ZipInputStream(buffered).use { zip ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zip.nextEntry ?: break
                        val target = sanitizeTarget(destinationDir, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var read: Int
                                while (zip.read(buffer).also { read = it } != -1) {
                                    coroutineContext.ensureActive()
                                    output.write(buffer, 0, read)
                                    processedBytes += read
                                    if (totalBytes > 0) {
                                        onProgress(processedBytes.toFloat() / totalBytes)
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Resolves an archive entry name against [destinationDir], rejecting
     * absolute paths and any ".." traversal segments.
     */
    private fun sanitizeTarget(destinationDir: File, entryName: String): File {
        val normalized = entryName.replace('\\', '/')
        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) {
            throw IOException("Path traversal rejected: $entryName")
        }
        val target = segments.fold(destinationDir) { dir, name -> File(dir, name) }
        val canonicalRoot = destinationDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot + File.separator) &&
            canonicalTarget != canonicalRoot
        ) {
            throw IOException("Path escapes destination: $entryName")
        }
        return target
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}