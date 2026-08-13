package com.lumocraft.app.data.native

import android.os.Build
import com.lumocraft.app.data.preferences.RendererPreference
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.native.NativeException
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.native.NativeVerificationReport
import com.lumocraft.app.domain.native.RendererProfile
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Reference [NativeRuntimeManager]: resolves the native jars for a
 * version, extracts them into the version's architecture folder (cached
 * and incremental via stamps), verifies them and exposes the JNI
 * environment the launch pipeline injects. Renderer settings are
 * delegated to [RendererPreference].
 */
class DefaultNativeRuntimeManager(
    private val storage: StorageManager,
    private val libraryManager: NativeLibraryManager,
    private val extractionService: NativeExtractionService,
    private val verificationService: NativeVerificationService,
    private val rendererPreference: RendererPreference,
    private val detectedArchitecture: RuntimeArchitecture,
) : NativeRuntimeManager {

    private val _status = MutableStateFlow(NativeStatus.NOT_PREPARED)
    override val status: StateFlow<NativeStatus> = _status.asStateFlow()

    private var preparedVersionId: String? = null

    override fun architecture(): RuntimeArchitecture = detectedArchitecture

    override fun nativeDirectory(versionId: String): File =
        extractionService.archDirectory(versionId, detectedArchitecture)

    override fun jniEnvironment(versionId: String): Map<String, String> {
        val directory = nativeDirectory(versionId).absolutePath
        return mapOf(
            "java.library.path" to directory,
            "org.lwjgl.librarypath" to directory
        )
    }

    override suspend fun prepare(versionId: String): Result<NativeVerificationReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                _status.value = NativeStatus.PREPARING
                preparedVersionId = versionId

                val sources = libraryManager.locate(versionId).getOrElse {
                    _status.value = NativeStatus.CORRUPTED
                    throw NativeException("Could not resolve native libraries: ${it.message}")
                }
                if (sources.isEmpty()) {
                    // Version without native libraries (e.g. headless-only
                    // versions) needs no preparation.
                    val directory = nativeDirectory(versionId)
                    _status.value = NativeStatus.READY
                    return@runCatching NativeVerificationReport(
                        versionId = versionId,
                        arch = detectedArchitecture,
                        nativeDirectory = directory,
                        status = NativeStatus.READY
                    )
                }

                val extraction = extractionService.extract(versionId, detectedArchitecture, sources)
                    .getOrElse {
                        _status.value = NativeStatus.CORRUPTED
                        throw NativeException("Native extraction failed: ${it.message}")
                    }

                val report = verifyAndReport(versionId, sources)
                report.copy(
                    extractedFiles = extraction.extractedFiles,
                    skippedJars = extraction.skippedJars,
                    duplicatesRemoved = extraction.duplicatesRemoved
                )
            }
        }

    override suspend fun verify(versionId: String): Result<NativeVerificationReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sources = libraryManager.locate(versionId).getOrElse {
                    throw NativeException("Could not resolve native libraries: ${it.message}")
                }
                verifyAndReport(versionId, sources)
            }
        }

    override fun statusOf(versionId: String): NativeStatus {
        if (preparedVersionId == versionId) return _status.value
        val stamp = extractionService.stampFile(versionId, detectedArchitecture)
        return if (stamp.isFile) NativeStatus.READY else NativeStatus.NOT_PREPARED
    }

    override fun rendererProfile(): RendererProfile = rendererPreference.loadProfile()

    override fun saveRendererProfile(profile: RendererProfile) {
        rendererPreference.saveProfile(profile)
    }

    private suspend fun verifyAndReport(
        versionId: String,
        sources: List<NativeJarSource>,
    ): NativeVerificationReport {
        val report = verificationService.verify(
            versionId = versionId,
            arch = detectedArchitecture,
            sources = sources,
            extraction = extractionService
        ).getOrElse {
            _status.value = NativeStatus.CORRUPTED
            throw NativeException("Native verification failed: ${it.message}")
        }
        _status.value = report.status
        return report
    }
}

/** Detects the device architecture from the supported ABIs. */
object NativeArchitecture {
    fun detect(): RuntimeArchitecture {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return RuntimeArchitecture.fromAbi(abi) ?: when {
            abi.contains("arm64") || abi.contains("aarch64") -> RuntimeArchitecture.ARM64_V8A
            abi.contains("v7a") || abi.contains("armeabi") -> RuntimeArchitecture.ARMEABI_V7A
            else -> RuntimeArchitecture.X86_64
        }
    }
}