package com.neteinstein.donaclone.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neteinstein.donaclone.feature.ambiences.AmbiencesRoute
import com.neteinstein.donaclone.feature.devices.DevicesRoute
import com.neteinstein.donaclone.feature.settings.SettingsRoute

/** The 3 Google-Home-style bottom nav destinations hosted inside [MainScreen]. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home),
    AUTOMATIONS("automations", "Automations", Icons.Filled.PlayCircle),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/**
 * The post-login shell: a persistent bottom [NavigationBar] over a nested [NavHost], mirroring
 * Google Home's Home/Automations/Settings tabs. Each tab keeps its own back stack/scroll state
 * via [androidx.navigation.NavOptionsBuilder.saveState]/`restoreState`. [DEVICE_DETAIL] is
 * deliberately not one of these tabs — it's pushed on the outer [DonaNavHost] instead, full
 * screen over the bottom nav.
 */
@Composable
fun MainScreen(
    onOpenDeviceDetail: (Int) -> Unit,
    onOpenHouses: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val innerNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by innerNavController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                TopLevelDestination.entries.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            innerNavController.navigate(destination.route) {
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = innerNavController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                DevicesRoute(onOpenDeviceDetail = onOpenDeviceDetail, onLoggedOut = onLoggedOut)
            }
            composable(TopLevelDestination.AUTOMATIONS.route) {
                AmbiencesRoute()
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsRoute(onManageHouses = onOpenHouses, onLoggedOut = onLoggedOut)
            }
        }
    }
}
