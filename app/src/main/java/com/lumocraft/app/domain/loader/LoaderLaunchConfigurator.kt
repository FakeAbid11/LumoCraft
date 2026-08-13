package com.lumocraft.app.domain.loader

import com.lumocraft.app.domain.launch.LaunchContext
import java.io.File

/**
 * Launch adjustments a loader applies on top of the vanilla launch
 * configuration. An all-default value (vanilla) means "use the version
 * JSON as-is".
 *
 * [mainClass] overrides the version JSON main class; [libraries] are
 * appended to the classpath; [clientJar] replaces the vanilla client jar
 * entry (Fabric ships the patched jar as a library, so the Mojang
 * client jar is not downloaded); [jvmArguments] and [gameArguments] are
 * appended to the built argument lists.
 */
data class LoaderLaunchConfiguration(
    val type: LoaderType = LoaderType.VANILLA,
    val mainClass: String? = null,
    val libraries: List<File> = emptyList(),
    val clientJar: File? = null,
    val jvmArguments: List<String> = emptyList(),
    val gameArguments: List<String> = emptyList()
)

/**
 * Resolves which loader (if any) is active for a launch target.
 *
 * The [LaunchPipeline] asks `loader.configureLaunch(context)` without
 * knowing which loader is active: a composite implementation delegates
 * to the registered per-type configurators and falls back to vanilla.
 * Adding Quilt/Forge/NeoForge later only registers another
 * configurator — the pipeline is untouched.
 */
interface LoaderLaunchConfigurator {

    /**
     * Launch adjustments for [context.versionId]; vanilla defaults when
     * no loader applies. Fails when the active loader's files are
     * missing or unreadable.
     */
    suspend fun configureLaunch(context: LaunchContext): Result<LoaderLaunchConfiguration>

    /**
     * The file that acts as the client jar for [versionId], or null when
     * the version uses the standard `<versionDir>/<id>.jar` layout.
     * Used by validation so loader instances pass the client jar check.
     */
    suspend fun clientJarFor(versionId: String): File?
}