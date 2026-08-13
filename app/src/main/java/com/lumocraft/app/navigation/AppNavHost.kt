package com.lumocraft.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lumocraft.app.domain.model.ThemeMode
import com.lumocraft.app.ui.accounts.AccountsScreen
import com.lumocraft.app.ui.home.HomeScreen
import com.lumocraft.app.ui.settings.SettingsScreen
import com.lumocraft.app.ui.versions.VersionsScreen

/**
 * NavHost wiring for all top-level destinations.
 * Leaf screens are registered here; deeper routes (launch flow, profile
 * details) will extend this host in later stages.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = LumoDestination.HOME.route,
        modifier = modifier
    ) {
        composable(LumoDestination.HOME.route) {
            HomeScreen()
        }
        composable(LumoDestination.ACCOUNTS.route) {
            AccountsScreen()
        }
        composable(LumoDestination.VERSIONS.route) {
            VersionsScreen()
        }
        composable(LumoDestination.SETTINGS.route) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
    }
}