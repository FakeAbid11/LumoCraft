package com.lumocraft.app.data.performance

import com.lumocraft.app.data.network.HashUtils
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.performance.CacheManager
import com.lumocraft.app.domain.performance.LaunchCacheEntry
import com.lumocraft.app.domain.performance.MemoryOptimizer
import com.lumocraft.app.domain.performance.SmartVerificationResult
import com.lumocraft.app.domain.performance.SmartVerifier
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Selective verification:
 *
 * - READINESS mode trusts a fingerprint-matching cache row outright —
 *   the Home screen never scans the disk again once a version verified.
 * - LAUNCH mode re-checks every library and asset by size (stat only);
 *   hashing happens only for files whose size changed or that have no
 *   valid cached checksum ([ChecksumCache]).
 * - A stale/missing fingerprint falls back to a full pass, which primes
 *   the checksum cache for every subsequent launch.
 */
class SmartVerifierImpl(
    private val storage: StorageManager,
    private val cache: CacheManager,
    private val checksums: ChecksumCache,
    private val buffers: MemoryOptimizer? = null,
) : SmartVerifier {

    override suspend fun verify(versionId: String, mode: SmartVerifier.Mode): SmartVerificationResult =
        withContext(Dispatchers.IO) {
            val json = versionJson(versionId) ?: return@withContext SmartVerificationResult(versionId)
            val chain = loadChain(versionId) ?: return@withContext SmartVerificationResult(versionId)
            val fingerprint = Fingerprints.of(chain.map { storage.versionJsonFile(it) })
            val entry = cache.getEntry(versionId)
            val cached = entry != null && entry.versionJsonFingerprint == fingerprint &&
                entry.verifiedLibraries.isNotEmpty()

            if (cached && mode == SmartVerifier.Mode.READINESS) {
                cache.recordHit()
                return@withContext SmartVerificationResult(
                    versionId = versionId,
                    ok = true,
                    cached = true,
                    assetIndexOk = true
                )
            }

            var filesChecked = 0
            var filesHashed = 0
            val missing = mutableListOf<String>()
            val corrupt = mutableListOf<String>()

            val libraryPaths = buildList {
                for (id in chain) {
                    val json = versionJson(id) ?: continue
                    for (lib in VersionJson.libraries(json)) {
                        if (lib.path !in this) add(lib.path)
                    }
                }
            }
            // Expected sizes/hashes parsed once per chain, not per file.
            val expected = linkedMapOf<String, Pair<Long?, String?>>()
            for (id in chain) {
                val json = versionJson(id) ?: continue
                for (lib in VersionJson.libraries(json)) {
                    expected.putIfAbsent(lib.path, lib.size to lib.sha1)
                }
            }

            for (path in libraryPaths) {
                val file = storage.libraryFile(path)
                if (!file.isFile) {
                    missing += path
                    continue
                }
                filesChecked++
                val (expectedSize, expectedSha1) = expected[path] ?: (null to null)
                if (expectedSize != null && file.length() != expectedSize) {
                    // Size changed since install: hash to confirm corruption.
                    filesHashed++
                    if (hashMatches(file, expectedSha1) != true) corrupt += path
                } else if (!cached) {
                    // No cached result for this fingerprint yet: verify hashes
                    // via the checksum cache, rehashing only unknown files.
                    val cachedSha = checksums.lookup(path, file.length(), file.lastModified())
                    if (cachedSha != null) {
                        if (expectedSha1 != null && cachedSha != expectedSha1) corrupt += path
                    } else {
                        filesHashed++
                        val sha = hash(file)
                        checksums.store(path, file.length(), file.lastModified(), sha)
                        if (expectedSha1 != null && sha != expectedSha1) corrupt += path
                    }
                }
            }

            val (assetIndexOk, missingAssets, corruptAssets, indexFingerprint, assetCount) =
                checkAssets(json)

            val ok = missing.isEmpty() && corrupt.isEmpty() &&
                missingAssets == 0 && corruptAssets == 0 && assetIndexOk

            val result = SmartVerificationResult(
                versionId = versionId,
                ok = ok,
                cached = cached,
                filesChecked = filesChecked,
                filesHashed = filesHashed,
                assetIndexOk = assetIndexOk,
                missingLibraries = missing,
                corruptLibraries = corrupt,
                missingAssets = missingAssets,
                corruptAssets = corruptAssets
            )

            if (ok) {
                val base = cache.getEntry(versionId) ?: LaunchCacheEntry(versionId)
                cache.putEntry(
                    base.copy(
                        versionJsonFingerprint = fingerprint,
                        verifiedLibraries = libraryPaths,
                        verifiedAssets = assetCount,
                        assetIndexFingerprint = indexFingerprint,
                        lastVerifiedAt = System.currentTimeMillis()
                    )
                )
                if (cached) cache.recordHit() else cache.recordMiss()
            }
            result
        }

    override suspend fun invalidate(versionId: String) {
        cache.removeEntry(versionId)
    }

    override suspend fun invalidateAll() = cache.clear()

    /**
     * Returns (indexOk, missing, corrupt, fingerprint, object count).
     * Object files are named by their SHA-1, so a size match is as good
     * as a hash match — no hashing here.
     */
    private suspend fun checkAssets(json: JSONObject): VerifyAssetsResult {
        val ref = VersionJson.assetIndex(json) ?: return VerifyAssetsResult(true, 0, 0, null, 0)
        val indexFile = storage.assetIndexFile(ref.id)
        if (!indexFile.isFile) return VerifyAssetsResult(false, 0, 0, null, 0)
        val fingerprint = Fingerprints.of(indexFile)
        val objects = runCatching {
            JSONObject(indexFile.readText()).optJSONObject("objects") ?: JSONObject()
        }.getOrDefault(JSONObject())

        var missing = 0
        var corrupt = 0
        objects.keys().forEach { key ->
            val obj = objects.optJSONObject(key) ?: return@forEach
            val hash = obj.optString("hash")
            val size = obj.optLong("size", 0L)
            if (hash.isEmpty()) return@forEach
            val file = storage.objectFile(hash)
            when {
                !file.isFile -> missing++
                file.length() != size -> corrupt++
            }
        }
        return VerifyAssetsResult(true, missing, corrupt, fingerprint, objects.length())
    }

    private fun versionJson(versionId: String): JSONObject? {
        val file = storage.versionJsonFile(versionId)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    /** Leaf-first inheritsFrom chain of version ids, or null when broken. */
    private fun loadChain(versionId: String): List<String>? {
        val result = mutableListOf<String>()
        var current = versionId
        val seen = mutableSetOf<String>()
        while (true) {
            if (!seen.add(current)) return null
            val file = storage.versionJsonFile(current)
            if (!file.isFile) return null
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
            result.add(current)
            val parent = json.optString("inheritsFrom").takeIf { it.isNotEmpty() } ?: break
            current = parent
        }
        return result
    }

    private suspend fun hashMatches(file: File, expected: String?): Boolean? {
        val sha = hash(file)
        return if (expected == null) null else sha == expected
    }

    private suspend fun hash(file: File): String {
        val buffer = buffers?.acquireBuffer(HASH_BUFFER)
        return try {
            if (buffer != null) HashUtils.sha1(file, buffer) else HashUtils.sha1(file)
        } finally {
            buffer?.let { buffers?.releaseBuffer(it) }
        }
    }

    private data class VerifyAssetsResult(
        val indexOk: Boolean,
        val missing: Int,
        val corrupt: Int,
        val fingerprint: String?,
        val objectCount: Int
    )

    private companion object {
        const val HASH_BUFFER = 16 * 1024
    }
}