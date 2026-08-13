package com.lumocraft.app.data.launch

import com.lumocraft.app.data.storage.StorageManager
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Session logs under <launcherRoot>/logs/. Every launch writes a
 * timestamped file (launcher-<ts>.log) that also feeds a replaying live
 * stream for the console UI. Line writes are buffered and flushed per
 * line so the game output appears in real time.
 */
class LauncherLogRepository(private val storage: StorageManager) {

    private val _lines = MutableSharedFlow<String>(
        replay = REPLAY_LINES,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val lines: Flow<String> = _lines.asSharedFlow()

    private val recent = ArrayDeque<String>()
    private var sessionFile: File? = null
    private var writer: BufferedWriter? = null

    fun logsDirectory(): File = storage.logsDirectory()

    /** Opens a new timestamped session file and returns it. */
    fun startSession(prefix: String = SESSION_PREFIX): File {
        endSession()
        val file = File(logsDirectory().apply { mkdirs() }, "$prefix-${timestamp()}.log")
        sessionFile = file
        writer = file.bufferedWriter(Charsets.UTF_8, BUFFER_SIZE)
        return file
    }

    /** Appends one line to the session file and the live stream. */
    suspend fun writeLine(line: String) {
        withContext(Dispatchers.IO) {
            writer?.let {
                it.append(line)
                it.newLine()
                it.flush()
            }
        }
        recent.addLast(line)
        while (recent.size > MAX_RECENT) recent.removeFirst()
        _lines.emit(line)
    }

    /** Closes the session file; the file stays on disk. */
    fun endSession() {
        writer?.close()
        writer = null
    }

    fun currentSessionFile(): File? = sessionFile

    fun recentLines(): List<String> = recent.toList()

    fun listLogFiles(): List<File> =
        logsDirectory().listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Copies every session log into [destination] (file or directory). */
    suspend fun copyLogs(destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            for (file in listLogFiles()) {
                val target = if (destination.isDirectory) {
                    File(destination, file.name)
                } else {
                    destination
                }
                file.copyTo(target, overwrite = true)
            }
        }
    }

    /** Deletes all session logs. */
    suspend fun clearLogs(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { listLogFiles().forEach { it.delete() } }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private companion object {
        const val SESSION_PREFIX = "launcher"
        const val BUFFER_SIZE = 16 * 1024
        const val REPLAY_LINES = 1000
        const val EXTRA_BUFFER = 256
        const val MAX_RECENT = 500
    }
}