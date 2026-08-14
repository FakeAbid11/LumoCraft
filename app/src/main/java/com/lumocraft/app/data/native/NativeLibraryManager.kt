package com.lumocraft.app.data.native

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A native-carrying jar on disk, plus its Mojang-relative path. */
data class NativeJarSource(
    val libraryPath: String,
    val file: File,
    val size: Long
)

/**
 * Locates the native jars (libraries with a natives classifier) a
 * version needs, walking the `inheritsFrom` chain like the classpath
 * builder. Sources are deduplicated by path; the same jar is never
 * listed twice.
 */
class NativeLibraryManager(private val storage: StorageManager) {

    suspend fun locate(versionId: String): Result<List<NativeJarSource>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = linkedMapOf<String, NativeJarSource>()
                var current = versionId
                val seen = mutableSetOf<String>()
                while (true) {
                    if (!seen.add(current)) break
                    val jsonFile = storage.versionJsonFile(current)
                    val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull()
                        ?: break
                    for (ref in VersionJson.libraries(json)) {
                        // Only true native classifiers carry JNI binaries
                        // ("natives" / "natives-linux" / "natives-linux-arm64").
                        // Other classifiers (sources, javadoc, …) must never
                        // be treated as native sources.
                        val classifier = ref.classifier ?: continue
                        if (!classifier.contains("natives")) continue
                        val file = storage.libraryFile(ref.path)
                        if (file.isFile) {
                            result.putIfAbsent(
                                ref.path,
                                NativeJarSource(
                                    libraryPath = ref.path,
                                    file = file,
                                    size = file.length()
                                )
                            )
                        }
                    }
                    val parent = json.optString("inheritsFrom").takeIf { it.isNotEmpty() }
                        ?: break
                    current = parent
                }
                result.values.toList()
            }
        }
}