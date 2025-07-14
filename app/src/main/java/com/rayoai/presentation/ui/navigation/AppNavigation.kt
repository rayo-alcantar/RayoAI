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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rayoai.presentation.ui.screens.about.AboutScreen
import com.rayoai.presentation.ui.screens.history.HistoryScreen
import com.rayoai.presentation.ui.screens.home.HomeScreen
import com.rayoai.presentation.ui.screens.settings.SettingsScreen

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home?captureId={captureId}", "Inicio", Icons.Default.Home) {
        fun createRoute(captureId: Long?) = if (captureId != null) "home?captureId=$captureId" else "home"
    }
    object History : Screen("history", "Historial", Icons.Default.History)
    object About : Screen("about", "Acerca de", Icons.Default.Info)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.History,
    Screen.About,
    Screen.Settings
)

@Composable
fun AppNavigation(imageUri: Uri?) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = screen.label }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
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
            composable(Screen.About.route) {
                AboutScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}