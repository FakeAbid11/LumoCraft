package com.lumocraft.app.domain.loader

/**
 * The game loaders the launcher knows about. [VANILLA] is the implicit
 * "no loader" profile; every other entry is a pluggable loader that is
 * installed on top of a Minecraft version.
 *
 * Future loaders (Quilt, Forge, NeoForge) are added here plus a
 * [LoaderInstaller] and a [LoaderLaunchConfigurator] per type — nothing
 * in the launch pipeline or the UI changes.
 */
enum class LoaderType(val id: String, val displayName: String) {
    VANILLA(id = "vanilla", displayName = "Vanilla"),
    FABRIC(id = "fabric", displayName = "Fabric");

    companion object {
        fun fromId(id: String): LoaderType? = entries.firstOrNull { it.id == id }
    }
}