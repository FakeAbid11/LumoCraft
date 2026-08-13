package com.lumocraft.app.data.launch

import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.launch.LaunchException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Makes sure the client jar (<versions>/<id>/<id>.jar) exists before
 * launch, downloading it from `downloads.client` when missing or corrupt.
 * Downloading reuses the shared [Downloader] (retry, SHA-1 + size
 * verification, streaming).
 */
class ClientJarManager(
    private val storage: StorageManager,
    private val downloader: Downloader,
) {

    suspend fun ensure(versionId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonFile = storage.versionJsonFile(versionId)
            val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull()
                ?: throw LaunchException("Version JSON missing for '$versionId'")
            val client = json.optJSONObject("downloads")?.optJSONObject("client")
                ?: throw LaunchException(
                    "'$versionId' does not publish a client jar (downloads.client missing)"
                )
            val url = client.optString("url").takeIf { it.isNotEmpty() }
                ?: throw LaunchException("Client jar URL missing for '$versionId'")
            val target = File(storage.versionDirectory(versionId), "$versionId.jar")
            downloader.downloadVerified(
                url = url,
                destination = target,
                expectedSha1 = client.optString("sha1").takeIf { it.isNotEmpty() },
                expectedSize = client.optLong("size", -1L).takeIf { it >= 0 }
            ).getOrElse { error ->
                throw LaunchException("Failed to obtain client jar: ${error.message}")
            }
        }
    }
}