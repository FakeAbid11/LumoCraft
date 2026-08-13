package com.lumocraft.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lumocraft.app.domain.model.ThemeMode
import com.lumocraft.app.ui.accounts.AccountsScreen
import com.lumocraft.app.ui.home.HomeScreen
import com.lumocraft.app.ui.launch.LaunchScreen
import com.lumocraft.app.ui.settings.SettingsScreen
import com.lumocraft.app.ui.versions.VersionsScreen

/**
 * NavHost wiring for all top-level destinations plus the plain launch
 * route (not part of [LumoDestination], so the bottom bar is hidden).
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
            HomeScreen(
                onPlay = { navController.navigate(LAUNCH_ROUTE) }
            )
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
        composable(LAUNCH_ROUTE) {
            LaunchScreen()
        }
    }
}

private const val LAUNCH_ROUTE = "launch"