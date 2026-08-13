package com.lumocraft.app.data.native

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Outcome of one extraction run. */
data class ExtractionResult(
    val extractedFiles: Int,
    val skippedJars: Int,
    val duplicatesRemoved: Int,
    val nativeFiles: List<NativeFile>
)

/** One extracted native binary. */
data class NativeFile(
    val name: String,
    val size: Long,
    val sourceJar: String
)

/** Per-jar extraction record inside the stamp. */
data class JarStamp(
    val jarPath: String,
    val jarSize: Long,
    val files: List<NativeFile>
)

/**
 * Incremental, cached native extraction.
 *
 * Every extraction writes a stamp file next to the natives folder
 * (`.stamp-<arch>.json`) describing which files each jar contributed
 * with their sizes. On the next run each jar is skipped when its stamp
 * record exists and every recorded file is still present with the same
 * size — no hashing, no re-extraction. Multi-arch LWJGL jars are read
 * once per architecture; flat jars (JNA, Netty) contribute their natives
 * to every architecture. Files are deduplicated by target name across
 * jars, keeping the first contributor.
 */
class NativeExtractionService(private val storage: StorageManager) {

    fun baseDirectory(versionId: String): File =
        File(storage.versionDirectory(versionId), NATIVES_DIR)

    fun archDirectory(versionId: String, arch: RuntimeArchitecture): File =
        File(baseDirectory(versionId), arch.directoryName)

    fun stampFile(versionId: String, arch: RuntimeArchitecture): File =
        File(baseDirectory(versionId), ".stamp-${arch.directoryName}.json")

    suspend fun extract(
        versionId: String,
        arch: RuntimeArchitecture,
        sources: List<NativeJarSource>,
    ): Result<ExtractionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = archDirectory(versionId, arch)
            targetDir.mkdirs()
            val stamp = loadStamp(versionId, arch)
            val seenNames = hashMapOf<String, String>()
            val jarStamps = stamp.jars.toMutableList()
            var extracted = 0
            var skippedJars = 0
            var duplicates = 0
            val allFiles = mutableListOf<NativeFile>()

            for (source in sources) {
                val recorded = jarStamps.firstOrNull { it.jarPath == source.libraryPath }
                if (recorded != null && recorded.jarSize == source.size &&
                    recorded.files.all { entry ->
                        val file = File(targetDir, entry.name)
                        file.isFile && file.length() == entry.size
                    }
                ) {
                    skippedJars++
                    recorded.files.forEach { entry ->
                        seenNames.putIfAbsent(entry.name, entry.sourceJar)
                        allFiles += entry
                    }
                    continue
                }

                val jarFiles = extractJarEntries(source.file, targetDir, arch, seenNames)
                duplicates += jarFiles.count { it.wasDuplicate }
                extracted += jarFiles.count { !it.wasDuplicate }
                val kept = jarFiles.filter { !it.wasDuplicate }
                    .map { NativeFile(it.name, it.size, sourceJar = source.libraryPath) }
                allFiles += kept

                jarStamps.removeAll { it.jarPath == source.libraryPath }
                jarStamps += JarStamp(
                    jarPath = source.libraryPath,
                    jarSize = source.size,
                    files = kept
                )
            }

