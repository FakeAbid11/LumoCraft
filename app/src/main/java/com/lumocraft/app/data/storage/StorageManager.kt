package com.lumocraft.app.data.storage

import android.content.Context
import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.domain.loader.LoaderMetadata
import com.lumocraft.app.domain.loader.LoaderType
import com.lumocraft.app.domain.version.InstallState
import com.lumocraft.app.domain.version.InstalledVersionMetadata
import java.io.File
import org.json.JSONObject

/**
 * Owns the on-disk launcher layout. All file paths and metadata
 * serialization live here, never in the UI.
 *
 * Layout:
 * <filesDir>/minecraft/
 * ├── versions/
 * │   └── <versionId>/
 * │       ├── <versionId>.json
 * │       ├── metadata.json
 * │       └── <loggingId>.json          (logging configuration)
 * ├── libraries/                        (Mojang folder structure preserved)
 * ├── assets/
 * │   ├── indexes/<indexId>.json
 * │   └── objects/<hash[0..2]>/<hash>
 * ├── loader/                          (mod loader registry)
 * │   └── <loaderType>/
 * │       ├── instances/<instanceId>/metadata.json
 * │       └── cache/                   (fetched metadata service responses)
 * ├── logs/                            (future: launcher/game logs)
 * └── runtime/                         (Java runtimes)
 *     ├── java17/
 *     ├── java21/
 *     └── metadata.json
 */
class StorageManager(context: Context) {

    private val launcherRoot: File = File(context.filesDir, AppConfig.LAUNCHER_DIRECTORY_NAME)

    fun launcherRoot(): File = launcherRoot

    fun versionsDirectory(): File = File(launcherRoot, "versions")

    fun versionDirectory(versionId: String): File =
        File(versionsDirectory(), sanitize(versionId))

    fun versionJsonFile(versionId: String): File =
        File(versionDirectory(versionId), "${sanitize(versionId)}.json")

    fun metadataFile(versionId: String): File =
        File(versionDirectory(versionId), METADATA_FILE_NAME)

    fun assetsDirectory(): File = File(launcherRoot, "assets")

    fun indexesDirectory(): File = File(assetsDirectory(), "indexes")

    fun objectsDirectory(): File = File(assetsDirectory(), "objects")

    fun librariesDirectory(): File = File(launcherRoot, "libraries")

    fun logsDirectory(): File = File(launcherRoot, "logs")

    fun loaderDirectory(type: LoaderType): File =
        File(launcherRoot, "loader").let { root ->
            File(root, sanitize(type.id))
        }

    fun loaderInstancesDirectory(type: LoaderType): File =
        File(loaderDirectory(type), "instances")

    fun loaderInstanceDirectory(type: LoaderType, instanceId: String): File =
        File(loaderInstancesDirectory(type), sanitize(instanceId))

    fun loaderInstanceMetadataFile(type: LoaderType, instanceId: String): File =
        File(loaderInstanceDirectory(type, instanceId), METADATA_FILE_NAME)

    /** Cache of metadata service responses, shared per loader type. */
    fun loaderCacheDirectory(type: LoaderType): File =
        File(loaderDirectory(type), "cache")

    fun inputDirectory(): File = File(launcherRoot, "input")

    fun inputProfilesDirectory(): File = File(inputDirectory(), "profiles")

    fun runtimeDirectory(): File = File(launcherRoot, "runtime")

    fun runtimeMetadataFile(): File = File(runtimeDirectory(), "metadata.json")

    fun runtimeDirectoryFor(id: String): File =
        File(runtimeDirectory(), sanitize(id))

    fun assetIndexFile(indexId: String): File =
        File(indexesDirectory(), "${sanitize(indexId)}.json")

    /** Object storage location: <objects>/<first two hash chars>/<hash>. */
    fun objectFile(hash: String): File =
        File(File(objectsDirectory(), hash.take(2)), hash)

    /**
     * Resolves a Mojang-relative library path inside `libraries/`, rejecting
     * traversal segments so manifest data can never escape the root.
     */
    fun libraryFile(relativePath: String): File {
        val segments = relativePath.split('/').mapNotNull { segment ->
            segment.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        }
        return segments.fold(librariesDirectory()) { dir, name -> File(dir, name) }
    }

    /** Logging configuration, stored inside the version folder. */
    fun loggingConfigFile(versionId: String, fileName: String): File =
        File(versionDirectory(versionId), sanitize(fileName))

    /** Creates the full launcher layout. Safe to call repeatedly. */
    fun prepareDirectories() {
        listOf(
            versionsDirectory(),
            librariesDirectory(),
            indexesDirectory(),
            objectsDirectory(),
            logsDirectory(),
            runtimeDirectory(),
            inputProfilesDirectory()
        ).forEach { it.mkdirs() }
        LoaderType.entries.forEach { type ->
            loaderInstancesDirectory(type).mkdirs()
            loaderCacheDirectory(type).mkdirs()
        }
    }

