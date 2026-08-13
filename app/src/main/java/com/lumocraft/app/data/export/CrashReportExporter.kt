package com.lumocraft.app.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.lumocraft.app.BuildConfig
import com.lumocraft.app.data.launch.LauncherLogRepository
import com.lumocraft.app.data.preferences.VersionPreference
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.loader.LoaderRepository
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.performance.PerformanceManager
import com.lumocraft.app.domain.runtime.RuntimeRepository
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What a [CrashReportExporter] produced and where it can be found. */
data class ExportResult(
    val displayName: String,
    /** Content URI on Android 10+ (public Downloads folder). */
    val uri: Uri? = null,
    /** File path on Android 8/9 (app-specific Downloads; share via FileProvider). */
    val file: File? = null,
)

/** What gets packaged into the export archive. */
enum class ExportKind {
    /** Launcher + crash logs only. */
    LOGS,

    /** Logs plus a machine-readable diagnostics snapshot. */
    DIAGNOSTICS,
}

/**
 * Packages launcher logs, crash reports and (optionally) a diagnostics
 * snapshot into a single ZIP for sharing or bug reports.
 *
 * Personal data handling: account passwords are never touched and
 * usernames are [redact]ed from log contents before export; the
 * diagnostics snapshot only carries hardware/software facts.
 *
 * Output: on Android 10+ the archive lands in the public
 * `Downloads/LumoCraft/` folder via [MediaStore]; on Android 8/9 it is
 * written to the app-specific external Downloads directory and returned
 * as a [File] for the caller to share through the FileProvider.
 */
class CrashReportExporter(
    private val context: Context,
    private val storage: StorageManager,
    private val logs: LauncherLogRepository,
    private val performance: PerformanceManager,
    private val runtimeRepository: RuntimeRepository,
    private val versionPreference: VersionPreference,
    private val loaderRepository: LoaderRepository,
    private val nativeRuntimeManager: NativeRuntimeManager,
) {

    /**
     * Builds and stores the archive. [redact] receives every exported
     * text line and should replace sensitive tokens (usernames etc.)
     * with placeholders.
     */
    suspend fun export(
        kind: ExportKind,
        redact: (String) -> String = { it },
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = timestamp()
            val name = when (kind) {
                ExportKind.LOGS -> "lumocraft-logs-$stamp.zip"
                ExportKind.DIAGNOSTICS -> "lumocraft-diagnostics-$stamp.zip"
            }

            val staging = File(context.cacheDir, "export/$stamp").apply { mkdirs() }
            try {
                prepare(staging, kind, redact)
                val zipBytes = zipToBytes(staging)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = writeToDownloads(name, zipBytes)
                    ExportResult(displayName = name, uri = uri)
                } else {
                    val dir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "LumoCraft"
                    ).apply { mkdirs() }
                    val file = File(dir, name)
                    FileOutputStream(file).use { it.write(zipBytes) }
                    ExportResult(displayName = name, file = file)
                }
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    private suspend fun prepare(staging: File, kind: ExportKind, redact: (String) -> String) {
        val logsDir = File(staging, "logs").apply { mkdirs() }
        for (logFile in logs.listLogFiles()) {
            redactTo(logFile, File(logsDir, logFile.name), redact)
        }
        val crashesDir = File(staging, "crashes").apply { mkdirs() }
        crashFiles().forEach { source ->
            redactTo(source, File(crashesDir, source.name), redact)
        }
        if (kind == ExportKind.DIAGNOSTICS) {
            File(staging, "diagnostics.json").writeText(buildDiagnostics().toString(2))
        }
    }

    /** Copies a text file line-by-line through the redaction filter. */
    private fun redactTo(source: File, target: File, redact: (String) -> String) {
        if (!source.isFile) return
        target.bufferedWriter(Charsets.UTF_8).use { writer ->
            source.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    writer.write(redact(line))
                    writer.newLine()
                }
            }
        }
    }

    private fun crashFiles(): List<File> {
        val dir = File(storage.launcherRoot(), "logs/crashes")
        return dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Hardware/software facts. No account data, no paths outside the launcher. */
    private suspend fun buildDiagnostics(): JSONObject {
        val app = JSONObject()
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("versionDisplay", com.lumocraft.app.core.version.VersionManager.currentDisplayName())

        val device = performance.deviceProfile()
        val hardware = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("androidSdk", device.androidSdk)
            .put("arch", device.architecture.abi)
            .put("cpuCores", device.cpuCores)
            .put("totalRamMB", device.totalRamMB)
            .put("tier", device.tier.name)
            .put("lowRam", device.lowRamDevice)

        val runtime = runtimeRepository.getDefaultRuntime()
        val selectedVersion = versionPreference.loadSelectedVersionId()
        val loader = selectedVersion?.let { loaderRepository.resolveActiveLoader(it) }

        val launch = JSONObject()
            .put("selectedVersion", selectedVersion ?: JSONObject.NULL)
            .put(
                "activeLoader",
                loader?.let {
                    JSONObject()
                        .put("type", it.metadata.type.id)
                        .put("minecraftVersion", it.metadata.minecraftVersion)
                        .put("loaderVersion", it.metadata.loaderVersion)
                } ?: JSONObject.NULL
            )
            .put(
                "runtime",
                runtime?.let {
                    JSONObject()
                        .put("id", it.id)
                        .put("version", it.version)
                        .put("vendor", it.vendor)
                        .put("arch", it.architecture.abi)
                        .put("status", it.status.name)
                } ?: JSONObject.NULL
            )
            .put("nativeArch", nativeRuntimeManager.architecture().abi)

        return JSONObject()
            .put("app", app)
            .put("hardware", hardware)
            .put("launch", launch)
            .put("generatedAt", System.currentTimeMillis())
    }

    private fun zipToBytes(staging: File): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            staging.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(staging).path.replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDownloads(name: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/LumoCraft")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create Downloads entry")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open Downloads stream")
        } finally {
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        return uri
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