            writeStamp(versionId, arch, jarStamps, duplicates)
            ExtractionResult(
                extractedFiles = extracted,
                skippedJars = skippedJars,
                duplicatesRemoved = duplicates,
                nativeFiles = allFiles
            )
        }
    }

    suspend fun loadStamp(versionId: String, arch: RuntimeArchitecture): NativeStamp =
        withContext(Dispatchers.IO) {
            val file = stampFile(versionId, arch)
            if (!file.isFile) return@withContext NativeStamp(arch.directoryName, emptyList(), 0)
            val json = runCatching { JSONObject(file.readText()) }.getOrNull()
                ?: return@withContext NativeStamp(arch.directoryName, emptyList(), 0)
            val jars = json.optJSONArray("jars") ?: JSONArray()
            val jarStamps = buildList {
                for (i in 0 until jars.length()) {
                    val jar = jars.optJSONObject(i) ?: continue
                    val files = jar.optJSONArray("files") ?: JSONArray()
                    val entries = buildList {
                        for (j in 0 until files.length()) {
                            val entry = files.optJSONObject(j) ?: continue
                            add(
                                NativeFile(
                                    name = entry.optString("name"),
                                    size = entry.optLong("size", 0L),
                                    sourceJar = entry.optString("source", "")
                                )
                            )
                        }
                    }
                    add(
                        JarStamp(
                            jarPath = jar.optString("path"),
                            jarSize = jar.optLong("jarSize", 0L),
                            files = entries
                        )
                    )
                }
            }
            NativeStamp(
                arch = json.optString("arch", arch.directoryName),
                jars = jarStamps,
                duplicatesRemoved = json.optInt("duplicatesRemoved", 0)
            )
        }

    private suspend fun writeStamp(
        versionId: String,
        arch: RuntimeArchitecture,
        jars: List<JarStamp>,
        duplicatesRemoved: Int,
    ) {
        val json = JSONObject().apply {
            put("arch", arch.directoryName)
            put("duplicatesRemoved", duplicatesRemoved)
            put("extractedAt", System.currentTimeMillis())
            put("jars", JSONArray().apply {
                jars.forEach { jar ->
                    put(
                        JSONObject().apply {
                            put("path", jar.jarPath)
                            put("jarSize", jar.jarSize)
                            put("files", JSONArray().apply {
                                jar.files.forEach { entry ->
                                    put(
                                        JSONObject().apply {
                                            put("name", entry.name)
                                            put("size", entry.size)
                                            put("source", entry.sourceJar)
                                        }
                                    )
                                }
                            })
                        }
                    )
                }
            })
        }
        stampFile(versionId, arch).writeText(json.toString())
    }

    /**
     * Extracts the entries belonging to [arch] from [jar] into [targetDir],
     * skipping names already claimed by an earlier jar.
     */
    private fun extractJarEntries(
        jar: File,
        targetDir: File,
        arch: RuntimeArchitecture,
        seenNames: MutableMap<String, String>,
    ): List<ExtractedEntry> {
        val result = mutableListOf<ExtractedEntry>()
        ZipFile(jar).use { zip ->
            val entries = zip.entries().toList()
            val multiArch = entries.any { entry ->
                ARCH_DIRS.any { "/$it/" in entry.name }
            }
            for (entry in entries) {
                if (entry.isDirectory || !isNativeFile(entry.name)) continue
                if (multiArch && !archMatches(entry.name, arch)) continue
                val fileName = entry.name.substringAfterLast('/')
                val owner = seenNames[fileName]
                if (owner != null) {
                    result += ExtractedEntry(fileName, entry.size, wasDuplicate = true)
                    continue
                }
                val target = File(targetDir, fileName)
                if (target.isFile && target.length() == entry.size) {
                    seenNames[fileName] = jar.name
                    result += ExtractedEntry(fileName, entry.size, wasDuplicate = false)
                    continue
                }
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                seenNames[fileName] = jar.name
                result += ExtractedEntry(fileName, entry.size, wasDuplicate = false)
            }
        }
        return result
    }

    private fun isNativeFile(name: String): Boolean =
        !name.startsWith("META-INF") && NATIVE_EXTENSIONS.any { name.endsWith(it) }

    private fun archMatches(name: String, arch: RuntimeArchitecture): Boolean {
        val dir = name.substringBeforeLast('/', "").substringAfterLast('/')
        return when (dir) {
            "arm64" -> arch == RuntimeArchitecture.ARM64_V8A
            "arm32" -> arch == RuntimeArchitecture.ARMEABI_V7A
            "x86_64" -> arch == RuntimeArchitecture.X86_64
            else -> arch == RuntimeArchitecture.X86_64
        }
    }

    private data class ExtractedEntry(
        val name: String,
        val size: Long,
        val wasDuplicate: Boolean
    )

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        const val NATIVES_DIR = "natives"
        val NATIVE_EXTENSIONS = listOf(".so", ".dylib", ".dll", ".jnilib")
        val ARCH_DIRS = setOf("arm64", "arm32", "x86_64")
    }
}

/** Arch mapping shared by extraction and verification. */
val RuntimeArchitecture.directoryName: String
    get() = when (this) {
        RuntimeArchitecture.ARM64_V8A -> "arm64"
        RuntimeArchitecture.ARMEABI_V7A -> "arm32"
        RuntimeArchitecture.X86_64 -> "x86_64"
    }

/** Parsed stamp document for one version + arch. */
data class NativeStamp(
    val arch: String,
    val jars: List<JarStamp>,
    val duplicatesRemoved: Int
)