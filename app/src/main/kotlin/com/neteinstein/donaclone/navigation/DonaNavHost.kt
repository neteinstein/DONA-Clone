package com.neteinstein.donaclone.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neteinstein.donaclone.feature.ambiences.AmbiencesRoute
import com.neteinstein.donaclone.feature.dashboard.DashboardRoute
import com.neteinstein.donaclone.feature.dashboard.dashboardEnterTransition
import com.neteinstein.donaclone.feature.devices.DevicesRoute
import com.neteinstein.donaclone.feature.houses.HousesRoute
import com.neteinstein.donaclone.feature.login.LoginRoute
import com.neteinstein.donaclone.feature.settings.SettingsRoute

object DonaDestinations {
    const val LOGIN = "login"
    const val HOUSES = "houses"
    const val DASHBOARD = "dashboard"
    const val DEVICES = "devices"
    const val AMBIENCES = "ambiences"
    const val SETTINGS = "settings"
}

@Composable
fun DonaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = DonaDestinations.LOGIN,
        enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 8 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 8 } },
    ) {
        composable(DonaDestinations.LOGIN) {
            LoginRoute(
                onLoggedIn = {
                    navController.navigate(DonaDestinations.DASHBOARD) {
                        popUpTo(DonaDestinations.LOGIN) { inclusive = true }
                    }
                },
                onManageHouses = { navController.navigate(DonaDestinations.HOUSES) },
            )
        }

        composable(DonaDestinations.HOUSES) {
            HousesRoute(onDone = { navController.popBackStack() })
        }

        composable(
            DonaDestinations.DASHBOARD,
            enterTransition = { dashboardEnterTransition() },
        ) {
            DashboardRoute(
                onOpenDevices = { navController.navigate(DonaDestinations.DEVICES) },
                onOpenAmbiences = { navController.navigate(DonaDestinations.AMBIENCES) },
                onOpenSettings = { navController.navigate(DonaDestinations.SETTINGS) },
                onLoggedOut = { navigateToLogin(navController) },
            )
        }

        composable(DonaDestinations.DEVICES) {
            DevicesRoute(onBack = { navController.popBackStack() })
        }

        composable(DonaDestinations.AMBIENCES) {
            AmbiencesRoute(onBack = { navController.popBackStack() })
        }

        composable(DonaDestinations.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onManageHouses = { navController.navigate(DonaDestinations.HOUSES) },
                onLoggedOut = { navigateToLogin(navController) },
            )
        }
    }
}

private fun navigateToLogin(navController: NavHostController) {
    navController.navigate(DonaDestinations.LOGIN) {
        popUpTo(DonaDestinations.DASHBOARD) { inclusive = true }
    }
}
