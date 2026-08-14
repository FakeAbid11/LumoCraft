package com.lumocraft.app.data.launch

import com.lumocraft.app.data.native.NativeArchitecture
import com.lumocraft.app.data.performance.RuntimeCache
import com.lumocraft.app.data.runtime.RuntimeVerifier
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchValidationReport
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.performance.SmartVerifier
import com.lumocraft.app.domain.loader.LoaderLaunchConfigurator
import com.lumocraft.app.domain.runtime.RuntimeVerificationReport
import com.lumocraft.app.domain.version.InstallState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Pre-launch validation: account, runtime binaries, installed version,
 * libraries, assets, client jar, main class and native libraries.
 *
 * File verification goes through the [SmartVerifier]: cached results are
 * trusted for the readiness check, launch-time checks are stat-only and
 * hashing happens only when files changed. The runtime verification is
 * cached through [RuntimeCache] so unchanged runtimes are not rescanned.
 * The pipeline refuses to launch when [LaunchValidationReport.ok] is false.
 */
class LaunchValidator(
    private val storage: StorageManager,
    private val runtimeVerifier: RuntimeVerifier,
    private val nativeRuntimeManager: NativeRuntimeManager,
    private val smartVerifier: SmartVerifier,
    private val runtimeCache: RuntimeCache? = null,
    /**
     * Resolves loader instances (Fabric) so their patched client jar and
     * loader libraries are validated instead of the vanilla layout.
     * Null keeps the validator vanilla-only.
     */
    private val loaderConfigurator: LoaderLaunchConfigurator? = null,
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

        val runtime = context.runtime
        val cachedRuntimeOk = runtime != null && runtimeCache?.isFresh(runtime) == true
        var runtimeReport: RuntimeVerificationReport? = null
        if (!cachedRuntimeOk && runtime != null) {
            runtimeReport = runtimeVerifier.verify(runtime)
            if (runtimeReport.ok) runtimeCache?.markValidated(runtime)
        }
        val runtimeOk = cachedRuntimeOk || runtimeReport?.ok == true
        val runtimeDetail = when {
            runtime == null -> "No Java runtime installed — install one in Settings"
            runtimeReport?.missingFiles?.isNotEmpty() == true ->
                "Runtime incomplete: missing ${runtimeReport.missingFiles.take(8).joinToString(", ")}"
            runtimeReport?.corruptFiles?.isNotEmpty() == true ->
                "Runtime corrupt: ${runtimeReport.corruptFiles.take(8).joinToString(", ")}"
            runtimeReport?.checksumDetail != null -> "Runtime checksum mismatch: ${runtimeReport.checksumDetail}"
            else -> null
        }

        val metadata = storage.readMetadata(context.versionId)
        val versionOk = metadata != null && metadata.state == InstallState.INSTALLED

        val json = versionJson(context.versionId)
        if (json == null) {
            return@withContext LaunchValidationReport(
                accountOk = accountOk,
                runtimeOk = runtimeOk,
                versionJsonOk = false,
                runtimeDetail = runtimeDetail
            )
        }

        val mode = if (requireClientJar) SmartVerifier.Mode.LAUNCH else SmartVerifier.Mode.READINESS
        val smart = smartVerifier.verify(context.versionId, mode)
        val loggingConfigOk = json.optJSONObject("logging")
            ?.optJSONObject("client")
            ?.optJSONObject("file")
            ?.optString("id")
            ?.let { id -> storage.loggingConfigFile(context.versionId, id).isFile }
            ?: true
        val clientJar = loaderConfigurator?.clientJarFor(context.versionId)
            ?: File(storage.versionDirectory(context.versionId), "${context.versionId}.jar")
        val clientJarOk = !requireClientJar || clientJar.isFile
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
            runtimeDetail = runtimeDetail,
            versionOk = versionOk,
            mainClassOk = mainClassPresent(json),
            clientJarOk = clientJarOk,
            assetIndexOk = smart.assetIndexOk,
            loggingConfigOk = loggingConfigOk,
            nativeOk = nativeOk,
            nativeDetail = nativeDetail,
            missingLibraries = smart.allMissingLibraries,
            missingAssets = smart.missingAssets + smart.corruptAssets,
            fromCache = smart.cached
        )
    }

    private fun versionJson(versionId: String): JSONObject? {
        val file = storage.versionJsonFile(versionId)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
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