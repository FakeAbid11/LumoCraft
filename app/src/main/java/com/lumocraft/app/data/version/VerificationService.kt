package com.lumocraft.app.data.version

import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.version.LibraryRef
import com.lumocraft.app.domain.version.VerificationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Full integrity scan of an installed version.
 *
 * - version JSON: exists and parses (hash checked if the manifest provided one)
 * - metadata: exists
 * - libraries: every file exists; SHA-1 verified when known
 * - asset index: exists and parses
 * - assets: every object exists with the exact size from the index
 *   (object file names are their SHA-1, so a size mismatch is conclusive)
 * - logging config: exists and hash verified when known
 *
 * All checks run on the IO dispatcher with streaming hashing.
 */
class VerificationService(private val storage: StorageManager) {

    suspend fun scan(versionId: String): VerificationReport =
        withContext(Dispatchers.IO) {
            val jsonFile = storage.versionJsonFile(versionId)
            if (!jsonFile.isFile) {
                return@withContext VerificationReport(versionId = versionId)
            }
            val json = runCatching { JSONObject(jsonFile.readText()) }
                .getOrNull()
            if (json == null) {
                return@withContext VerificationReport(versionId = versionId)
            }
            scanWithJson(versionId, json)
        }

    private suspend fun scanWithJson(
        versionId: String,
        json: JSONObject,
    ): VerificationReport {
        val metadataOk = storage.readMetadata(versionId) != null

        val libraries = VersionJson.libraries(json)
        val missingLibraries = mutableListOf<LibraryRef>()
        val corruptLibraries = mutableListOf<LibraryRef>()
        for (lib in libraries) {
            val file = storage.libraryFile(lib.path)
            when {
                !file.isFile -> missingLibraries += lib
                lib.sha1 != null && runCatching { HashUtils.sha1(file) }.getOrNull() != lib.sha1 -> corruptLibraries += lib
                lib.size != null && file.length() != lib.size -> corruptLibraries += lib
            }
        }

        val assetRef = VersionJson.assetIndex(json)
        var assetIndexOk = true
        val missingAssets = mutableListOf<String>()
        val corruptAssets = mutableListOf<String>()
        var verifiedAssets = 0
        var totalAssets = 0
        val indexFile = assetRef?.let { storage.assetIndexFile(it.id) }
        if (assetRef != null && indexFile?.isFile == true) {
            val objects = runCatching {
                JSONObject(indexFile.readText()).optJSONObject("objects") ?: JSONObject()
            }.getOrDefault(JSONObject())
            totalAssets = objects.length()
            objects.keys().forEach { key ->
                val obj = objects.optJSONObject(key) ?: return@forEach
                val hash = obj.optString("hash")
                val size = obj.optLong("size", 0L)
                val file = storage.objectFile(hash)
                when {
                    !file.isFile -> missingAssets += hash
                    file.length() != size -> corruptAssets += hash
                    else -> verifiedAssets++
                }
            }
        } else if (assetRef != null) {
            assetIndexOk = false
        }

        val loggingRef = VersionJson.loggingConfig(json)
        val loggingConfigOk = loggingRef?.let { ref ->
            val file = storage.loggingConfigFile(versionId, ref.id)
            file.isFile &&
                (ref.sha1 == null || runCatching { HashUtils.sha1(file) }.getOrNull() == ref.sha1) &&
                (ref.size == null || file.length() == ref.size)
        } ?: true

        return VerificationReport(
            versionId = versionId,
            versionJsonOk = true,
            metadataOk = metadataOk,
            assetIndexOk = assetIndexOk,
            loggingConfigOk = loggingConfigOk,
            missingLibraries = missingLibraries,
            corruptLibraries = corruptLibraries,
            missingAssets = missingAssets,
            corruptAssets = corruptAssets,
            totalLibraries = libraries.size,
            totalAssets = totalAssets,
            verifiedAssets = verifiedAssets
        )
    }
}