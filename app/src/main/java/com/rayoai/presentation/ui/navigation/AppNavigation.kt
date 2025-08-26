package com.rayoai.presentation.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rayoai.presentation.ui.screens.about.AboutScreen
import com.rayoai.presentation.ui.screens.api_instructions.ApiInstructionsScreen
import com.rayoai.presentation.ui.screens.history.HistoryScreen
import com.rayoai.presentation.ui.screens.home.HomeScreen
import com.rayoai.presentation.ui.screens.settings.SettingsScreen
import com.rayoai.presentation.ui.screens.welcome.WelcomeScreen
import com.rayoai.R

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.app.Activity

sealed class Screen(val route: String, val baseRoute: String, val labelRes: Int? = null, val icon: ImageVector? = null) {
    object Welcome : Screen("welcome", "welcome")
    object ApiInstructions : Screen("api_instructions", "api_instructions")
    object Home : Screen("home?captureId={captureId}", "home", R.string.tab_home, Icons.Default.Home) {
        fun createRoute(captureId: Long?) = if (captureId != null) "home?captureId=$captureId" else "home"
    }
    object History : Screen("history", "history", R.string.tab_history, Icons.Default.History)
    object About : Screen("about?showDonationDialog={showDonationDialog}", "about", R.string.tab_about, Icons.Default.Info) {
        fun createRoute(showDonationDialog: Boolean = false) = "about?showDonationDialog=$showDonationDialog"
    }
    object Settings : Screen("settings", "settings", R.string.tab_settings, Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.History,
    Screen.About,
    Screen.Settings
)

private fun NavDestination?.isSameRouteAs(baseRoute: String): Boolean {
    // Devuelve true si la ruta del destino (o alguna de sus jerarquías) comienza con el baseRoute
    return this?.hierarchy?.any { dest -> dest.route?.startsWith(baseRoute) == true } == true
}

@Composable
fun AppNavigation(imageUri: Uri?, startDestination: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activity = (LocalContext.current as? Activity)

    BackHandler(enabled = true) {
        if (currentDestination?.route?.startsWith(Screen.Home.baseRoute) == false) {
            navController.navigate(Screen.Home.createRoute(null)) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        } else {
            activity?.finish()
        }
    }

    Scaffold(
        bottomBar = {
            val showBottomBar = items.any { currentDestination.isSameRouteAs(it.baseRoute) }
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { screen ->
                        val contentDesc = screen.labelRes?.let { stringResource(it) } ?: ""
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = null) } },
                            label = { screen.labelRes?.let { Text(stringResource(it)) } },
                            selected = currentDestination.isSameRouteAs(screen.baseRoute),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = contentDesc
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Home.route,
                arguments = listOf(navArgument("captureId") {
                    type = NavType.StringType
                    nullable = true
                })
            ) { backStackEntry ->
                val captureId = backStackEntry.arguments?.getString("captureId")?.toLongOrNull()
                HomeScreen(
                    navController = navController,
                    imageUri = imageUri,
                    captureId = captureId
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(navController = navController)
            }
            composable(
                route = Screen.About.route,
                arguments = listOf(navArgument("showDonationDialog") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val showDonationDialog = backStackEntry.arguments?.getBoolean("showDonationDialog") ?: false
                AboutScreen(showDonationDialogInitially = showDonationDialog)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(Screen.Welcome.route) {
                WelcomeScreen(navController = navController)
            }
            composable(Screen.ApiInstructions.route) {
                ApiInstructionsScreen(navController = navController)
            }
        }
    }
}