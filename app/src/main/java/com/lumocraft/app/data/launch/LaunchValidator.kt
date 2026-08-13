package com.lumocraft.app.data.launch

import com.lumocraft.app.data.native.NativeArchitecture
import com.lumocraft.app.data.runtime.RuntimeVerifier
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchValidationReport
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.version.InstallState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Pre-launch validation: account, runtime binaries, installed version,
 * libraries, assets, client jar, main class and native libraries. All
 * checks are existence/size based (no hashing), so both the Home
 * readiness check and the launch pipeline stay fast. The pipeline
 * refuses to launch when [LaunchValidationReport.ok] is false.
 */
class LaunchValidator(
    private val storage: StorageManager,
    private val runtimeVerifier: RuntimeVerifier,
    private val nativeRuntimeManager: NativeRuntimeManager,
) {

    /** Full validation including the client jar; used by the pipeline. */
    suspend fun validate(context: LaunchContext): LaunchValidationReport =
        scan(context, requireClientJar = true)

    /** Readiness check without the client jar (fetched at launch time). */
    suspend fun validateReadiness(context: LaunchContext): LaunchValidationReport =
        scan(context, requireClientJar = false)

    private suspend fun scan(
        context: LaunchContext,
        requireClientJar: Boolean,
    ): LaunchValidationReport = withContext(Dispatchers.IO) {
        val accountOk = context.account.username.isNotBlank()

        val runtimeReport = context.runtime?.let { runtimeVerifier.verify(it) }
        val runtimeOk = runtimeReport?.ok == true

        val metadata = storage.readMetadata(context.versionId)
        val versionOk = metadata != null && metadata.state == InstallState.INSTALLED

        val json = versionJson(context.versionId)
        if (json == null) {
            return@withContext LaunchValidationReport(
                accountOk = accountOk,
                runtimeOk = runtimeOk,
                runtimeDetail = runtimeReport?.missingFiles?.joinToString()
                    ?.takeIf { runtimeReport.missingFiles.isNotEmpty() }
            )
        }

        val missingLibraries = buildSet {
            var current = json
            while (true) {
                addAll(VersionJson.libraries(current).map { it.path })
                val parentId = current.optString("inheritsFrom").takeIf { it.isNotEmpty() }
                    ?: break
                current = versionJson(parentId) ?: break
            }
        }.filter { path -> !storage.libraryFile(path).isFile }
        val (assetIndexOk, missingAssets) = checkAssets(json)
        val loggingConfigOk = json.optJSONObject("logging")
            ?.optJSONObject("client")
            ?.optJSONObject("file")
            ?.optString("id")
            ?.let { id -> storage.loggingConfigFile(context.versionId, id).isFile }
            ?: true
        val clientJarOk = !requireClientJar ||
            File(storage.versionDirectory(context.versionId), "${context.versionId}.jar").isFile
        val nativeStatus = nativeRuntimeManager.statusOf(context.versionId)
        val nativeOk = nativeStatus == NativeStatus.READY
        val nativeDetail = if (nativeOk) {
            null
        } else {
            "${NativeArchitecture.detect().abi} / $nativeStatus"
        }

        LaunchValidationReport(
            accountOk = accountOk,
            runtimeOk = runtimeOk,
            runtimeDetail = runtimeReport?.missingFiles?.joinToString()
                ?.takeIf { runtimeReport.missingFiles.isNotEmpty() },
            versionOk = versionOk,
            mainClassOk = mainClassPresent(json),
            clientJarOk = clientJarOk,
            assetIndexOk = assetIndexOk,
            loggingConfigOk = loggingConfigOk,
            nativeOk = nativeOk,
            nativeDetail = nativeDetail,
            missingLibraries = missingLibraries,
            missingAssets = missingAssets
        )
    }

    private fun versionJson(versionId: String): JSONObject? {
        val file = storage.versionJsonFile(versionId)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    /** Returns (indexOk, count of missing object files). */
    private fun checkAssets(json: JSONObject): Pair<Boolean, Int> {
        val ref = VersionJson.assetIndex(json) ?: return true to 0
        val indexFile = storage.assetIndexFile(ref.id)
        if (!indexFile.isFile) return false to 0
        val objects = runCatching {
            JSONObject(indexFile.readText()).optJSONObject("objects") ?: JSONObject()
        }.getOrNull() ?: return false to 0
        var missing = 0
        objects.keys().forEach { key ->
            val hash = objects.optJSONObject(key)?.optString("hash").orEmpty()
            if (hash.isNotEmpty() && !storage.objectFile(hash).isFile) missing++
        }
        return true to missing
    }

    private fun mainClassPresent(json: JSONObject): Boolean {
        if (json.optString("mainClass").isNotEmpty()) return true
        var parentId = json.optString("inheritsFrom").takeIf { it.isNotEmpty() } ?: return false
        while (true) {
            val parent = versionJson(parentId) ?: return false
            if (parent.optString("mainClass").isNotEmpty()) return true
            parentId = parent.optString("inheritsFrom").takeIf { it.isNotEmpty() } ?: return false
        }
    }
}