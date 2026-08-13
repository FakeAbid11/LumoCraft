package com.lumocraft.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lumocraft.app.domain.model.ThemeMode
import com.lumocraft.app.ui.accounts.AccountsScreen
import com.lumocraft.app.ui.home.HomeScreen
import com.lumocraft.app.ui.input.ControlsPreviewScreen
import com.lumocraft.app.ui.input.LayoutEditorScreen
import com.lumocraft.app.ui.launch.LaunchScreen
import com.lumocraft.app.ui.loader.LoaderManagerScreen
import com.lumocraft.app.ui.performance.PerformanceDashboardScreen
import com.lumocraft.app.ui.settings.SettingsScreen
import com.lumocraft.app.ui.versions.VersionsScreen

/**
 * NavHost wiring for all top-level destinations plus the plain routes
 * (launch, input preview, layout editor) that are not part of
 * [LumoDestination], so the bottom bar is hidden there.
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
            VersionsScreen(
                onOpenLoaders = { navController.navigate(LOADERS_ROUTE) }
            )
        }
        composable(LOADERS_ROUTE) {
            LoaderManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(LumoDestination.SETTINGS.route) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onEditLayout = { navController.navigate(INPUT_LAYOUT_ROUTE) },
                onPreviewControls = { navController.navigate(INPUT_PREVIEW_ROUTE) },
                onOpenPerformance = { navController.navigate(PERFORMANCE_ROUTE) }
            )
        }
        composable(PERFORMANCE_ROUTE) {
            PerformanceDashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(LAUNCH_ROUTE) {
            LaunchScreen()
        }
        composable(INPUT_PREVIEW_ROUTE) {
            ControlsPreviewScreen(
                onEditLayout = { navController.navigate(INPUT_LAYOUT_ROUTE) },
                onExit = { navController.popBackStack() }
            )
        }
        composable(INPUT_LAYOUT_ROUTE) {
            LayoutEditorScreen(
                onDone = { navController.popBackStack() }
            )
        }
    }
}

private const val LAUNCH_ROUTE = "launch"
private const val INPUT_PREVIEW_ROUTE = "input/preview"
private const val INPUT_LAYOUT_ROUTE = "input/layout"
private const val PERFORMANCE_ROUTE = "performance"
private const val LOADERS_ROUTE = "loaders"