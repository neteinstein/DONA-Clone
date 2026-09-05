package com.neteinstein.donaclone.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neteinstein.donaclone.feature.ambiences.AmbiencesRoute
import com.neteinstein.donaclone.feature.devices.DevicesRoute
import com.neteinstein.donaclone.feature.devices.SensorsRoute
import com.neteinstein.donaclone.feature.settings.SettingsRoute

/** The 4 Google-Home-style bottom nav destinations hosted inside [MainScreen]. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home),
    SENSORS("sensors", "Sensors", Icons.Filled.Sensors),
    AUTOMATIONS("automations", "Automations", Icons.Filled.PlayCircle),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/**
 * The post-login shell: a persistent bottom [DonaBottomBar] over a nested [NavHost], mirroring
 * Google Home's Home/Sensors/Automations/Settings tabs. Each tab keeps its own back stack/scroll state
 * via [androidx.navigation.NavOptionsBuilder.saveState]/`restoreState`. [DEVICE_DETAIL] is
 * deliberately not one of these tabs — it's pushed on the outer [DonaNavHost] instead, full
 * screen over the bottom nav.
 */
@Composable
fun MainScreen(
    onOpenDeviceDetail: (Int) -> Unit,
    onOpenHouses: () -> Unit,
    onLoggedOut: () -> Unit,
    onCreateAutomation: () -> Unit,
    onOpenAutomationDetail: (Int) -> Unit,
) {
    val innerNavController = rememberNavController()

    Scaffold(
        // Each tab hosted below already handles its own top inset via its own Scaffold +
        // TopAppBar — exclude the status bar here, or every tab's top bar ends up pushed down
        // by its height a second time (mirrors the same fix in MainActivity's outer Scaffold).
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.statusBars),
        bottomBar = {
            val backStackEntry by innerNavController.currentBackStackEntryAsState()
            DonaBottomBar(
                currentDestination = backStackEntry?.destination,
                onNavigate = { destination ->
                    innerNavController.navigate(destination.route) {
                        popUpTo(innerNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
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
            composable(TopLevelDestination.SENSORS.route) {
                SensorsRoute(onOpenDeviceDetail = onOpenDeviceDetail)
            }
            composable(TopLevelDestination.AUTOMATIONS.route) {
                AmbiencesRoute(
                    onCreateAutomation = onCreateAutomation,
                    onOpenAutomationDetail = onOpenAutomationDetail,
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsRoute(onManageHouses = onOpenHouses, onLoggedOut = onLoggedOut)
            }
        }
    }
}

/** A custom bottom bar (replacing Material3's stock `NavigationBar`/`NavigationBarItem`) whose
 * selected-item highlight fills the bar's full height edge-to-edge, rather than a small centered
 * pill with empty space above/below it. */
@Composable
private fun DonaBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding(),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            val containerColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "bottom-nav-item-color",
            )
            val contentColor =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(destination) },
                        )
                        .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(destination.icon, contentDescription = destination.label, tint = contentColor)
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
