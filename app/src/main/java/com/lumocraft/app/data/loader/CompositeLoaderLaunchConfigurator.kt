package com.lumocraft.app.data.loader

import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.loader.LoaderLaunchConfiguration
import com.lumocraft.app.domain.loader.LoaderLaunchConfigurator
import com.lumocraft.app.domain.loader.LoaderType
import java.io.File

/**
 * Routes a launch target to its active loader's configurator.
 *
 * The [com.lumocraft.app.domain.launch.LaunchPipeline] only sees this
 * composite: `loader.configureLaunch(context)` resolves the loader by
 * version id and delegates to the registered per-type configurators.
 * Vanilla versions and unknown targets fall back to an all-default
 * (no-op) configuration. Future loaders register their own configurator
 * here — the pipeline never changes.
 */
class CompositeLoaderLaunchConfigurator(
    private val configurators: List<LoaderLaunchConfigurator>,
) : LoaderLaunchConfigurator {

    override suspend fun configureLaunch(context: LaunchContext): Result<LoaderLaunchConfiguration> {
        for (configurator in configurators) {
            // Each configurator returns an all-default (vanilla) config for
            // versions it does not claim; only claimed versions can fail.
            val result = configurator.configureLaunch(context)
            if (result.isFailure) return result
            if (result.getOrThrow().type != LoaderType.VANILLA) return result
        }
        return Result.success(LoaderLaunchConfiguration())
    }

    override suspend fun clientJarFor(versionId: String): File? {
        for (configurator in configurators) {
            val file = configurator.clientJarFor(versionId) ?: continue
            return file
        }
        return null
    }
}