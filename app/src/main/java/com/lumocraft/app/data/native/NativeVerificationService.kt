package com.lumocraft.app.data.native

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.domain.native.NativeStatus
import com.lumocraft.app.domain.native.NativeVerificationReport
import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies extracted natives against their stamp: every recorded file
 * must exist with the expected size, the stamp architecture must match
 * the device architecture, and no extraction may be missing. Files that
 * vanished or changed size are reported as corrupt; entries never
 * recorded are reported as missing. A stale extraction (stamp from a
 * different architecture) is flagged as an architecture mismatch.
 */
class NativeVerificationService(private val storage: StorageManager) {

    suspend fun verify(
        versionId: String,
        arch: RuntimeArchitecture,
        sources: List<NativeJarSource>,
        extraction: NativeExtractionService,
    ): Result<NativeVerificationReport> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = extraction.archDirectory(versionId, arch)
            val stamp = extraction.loadStamp(versionId, arch)
            val expectedArch = arch.directoryName
            val archMismatch = stamp.arch != expectedArch

            if (!directory.isDirectory || stamp.jars.isEmpty()) {
                return@runCatching NativeVerificationReport(
                    versionId = versionId,
                    arch = arch,
                    nativeDirectory = directory,
                    status = NativeStatus.NOT_PREPARED
                )
            }

            val missing = mutableListOf<String>()
            val corrupt = mutableListOf<String>()

            // A source jar that was never stamped means the extraction ran
            // before that library existed (e.g. a library re-added after
            // repair): the extraction is incomplete, not ready.
            val stampedPaths = stamp.jars.map { it.jarPath }.toSet()
            for (source in sources) {
                if (source.libraryPath !in stampedPaths) {
                    missing += source.libraryPath
                }
            }

            for (jar in stamp.jars) {
                val source = sources.firstOrNull { it.libraryPath == jar.jarPath }
                if (source != null && source.size != jar.jarSize) {
                    corrupt += jar.jarPath
                }
                for (entry in jar.files) {
                    val file = File(directory, entry.name)
                    when {
                        !file.isFile -> missing += entry.name
                        file.length() != entry.size -> corrupt += entry.name
                    }
                }
            }

            val status = when {
                archMismatch -> NativeStatus.CORRUPTED
                missing.isNotEmpty() || corrupt.isNotEmpty() -> NativeStatus.CORRUPTED
                else -> NativeStatus.READY
            }

            NativeVerificationReport(
                versionId = versionId,
                arch = arch,
                nativeDirectory = directory,
                status = status,
                missingFiles = missing,
                corruptFiles = corrupt,
                duplicatesRemoved = stamp.duplicatesRemoved,
                archMismatch = archMismatch
            )
        }
    }
}