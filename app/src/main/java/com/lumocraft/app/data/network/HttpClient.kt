package com.lumocraft.app.data.network

import com.lumocraft.app.core.config.AppConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Thrown when the server responds with a non-2xx status code. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * Minimal HTTP client built on the platform [HttpURLConnection] —
 * no third-party networking library needed.
 *
 * All calls run on [Dispatchers.IO], honour coroutine cancellation and map
 * failures into [Result]. [download] supports progress callbacks;
 * [downloadResumable] resumes partial files via HTTP Range requests and
 * keeps the partial file on failure so interrupted downloads can resume.
 */
class HttpClient(
    private val connectTimeoutMs: Int = AppConfig.HTTP_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = AppConfig.HTTP_READ_TIMEOUT_MS,
) {

    /** Fetches a URL body as text. */
    suspend fun get(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(url)
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.rethrowCancellation()
    }

    /**
     * Downloads a URL to [destination]. [onProgress] receives the fraction
     * 0f..1f, or null when the server reports no content length.
     * The partial file is deleted when the download fails.
     */
    suspend fun download(
        url: String,
        destination: File,
        onProgress: suspend (Float?) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            destination.delete()
            downloadTo(url, destination, resumeFrom = 0, onProgress)
        }.onFailure { _ ->
            destination.delete()
        }.rethrowCancellation()
    }

    /**
     * Downloads a URL to [destination], resuming from the existing
     * partial file via a Range request. The partial file is kept on
     * failure so a later call can continue. When the server does not
     * support ranges (HTTP 200 instead of 206) or the range is
     * unsatisfiable (416), the partial file is discarded and the
     * download restarts from scratch.
     */
    suspend fun downloadResumable(
        url: String,
        destination: File,
        onProgress: suspend (Float?) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            val resumeFrom = destination.length()
            try {
                downloadTo(url, destination, resumeFrom, onProgress)
            } catch (e: ResumeUnsupported) {
                // Server ignored the Range header: start over.
                destination.delete()
                downloadTo(url, destination, 0, onProgress)
            } catch (e: HttpStatusException) {
                if (e.code != HTTP_NOT_SATISFIABLE) throw e
                // Partial file is complete or longer than the remote file.
                destination.delete()
                downloadTo(url, destination, 0, onProgress)
            }
        }.rethrowCancellation()
    }

    private suspend fun downloadTo(
        url: String,
        destination: File,
        resumeFrom: Long,
        onProgress: suspend (Float?) -> Unit,
    ): File {
        val connection = openConnection(url, resumeFrom.takeIf { it > 0 })
        try {
            val code = connection.responseCode
            if (resumeFrom > 0 && code == HttpURLConnection.HTTP_OK) {
                throw ResumeUnsupported()
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val absoluteTotal = total?.plus(resumeFrom)
            connection.inputStream.use { input ->
                FileOutputStream(destination, resumeFrom > 0).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = resumeFrom
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (absoluteTotal != null) {
                            onProgress(downloaded.toFloat() / absoluteTotal)
                        } else {
                            onProgress(null)
                        }
                    }
                }
            }
            destination
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, rangeStart: Long? = null): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", AppConfig.USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            if (rangeStart != null) {
                setRequestProperty("Range", "bytes=$rangeStart-")
            }
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw HttpStatusException(code)
        }
        return connection
    }

    private class ResumeUnsupported : IOException("Server does not support Range requests")

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 16 * 1024
        const val HTTP_NOT_SATISFIABLE = 416
    }
}