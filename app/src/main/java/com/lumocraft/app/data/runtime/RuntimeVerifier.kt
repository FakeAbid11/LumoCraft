package com.lumocraft.app.data.runtime

import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeVerificationReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies a runtime installation on disk.
 *
 * - required binaries exist (java, javac, keytool)
 * - metadata matches the on-disk state
 * - executable files are actually executable
 * - checksum matches when a recorded checksum is present
 *
 * Returns a detailed [RuntimeVerificationReport] with missing and corrupt
 * file lists so callers can drive repair.
 */
class RuntimeVerifier {

    suspend fun verify(runtime: RuntimeInfo): RuntimeVerificationReport =
        withContext(Dispatchers.IO) {
            val runtimeDir = File(runtime.path)
            if (!runtimeDir.isDirectory) {
                return@withContext RuntimeVerificationReport(
                    runtimeId = runtime.id,
                    missingFiles = listOf(runtimeDir.absolutePath)
                )
            }

            val missing = mutableListOf<String>()
            val corrupt = mutableListOf<String>()

            // Required binaries.
            val binariesOk = REQUIRED_BINARIES.all { name ->
                val file = File(runtimeDir, name)
                if (!file.isFile) {
                    missing += name
                    false
                } else {
                    true
                }
            }

            // Executable check.
            val executableOk = REQUIRED_BINARIES.all { name ->
                val file = File(runtimeDir, name)
                !file.isFile || file.canExecute()
            }

            // Metadata check: release file exists and contains expected version.
            val metadataOk = verifyMetadata(runtimeDir, runtime)

            // Checksum: verify the release file against the recorded checksum.
            val checksumOk = verifyChecksum(runtimeDir, runtime, corrupt)

            RuntimeVerificationReport(
                runtimeId = runtime.id,
                binariesOk = binariesOk && executableOk,
                metadataOk = metadataOk,
                checksumOk = checksumOk,
                missingFiles = missing,
                corruptFiles = corrupt
            )
        }

    /**
     * Verifies the runtime's `release` file exists and, when the runtime
     * records a version, that the release file mentions it. The check is
     * lenient: Temurin release files contain versions like
     * `JAVA_VERSION="17.0.11+9"`, so we match on the major version prefix.
     */
    private fun verifyMetadata(runtimeDir: File, runtime: RuntimeInfo): Boolean {
        val releaseFile = File(runtimeDir, "release")
        if (!releaseFile.isFile) return false
        if (runtime.version.isBlank()) return true
        val content = runCatching { releaseFile.readText() }.getOrNull() ?: return false
        val major = runtime.version.substringBefore('.')
        return content.contains("JAVA_VERSION=\"$major") ||
            content.contains("JAVA_VERSION=\"1.$major")
    }

    /**
     * Verifies the SHA-256 of the runtime's `release` file against the
     * recorded checksum. When no checksum is recorded, the check passes
     * (nothing to compare against).
     */
    private suspend fun verifyChecksum(
        runtimeDir: File,
        runtime: RuntimeInfo,
        corrupt: MutableList<String>,
    ): Boolean {
        val expected = runtime.checksum ?: return true
        val releaseFile = File(runtimeDir, "release")
        if (!releaseFile.isFile) {
            corrupt += "release"
            return false
        }
        val actual = HashUtils.sha256(releaseFile)
        if (actual != expected) {
            corrupt += "release"
            return false
        }
        return true
    }

    private companion object {
        val REQUIRED_BINARIES = listOf(
            "bin/java",
            "bin/javac",
            "bin/keytool"
        )
    }
}