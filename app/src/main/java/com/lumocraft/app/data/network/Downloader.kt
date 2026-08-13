package com.lumocraft.app.data.network

import com.lumocraft.app.core.config.AppConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Reusable downloader with retry and backoff on top of [HttpClient].
 *
 * Retries only transient failures (I/O errors and 5xx responses) with
 * exponential backoff; permanent errors (4xx, malformed URL) fail fast.
 * Cancellation propagates immediately between attempts and chunk reads.
 *
 * Later phases reuse this for assets, libraries and the Java runtime.
 */
class Downloader(
    private val client: HttpClient,
    private val maxAttempts: Int = AppConfig.DOWNLOAD_MAX_ATTEMPTS,
    private val retryBackoffMs: Long = AppConfig.DOWNLOAD_RETRY_BACKOFF_MS,
) {

    suspend fun download(
        url: String,
        destination: File,
        onProgress: suspend (Float?) -> Unit = {},
    ): Result<File> {
        var lastFailure: Throwable? = null
        repeat(maxAttempts) { attempt ->
            coroutineContext.ensureActive()
            val result = client.download(url, destination, onProgress)
            if (result.isSuccess) return result

            lastFailure = result.exceptionOrNull()
            if (!isRetryable(lastFailure) || attempt == maxAttempts - 1) {
                return result
            }
            delay(retryBackoffMs * (1L shl attempt))
        }
        return Result.failure(lastFailure ?: IOException("Download failed"))
    }

    /**
     * Downloads to a temp file, verifies size and SHA-1, then atomically
     * renames into place. Already existing files that verify are skipped.
     *
     * @param force redownloads even when the existing file verifies.
     * @param rehashExisting when false, an existing file that matches the
     * expected size is trusted without hashing (used for large asset files).
     */
    suspend fun downloadVerified(
        url: String,
        destination: File,
        expectedSha1: String? = null,
        expectedSize: Long? = null,
        force: Boolean = false,
        rehashExisting: Boolean = true,
        onProgress: suspend (Float?) -> Unit = {},
    ): Result<File> {
        if (!force && destination.isFile && sizeMatches(destination, expectedSize)) {
            if (expectedSha1 == null || !rehashExisting || sha1Matches(destination, expectedSha1)) {
                return Result.success(destination)
            }
        }
        val temp = File(destination.parentFile, ".${destination.name}.part")
        val result = download(url, temp, onProgress)
        if (result.isFailure) return result

        val file = result.getOrThrow()
        if (expectedSha1 != null && HashUtils.sha1(file) != expectedSha1) {
            file.delete()
            return Result.failure(IOException("SHA-1 mismatch for $url"))
        }
        if (expectedSize != null && file.length() != expectedSize) {
            file.delete()
            return Result.failure(IOException("Size mismatch for $url"))
        }
        file.renameTo(destination)
        return Result.success(destination)
    }

    private fun sizeMatches(file: File, expectedSize: Long?): Boolean =
        expectedSize == null || file.length() == expectedSize

    private suspend fun sha1Matches(file: File, expectedSha1: String?): Boolean =
        expectedSha1 == null || HashUtils.sha1(file) == expectedSha1

    private fun isRetryable(error: Throwable?): Boolean = when (error) {
        is HttpStatusException -> error.code >= 500
        is IOException -> true
        else -> false
    }
}
