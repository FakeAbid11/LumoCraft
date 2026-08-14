package com.lumocraft.app.data.runtime

import android.os.Build
import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.data.performance.RuntimeCache
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.runtime.JvmConfiguration
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeProgress
import com.lumocraft.app.domain.runtime.RuntimeRepository
import com.lumocraft.app.domain.runtime.RuntimeStage
import com.lumocraft.app.domain.runtime.RuntimeStatus
import com.lumocraft.app.domain.runtime.RuntimeVerificationReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * [RuntimeRepository] backed by [StorageManager] metadata and the
 * [RuntimeInstaller]/[RuntimeVerifier] pipeline. The UI only observes
 * [observeRuntimes]; all mutations re-sync the state flow from disk.
 * A [RuntimeCache] avoids re-verifying unchanged runtimes.
 */
class DefaultRuntimeRepository(
    private val storage: StorageManager,
    private val installer: RuntimeInstaller,
    private val verifier: RuntimeVerifier,
    private val runtimeCache: RuntimeCache? = null,
) : RuntimeRepository {

    private val _runtimes = MutableStateFlow(readRuntimesFromDisk())

    override fun observeRuntimes(): Flow<List<RuntimeInfo>> = _runtimes.asStateFlow()

    override suspend fun getDefaultRuntime(): RuntimeInfo? {
        // Prefer the marked default; fall back to any installed runtime
        // so Phase 6 can always obtain a ready-to-launch runtime.
        val runtime = _runtimes.value.firstOrNull { it.isDefault && it.status == RuntimeStatus.INSTALLED }
            ?: _runtimes.value.firstOrNull { it.status == RuntimeStatus.INSTALLED }
            ?: return null
        // Skip re-verification for runtimes validated recently.
        if (runtimeCache?.isFresh(runtime) == true) return runtime
        val report = verifier.verify(runtime)
        if (report.ok) {
            runtimeCache?.markValidated(runtime)
            return runtime
        }
        runtimeCache?.invalidate()
        val updated = runtime.copy(status = RuntimeStatus.CORRUPTED)
        writeRuntimeMetadata(updated)
        _runtimes.value = readRuntimesFromDisk()
        return null
    }

    override fun detectArchitecture(): RuntimeArchitecture {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: return RuntimeArchitecture.ARM64_V8A
        return RuntimeArchitecture.fromAbi(abi) ?: RuntimeArchitecture.ARM64_V8A
    }

    override fun install(runtimeId: String): Flow<RuntimeProgress> = channelFlow {
        try {
            val existing = _runtimes.value
            val hasDefault = existing.any { it.isDefault }
            val arch = detectArchitecture()
            val version = runtimeId.removePrefix("java")
            val url = try {
                buildRuntimeUrl(version, arch)
            } catch (e: Exception) {
                send(RuntimeProgress(runtimeId, RuntimeStage.COMPLETE, error = e.message))
                return@channelFlow
            }
            val result = installer.install(
                runtimeId = runtimeId,
                version = version,
                architecture = arch,
                vendor = "openjdk",
                archiveUrl = url,
                archiveSha256 = null,
                onProgress = { send(it) }
            )
            if (result.isFailure) {
                send(
                    RuntimeProgress(
                        runtimeId = runtimeId,
                        stage = RuntimeStage.COMPLETE,
                        error = result.exceptionOrNull()?.message
                    )
                )
            } else {
                val info = result.getOrThrow()
                send(RuntimeProgress(runtimeId, RuntimeStage.VERIFYING))
                val report = verifier.verify(info)
                // First installed runtime (or any install when none is marked
                // default) becomes the default automatically.
                val shouldBeDefault = !hasDefault || info.isDefault
                val verified = info.copy(
                    isDefault = shouldBeDefault,
                    status = if (report.ok) RuntimeStatus.INSTALLED else RuntimeStatus.CORRUPTED
                )
                if (report.ok) runtimeCache?.markValidated(verified)
                writeRuntimeMetadata(verified)
                _runtimes.value = readRuntimesFromDisk()
            }
        } finally {
            _runtimes.value = readRuntimesFromDisk()
        }
    }

    override suspend fun remove(runtimeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            runtimeCache?.invalidate()
            val removedWasDefault = _runtimes.value.any { it.id == runtimeId && it.isDefault }
            val dir = storage.runtimeDirectoryFor(runtimeId)
            dir.deleteRecursively()
            removeRuntimeFromMetadata(runtimeId)
            // If the default runtime was removed, promote another installed runtime.
            if (removedWasDefault) {
                val remaining = readRuntimesFromDisk()
                val newDefault = remaining.firstOrNull { it.status == RuntimeStatus.INSTALLED }
                if (newDefault != null) {
                    writeRuntimeMetadata(newDefault.copy(isDefault = true))
                }
            }
            _runtimes.value = readRuntimesFromDisk()
        }
    }

    override suspend fun verify(runtimeId: String): Result<RuntimeVerificationReport> {
        val runtime = _runtimes.value.firstOrNull { it.id == runtimeId }
            ?: return Result.failure(IllegalStateException("Runtime not found: $runtimeId"))
        val report = verifier.verify(runtime)
        val updated = runtime.copy(
            status = if (report.ok) RuntimeStatus.INSTALLED else RuntimeStatus.CORRUPTED
        )
        if (report.ok) runtimeCache?.markValidated(updated)
        writeRuntimeMetadata(updated)
        _runtimes.value = readRuntimesFromDisk()
        return Result.success(report)
    }

    override fun repair(runtimeId: String): Flow<RuntimeProgress> = channelFlow {
        try {
            runtimeCache?.invalidate()
            val runtime = _runtimes.value.firstOrNull { it.id == runtimeId }
            if (runtime == null) {
                send(
                    RuntimeProgress(
                        runtimeId = runtimeId,
                        stage = RuntimeStage.COMPLETE,
                        error = "Runtime not found"
                    )
                )
                return@channelFlow
            }
            val report = verifier.verify(runtime)
            if (report.ok) {
                val updated = runtime.copy(status = RuntimeStatus.INSTALLED)
                runtimeCache?.markValidated(updated)
                writeRuntimeMetadata(updated)
                _runtimes.value = readRuntimesFromDisk()
                send(RuntimeProgress(runtimeId, RuntimeStage.COMPLETE, 1f))
                return@channelFlow
            }

            // Permission repair first: reapply executable bits on the
            // existing runtime and reverify. Only redownload when the
            // runtime is still broken after the permission repair, so
            // common Permission-denied failures never cost a download.
            send(RuntimeProgress(runtimeId, RuntimeStage.VERIFYING))
            val repaired = RuntimePermissions.restoreExecutableBits(File(runtime.path)) &&
                verifier.verify(runtime).ok
            if (repaired) {
                val updated = runtime.copy(status = RuntimeStatus.INSTALLED)
                runtimeCache?.markValidated(updated)
                writeRuntimeMetadata(updated)
                _runtimes.value = readRuntimesFromDisk()
                send(RuntimeProgress(runtimeId, RuntimeStage.COMPLETE, 1f))
                return@channelFlow
            }

            val arch = runtime.architecture
            val url = try {
                buildRuntimeUrl(runtime.version, arch)
            } catch (e: Exception) {
                send(RuntimeProgress(runtimeId, RuntimeStage.COMPLETE, error = e.message))
                return@channelFlow
            }
            val result = installer.install(
                runtimeId = runtimeId,
                version = runtime.version,
                architecture = arch,
                vendor = runtime.vendor,
                archiveUrl = url,
                archiveSha256 = null,
                onProgress = { send(it) }
            )
            if (result.isFailure) {
                send(
                    RuntimeProgress(
                        runtimeId = runtimeId,
                        stage = RuntimeStage.COMPLETE,
                        error = result.exceptionOrNull()?.message
                    )
                )
            } else {
                val info = result.getOrThrow().copy(isDefault = runtime.isDefault)
                send(RuntimeProgress(runtimeId, RuntimeStage.VERIFYING))
                val verifyReport = verifier.verify(info)
                val verified = info.copy(
                    status = if (verifyReport.ok) RuntimeStatus.INSTALLED else RuntimeStatus.CORRUPTED
                )
                if (verifyReport.ok) runtimeCache?.markValidated(verified)
                writeRuntimeMetadata(verified)
                _runtimes.value = readRuntimesFromDisk()
            }
        } finally {
            _runtimes.value = readRuntimesFromDisk()
        }
    }

    override suspend fun setDefault(runtimeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updated = _runtimes.value.map { it.copy(isDefault = it.id == runtimeId) }
            updated.forEach { writeRuntimeMetadata(it) }
            _runtimes.value = readRuntimesFromDisk()
        }
    }

    override fun saveJvmConfiguration(config: JvmConfiguration) {
        val json = JSONObject()
            .put(KEY_MAX_MEMORY, config.maxMemoryMB)
            .put(KEY_MIN_MEMORY, config.minMemoryMB)
            .put(KEY_GC_MODE, config.gcMode.name)
            .put(KEY_EXTRA_ARGS, JSONArray(config.extraArguments))
        storage.runtimeMetadataFile().parentFile?.mkdirs()
        File(storage.runtimeMetadataFile().parentFile, JVM_CONFIG_FILE).writeText(json.toString())
    }

    override fun loadJvmConfiguration(): JvmConfiguration {
        val file = File(storage.runtimeMetadataFile().parentFile, JVM_CONFIG_FILE)
        if (!file.isFile) return JvmConfiguration()
        return runCatching {
            val obj = JSONObject(file.readText())
            JvmConfiguration(
                maxMemoryMB = obj.optInt(KEY_MAX_MEMORY, JvmConfiguration.DEFAULT_MAX_MB),
                minMemoryMB = obj.optInt(KEY_MIN_MEMORY, JvmConfiguration.DEFAULT_MIN_MB),
                gcMode = runCatching {
                    JvmConfiguration.GcMode.valueOf(obj.optString(KEY_GC_MODE))
                }.getOrDefault(JvmConfiguration.GcMode.G1),
                extraArguments = runCatching {
                    val arr = obj.optJSONArray(KEY_EXTRA_ARGS) ?: JSONArray()
                    buildList {
                        for (i in 0 until arr.length()) add(arr.getString(i))
                    }
                }.getOrDefault(emptyList())
            )
        }.getOrDefault(JvmConfiguration())
    }

    private fun readRuntimesFromDisk(): List<RuntimeInfo> {
        val file = storage.runtimeMetadataFile()
        if (!file.isFile) return emptyList()
        return runCatching {
            val arr = JSONObject(file.readText()).optJSONArray(KEY_RUNTIMES) ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    if (id.isEmpty()) continue
                    add(
                        RuntimeInfo(
                            id = id,
                            version = obj.optString("version"),
                            architecture = RuntimeArchitecture.fromAbi(obj.optString("arch"))
                                ?: RuntimeArchitecture.ARM64_V8A,
                            vendor = obj.optString("vendor"),
                            path = obj.optString("path"),
                            installedAt = obj.optLong("installedAt"),
                            isDefault = obj.optBoolean("isDefault"),
                            status = runCatching {
                                RuntimeStatus.valueOf(obj.optString("status"))
                            }.getOrDefault(RuntimeStatus.MISSING),
                            checksum = obj.optString("checksum").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRuntimeMetadata(info: RuntimeInfo) {
        val file = storage.runtimeMetadataFile()
        file.parentFile?.mkdirs()
        val existing = if (file.isFile) JSONObject(file.readText()) else JSONObject()
        val arr = existing.optJSONArray(KEY_RUNTIMES) ?: JSONArray()
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optString("id") != info.id) filtered.put(obj)
        }
        filtered.put(
            JSONObject()
                .put("id", info.id)
                .put("version", info.version)
                .put("arch", info.architecture.abi)
                .put("vendor", info.vendor)
                .put("path", info.path)
                .put("installedAt", info.installedAt)
                .put("isDefault", info.isDefault)
                .put("status", info.status.name)
                .put("checksum", info.checksum ?: "")
        )
        file.writeText(JSONObject().put(KEY_RUNTIMES, filtered).toString())
    }

    private fun removeRuntimeFromMetadata(runtimeId: String) {
        val file = storage.runtimeMetadataFile()
        if (!file.isFile) return
        val arr = JSONObject(file.readText()).optJSONArray(KEY_RUNTIMES) ?: JSONArray()
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optString("id") != runtimeId) filtered.put(obj)
        }
        file.writeText(JSONObject().put(KEY_RUNTIMES, filtered).toString())
    }

    private fun buildRuntimeUrl(version: String, arch: RuntimeArchitecture): String {
        // Only Java 17 has a published Android/Bionic build in the source
        // mirror. A glibc desktop JDK for any version cannot load on Android
        // (see AppConfig.RUNTIME_JRE17_BASE_URL), so reject other majors with
        // an actionable message instead of downloading an unusable archive.
        val major = version.substringBefore('.')
        if (major != "17") {
            throw java.io.IOException(
                "No Android-compatible Java $major runtime is available. " +
                    "Install Java 17 instead."
            )
        }
        val asset = when (arch) {
            RuntimeArchitecture.ARM64_V8A -> AppConfig.RUNTIME_JRE17_ARM64
            RuntimeArchitecture.ARMEABI_V7A -> AppConfig.RUNTIME_JRE17_ARM
            RuntimeArchitecture.X86_64 -> AppConfig.RUNTIME_JRE17_X86_64
        }
        return AppConfig.RUNTIME_JRE17_BASE_URL + asset
    }

    private companion object {
        const val KEY_RUNTIMES = "runtimes"
        const val KEY_MAX_MEMORY = "maxMemoryMB"
        const val KEY_MIN_MEMORY = "minMemoryMB"
        const val KEY_GC_MODE = "gcMode"
        const val KEY_EXTRA_ARGS = "extraArguments"
        const val JVM_CONFIG_FILE = "jvm_config.json"
    }
}