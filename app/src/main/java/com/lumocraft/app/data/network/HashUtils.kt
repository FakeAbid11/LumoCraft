package com.lumocraft.app.data.network

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Streaming, memory-efficient hashing helpers.
 * Files are read in small buffers so large assets never load into RAM.
 */
object HashUtils {

    private const val BUFFER_SIZE = 16 * 1024
    private const val HEX_CHARS = "0123456789abcdef"

    /** SHA-1 digest of a file, as lowercase hex. Runs on the IO dispatcher. */
    suspend fun sha1(file: File): String = sha1(file, null)

    /**
     * SHA-1 digest of a file reusing [buffer] (or allocating a fresh one
     * when null) — used with a pooled buffer to avoid per-call
     * allocations on hot verification paths.
     */
    suspend fun sha1(file: File, buffer: ByteArray?): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = buffer ?: ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                digest.update(buf, 0, read)
            }
        }
        digest.digest().toHex()
    }

    /** SHA-256 digest of a file, as lowercase hex. Runs on the IO dispatcher. */
    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    }

    /** Byte array -> lowercase hex, allocation-free per byte. */
    fun ByteArray.toHex(): String {
        val result = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            result.append(HEX_CHARS[value ushr 4])
            result.append(HEX_CHARS[value and 0x0F])
        }
        return result.toString()
    }
}
