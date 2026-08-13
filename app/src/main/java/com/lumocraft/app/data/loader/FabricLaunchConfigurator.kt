package com.lumocraft.app.data.loader

import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.VersionJson
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchException
import com.lumocraft.app.domain.loader.LoaderLaunchConfiguration
import com.lumocraft.app.domain.loader.LoaderLaunchConfigurator
import com.lumocraft.app.domain.loader.LoaderRepository
import com.lumocraft.app.domain.loader.LoaderType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * [LoaderLaunchConfigurator] for Fabric.
 *
 * Reads the installed instance's profile JSON and turns it into launch
 * adjustments: the Knot main class, the Fabric libraries (maven layout
 * inside `libraries/`), the patched client jar (the `net.fabricmc:minecraft`
 * artifact replaces the Mojang client jar) and the loader game arguments.
 * Vanilla versions resolve to an all-default configuration.
 */
class FabricLaunchConfigurator(
    private val storage: StorageManager,
    private val loaderRepository: LoaderRepository,
) : LoaderLaunchConfigurator {

    override suspend fun configureLaunch(context: LaunchContext): Result<LoaderLaunchConfiguration> =
        withContext(Dispatchers.IO) {
            val instance = loaderRepository.resolveActiveLoader(context.versionId)
                ?: return@withContext Result.success(LoaderLaunchConfiguration())
            if (instance.metadata.type != LoaderType.FABRIC) {
                return@withContext Result.success(LoaderLaunchConfiguration())
            }
            val jsonFile = storage.versionJsonFile(instance.instanceId)
            val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull()
                ?: return@withContext Result.failure(
                    LaunchException("Fabric profile JSON missing for '${instance.instanceId}'")
                )

            val refs = VersionJson.libraries(json)
            val minecraftJar = File(
                storage.librariesDirectory(),
                "net/fabricmc/minecraft/${instance.metadata.minecraftVersion}/" +
                    "minecraft-${instance.metadata.minecraftVersion}.jar"
            )
            val libraries = refs
                .filter { ref -> storage.libraryFile(ref.path) != minecraftJar }
                .map { ref -> storage.libraryFile(ref.path) }
            val missing = libraries.filter { !it.isFile }
            if (missing.isNotEmpty()) {
                return@withContext Result.failure(
                    LaunchException(
                        "Fabric libraries missing: ${missing.take(MAX_REPORTED).joinToString(", ")} " +
                            "— repair the loader in the Loader Manager"
                    )
                )
            }
            if (!minecraftJar.isFile) {
                return@withContext Result.failure(
                    LaunchException(
                        "Fabric client jar missing (${minecraftJar.name}) — repair the loader"
                    )
                )
            }

            Result.success(
                LoaderLaunchConfiguration(
                    type = LoaderType.FABRIC,
                    mainClass = json.optString("mainClass").takeIf { it.isNotEmpty() },
                    libraries = libraries,
                    clientJar = minecraftJar,
                    gameArguments = gameArguments(json)
                )
            )
        }

    override suspend fun clientJarFor(versionId: String): File? {
        val instance = loaderRepository.resolveActiveLoader(versionId) ?: return null
        if (instance.metadata.type != LoaderType.FABRIC) return null
        return File(
            storage.librariesDirectory(),
            "net/fabricmc/minecraft/${instance.metadata.minecraftVersion}/" +
                "minecraft-${instance.metadata.minecraftVersion}.jar"
        )
    }

    /** Loader arguments declared by the profile (`--fabric.gameVersion`, ...). */
    private fun gameArguments(json: JSONObject): List<String> {
        val game = json.optJSONObject("arguments")?.optJSONArray("game") ?: return emptyList()
        return buildList {
            for (i in 0 until game.length()) {
                game.opt(i)?.let { if (it is String) add(it) }
            }
        }
    }

    private companion object {
        const val MAX_REPORTED = 10
    }
}