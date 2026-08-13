package com.lumocraft.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lumocraft.app.core.theme.LumoCraftTheme
import com.lumocraft.app.data.preferences.AppThemePreference
import com.lumocraft.app.domain.model.ThemeMode
import com.lumocraft.app.navigation.AppNavHost
import com.lumocraft.app.navigation.LumoDestination
import com.lumocraft.app.ui.components.LumoNavigationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoCraftApp() {
    val context = LocalContext.current
    val themePreference = remember { AppThemePreference(context) }
    var themeMode by rememberSaveable { mutableStateOf(themePreference.loadThemeMode()) }

    LumoCraftTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = LumoDestination.fromRoute(backStackEntry?.destination?.route)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentDestination?.labelResource?.let { context.getString(it) }
                                ?: context.getString(com.lumocraft.app.R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (currentDestination != null) {
                    LumoNavigationBar(
                        currentDestination = currentDestination,
                        onDestinationSelected = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                themeMode = themeMode,
                onThemeModeChange = { newMode ->
                    themeMode = newMode
                    themePreference.saveThemeMode(newMode)
                }
            )
        }
    }
}