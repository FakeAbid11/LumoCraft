package com.lumocraft.app.data.runtime

import android.util.Log
import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.domain.runtime.RuntimeInfo
import com.lumocraft.app.domain.runtime.RuntimeVerificationReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies a runtime installation on disk.
 *
 * - required binaries exist, are regular files and are executable
 *   (bin/java, bin/javac, bin/keytool) — every binary is classified as
 *   Missing, Not executable or Corrupted
 * - metadata (`release`) exists and matches the recorded version
 * - checksum of the `release` file matches the recorded SHA-256 when present
 * - `lib/modules` exists (JRT image — the JDK cannot start without it)
 * - `lib/server/libjvm.so` exists (the HotSpot VM shared library)
 * - `jmods/` exists and is non-empty (JDK image completeness)
 * - runtime root layout is consistent (bin/, lib/ at the root, release
 *   at the root — a nested JVM home is flagged)
 *
 * Returns a detailed [RuntimeVerificationReport] with missing and corrupt
 * file lists so callers can drive repair. A partially broken runtime is
 * never reported as ok.
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
            val notExecutable = mutableListOf<String>()

            // Required launcher binaries: each must exist, be a regular
            // file and carry the executable bit. Classification:
            // exists()==false -> Missing, !isFile -> Corrupted,
            // !canExecute() -> Not executable. A non-executable bin/java
            // must stop every launch path (ProcessBuilder would fail
            // with Permission denied).
            REQUIRED_BINARIES.forEach { name ->
                val file = File(runtimeDir, name)
                when {
                    !file.exists() -> missing += name
                    !file.isFile -> corrupt += name
                    !file.canExecute() -> {
                        notExecutable += name
                        Log.e(
                            TAG,
                            "RUNTIME_EXECUTABLE_FAILED path=${file.absolutePath} " +
                                "canExecute=false result=notExecutable"
                        )
                    }
                    else -> Unit
                }
            }

            val binariesOk = missing.isEmpty() && corrupt.isEmpty() && notExecutable.isEmpty()

            // Metadata check: release file exists and contains expected version.
            val metadataOk = verifyMetadata(runtimeDir, runtime)

            // Checksum: verify the release file against the recorded checksum.
            val (checksumOk, checksumDetail) = verifyChecksum(runtimeDir, runtime, corrupt)

            // JRT image: lib/modules is what the JVM actually loads at startup.
            val modulesFile = File(runtimeDir, "lib/modules")
            val modulesOk = if (modulesFile.isFile) {
                true
            } else {
                missing += "lib/modules"
                false
            }

            // HotSpot server library: lib/server/libjvm.so (JDK 9+ layout).
            val serverLib = File(runtimeDir, "lib/server/libjvm.so")
            val serverOk = if (serverLib.isFile) {
                true
            } else {
                missing += "lib/server/libjvm.so"
                false
            }

            // jmods directory: present and non-empty for a complete JDK image.
            val jmodsDir = File(runtimeDir, "jmods")
            val jmodsOk = if (jmodsDir.isDirectory && jmodsDir.listFiles()?.isNotEmpty() == true) {
                true
            } else {
                missing += "jmods/"
                false
            }

            // Root consistency: bin/ and lib/ live at the runtime root, and
            // the runtime is not a nested JVM home (bin/java would be one
            // level down in that case).
            val rootOk = File(runtimeDir, "bin").isDirectory &&
                File(runtimeDir, "lib").isDirectory &&
                File(runtimeDir, "bin/java").isFile

            RuntimeVerificationReport(
                runtimeId = runtime.id,
                binariesOk = binariesOk,
                metadataOk = metadataOk,
                checksumOk = checksumOk,
                modulesOk = modulesOk,
                serverOk = serverOk,
                jmodsOk = jmodsOk,
                rootOk = rootOk,
                missingFiles = missing,
                notExecutableFiles = notExecutable,
                corruptFiles = corrupt,
                checksumDetail = checksumDetail
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
    ): Pair<Boolean, String?> {
        val expected = runtime.checksum ?: return true to null
        val releaseFile = File(runtimeDir, "release")
        if (!releaseFile.isFile) {
            corrupt += "release"
            return false to "release file missing"
        }
        val actual = HashUtils.sha256(releaseFile)
        if (actual != expected) {
            corrupt += "release"
            return false to "expected $expected, found $actual"
        }
        return true to null
    }

    private companion object {
        const val TAG = "LumoCraft/RuntimeVerifier"

        val REQUIRED_BINARIES = listOf(
            "bin/java",
            "bin/javac",
            "bin/keytool"
        )
    }
}