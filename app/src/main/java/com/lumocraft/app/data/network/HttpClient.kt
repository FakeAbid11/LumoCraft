package com.lumocraft.app.data.network

import com.lumocraft.app.core.config.AppConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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
 * failures into [Result]. Download supports progress callbacks.
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
            val connection = openConnection(url)
            try {
                val total = connection.contentLengthLong.takeIf { it > 0 }
                connection.inputStream.use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total != null) {
                                onProgress(downloaded.toFloat() / total)
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            destination
        }.onFailure { _ ->
            destination.delete()
        }.rethrowCancellation()
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", AppConfig.USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw HttpStatusException(code)
        }
        return connection
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 16 * 1024
    }
}
