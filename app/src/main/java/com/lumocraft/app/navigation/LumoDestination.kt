package com.lumocraft.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.lumocraft.app.R

/**
 * Top-level destinations of the launcher. Routes are plain strings so that
 * future screens (launch session, profile editor, logs, ...) can be added
 * without touching this enum.
 */
enum class LumoDestination(
    val route: String,
    @StringRes val labelResource: Int,
    val icon: ImageVector
) {
    HOME(route = "home", labelResource = R.string.nav_home, icon = Icons.Filled.Home),
    ACCOUNTS(route = "accounts", labelResource = R.string.nav_accounts, icon = Icons.Filled.Person),
    VERSIONS(route = "versions", labelResource = R.string.nav_versions, icon = Icons.Filled.List),
    SETTINGS(route = "settings", labelResource = R.string.nav_settings, icon = Icons.Filled.Settings);

    companion object {
        fun fromRoute(route: String?): LumoDestination? =
            entries.firstOrNull { it.route == route }
    }
}