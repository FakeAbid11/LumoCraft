package com.lumocraft.app.data.loader

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VerificationService
import com.lumocraft.app.domain.loader.LoaderInstance
import com.lumocraft.app.domain.loader.LoaderStatus
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.version.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects the health of every installed loader instance of a type.
 *
 * The version directory and loader metadata must exist; otherwise the
 * instance is [LoaderStatus.MISSING]. Pending/failed metadata states are
 * reported as-is; anything else is verified with the shared
 * [VerificationService] (loader profile JSON, libraries, metadata) and
 * marked [LoaderStatus.INSTALLED] or [LoaderStatus.CORRUPTED].
 */
class LoaderScanner(
    private val storage: StorageManager,
    private val verificationService: VerificationService,
) {

    suspend fun scanInstances(type: LoaderType): List<LoaderInstance> =
        withContext(Dispatchers.IO) {
            val metadata = storage.readAllLoaderMetadata(type)
            metadata.values.map { meta ->
                val status = when {
                    meta.state == InstallState.PENDING -> LoaderStatus.PENDING
                    meta.state == InstallState.FAILED -> LoaderStatus.FAILED
                    !storage.versionDirectory(meta.instanceId).isDirectory ->
                        LoaderStatus.MISSING
                    else -> {
                        val report = verificationService.scan(meta.instanceId)
                        when {
                            !report.versionJsonOk || !report.metadataOk -> LoaderStatus.MISSING
                            report.ok -> LoaderStatus.INSTALLED
                            else -> LoaderStatus.CORRUPTED
                        }
                    }
                }
                LoaderInstance(meta, status)
            }.sortedBy { it.metadata.minecraftVersion }
        }
}