    fun readMetadata(versionId: String): InstalledVersionMetadata? {
        val file = metadataFile(versionId)
        if (!file.isFile) return null
        return runCatching {
            val obj = JSONObject(file.readText())
            InstalledVersionMetadata(
                version = obj.getString(KEY_VERSION),
                installedAt = obj.getLong(KEY_INSTALLED_AT),
                source = obj.optString(KEY_SOURCE),
                installerVersion = obj.optInt(KEY_INSTALLER_VERSION, 0),
                state = InstallState.valueOf(obj.getString(KEY_STATE))
            )
        }.getOrNull()
    }

    fun writeMetadata(metadata: InstalledVersionMetadata) {
        val dir = versionDirectory(metadata.version)
        dir.mkdirs()
        val json = JSONObject()
            .put(KEY_VERSION, metadata.version)
            .put(KEY_INSTALLED_AT, metadata.installedAt)
            .put(KEY_SOURCE, metadata.source)
            .put(KEY_INSTALLER_VERSION, metadata.installerVersion)
            .put(KEY_STATE, metadata.state.name)
        metadataFile(metadata.version).writeText(json.toString())
    }

    /** Scans all version directories and returns id -> state. */
    fun readInstallStates(): Map<String, InstallState> {
        val dir = versionsDirectory()
        if (!dir.isDirectory) return emptyMap()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { entry -> readMetadata(entry.name)?.let { it.version to it.state } }
            ?.toMap()
            ?: emptyMap()
    }

    /** Removes a version directory (version JSON, jar, metadata, logging). */
    fun removeVersionDirectory(versionId: String): Boolean {
        val dir = versionDirectory(versionId)
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    fun readLoaderMetadata(type: LoaderType, instanceId: String): LoaderMetadata? {
        val file = loaderInstanceMetadataFile(type, instanceId)
        if (!file.isFile) return null
        return runCatching {
            val obj = JSONObject(file.readText())
            LoaderMetadata(
                instanceId = obj.getString(KEY_LOADER_INSTANCE_ID),
                type = LoaderType.fromId(obj.getString(KEY_LOADER_TYPE)) ?: return null,
                minecraftVersion = obj.getString(KEY_LOADER_MINECRAFT_VERSION),
                loaderVersion = obj.getString(KEY_LOADER_VERSION),
                installerVersion = obj.getString(KEY_LOADER_INSTALLER_VERSION),
                installedAt = obj.getLong(KEY_LOADER_INSTALLED_AT),
                state = InstallState.valueOf(obj.getString(KEY_STATE))
            )
        }.getOrNull()
    }

    fun writeLoaderMetadata(metadata: LoaderMetadata) {
        val dir = loaderInstanceDirectory(metadata.type, metadata.instanceId)
        dir.mkdirs()
        val json = JSONObject()
            .put(KEY_LOADER_INSTANCE_ID, metadata.instanceId)
            .put(KEY_LOADER_TYPE, metadata.type.id)
            .put(KEY_LOADER_MINECRAFT_VERSION, metadata.minecraftVersion)
            .put(KEY_LOADER_VERSION, metadata.loaderVersion)
            .put(KEY_LOADER_INSTALLER_VERSION, metadata.installerVersion)
            .put(KEY_LOADER_INSTALLED_AT, metadata.installedAt)
            .put(KEY_STATE, metadata.state.name)
        loaderInstanceMetadataFile(metadata.type, metadata.instanceId).writeText(json.toString())
    }

    fun removeLoaderMetadata(type: LoaderType, instanceId: String): Boolean {
        val dir = loaderInstanceDirectory(type, instanceId)
        return dir.deleteRecursively()
    }

    /** All loader metadata files of one type, keyed by instance id. */
    fun readAllLoaderMetadata(type: LoaderType): Map<String, LoaderMetadata> {
        val dir = loaderInstancesDirectory(type)
        if (!dir.isDirectory) return emptyMap()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { entry ->
                readLoaderMetadata(type, entry.name)?.let { it.instanceId to it }
            }
            ?.toMap()
            ?: emptyMap()
    }

    /** Version ids become file/directory names; keep them filesystem-safe. */
    private fun sanitize(versionId: String): String =
        versionId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val METADATA_FILE_NAME = "metadata.json"
        const val KEY_VERSION = "version"
        const val KEY_INSTALLED_AT = "installedAt"
        const val KEY_SOURCE = "source"
        const val KEY_INSTALLER_VERSION = "installerVersion"
        const val KEY_STATE = "state"
        const val KEY_LOADER_INSTANCE_ID = "instanceId"
        const val KEY_LOADER_TYPE = "type"
        const val KEY_LOADER_MINECRAFT_VERSION = "minecraftVersion"
        const val KEY_LOADER_VERSION = "loaderVersion"
        const val KEY_LOADER_INSTALLER_VERSION = "installerVersion"
        const val KEY_LOADER_INSTALLED_AT = "installedAt"
    }
}