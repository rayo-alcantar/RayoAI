package com.rayoai.presentation.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.rayoai.presentation.ui.updates.UpdateFlowDialog
import com.rayoai.BuildConfig
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
    object Tools : Screen("tools", "tools", R.string.tab_tools, Icons.Default.Build)
    object ScanPdf : Screen("scan_pdf", "tools")
    object ViewPdf : Screen("view_pdf?id={id}", "tools") {
        fun createRoute(id: Long) = "view_pdf?id=$id"
    }
    object PdfChat : Screen("pdf_chat?id={id}", "tools") {
        fun createRoute(id: Long) = "pdf_chat?id=$id"
    }
    object ScanVideo : Screen("scan_video", "tools")
    object ViewVideo : Screen("view_video?id={id}", "tools") {
        fun createRoute(id: Long) = "view_video?id=$id"
    }
    object About : Screen("about?showDonationDialog={showDonationDialog}", "about", R.string.tab_about, Icons.Default.Info) {
        fun createRoute(showDonationDialog: Boolean = false) = "about?showDonationDialog=$showDonationDialog"
    }
    object Settings : Screen("settings", "settings", R.string.tab_settings, Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.History,
    Screen.Tools,
    Screen.About,
    Screen.Settings
)

private fun NavDestination?.isSameRouteAs(baseRoute: String): Boolean {
    // Devuelve true si la ruta del destino (o alguna de sus jerarquías) comienza con el baseRoute
    return this?.hierarchy?.any { dest -> dest.route?.startsWith(baseRoute) == true } == true
}

@Composable
fun AppNavigation(imageUri: Uri?, startDestination: String, pdfUri: Uri? = null, videoUri: Uri? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activity = (LocalContext.current as? Activity)
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    if (BuildConfig.GITHUB_UPDATES_ENABLED) {
        UpdateFlowDialog()
    }

    LaunchedEffect(pdfUri) {
        if (pdfUri != null) {
            pendingPdfUri = pdfUri
            navController.navigate(Screen.ScanPdf.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            pendingVideoUri = videoUri
            navController.navigate(Screen.ScanVideo.route) {
                launchSingleTop = true
            }
        }
    }

    

    Scaffold(
        bottomBar = {
            val showBottomBar = items.any { currentDestination.isSameRouteAs(it.baseRoute) }
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { screen ->
                        val contentDesc = screen.labelRes?.let { stringResource(it) } ?: ""
                        val selectedNavColor = Color(0xFF00B8FF)
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = null) } },
                            label = { screen.labelRes?.let { Text(stringResource(it)) } },
                            selected = currentDestination.isSameRouteAs(screen.baseRoute),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = selectedNavColor,
                                indicatorColor = selectedNavColor,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
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
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { captureId ->
                        navController.navigate(Screen.Home.createRoute(captureId))
                    }
                )
            }
            composable(Screen.Tools.route) {
                com.rayoai.presentation.ui.screens.tools.ToolsScreen(
                    onScanPdf = { navController.navigate(Screen.ScanPdf.route) },
                    onScanVideo = { navController.navigate(Screen.ScanVideo.route) },
                    onOpenVideo = { video ->
                        navController.navigate(Screen.ViewVideo.createRoute(video.id))
                    }
                )
            }
            composable(Screen.ScanPdf.route) {
                com.rayoai.presentation.ui.screens.tools.ScanPdfScreen(
                    incomingPdfUri = pendingPdfUri,
                    onPdfConsumed = { pendingPdfUri = null },
                    onNavigateBack = { navController.popBackStack() },
                    onOpenProcessed = { doc ->
                        navController.navigate(Screen.ViewPdf.createRoute(doc.id))
                    },
                    onChatPdf = { doc ->
                        navController.navigate(Screen.PdfChat.createRoute(doc.id))
                    }
                )
            }
            composable(
                route = Screen.ViewPdf.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                com.rayoai.presentation.ui.screens.tools.PdfResultScreen(
                    docId = id,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.PdfChat.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) {
                com.rayoai.presentation.ui.screens.tools.PdfChatScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ScanVideo.route) {
                com.rayoai.presentation.ui.screens.tools.ScanVideoScreen(
                    incomingVideoUri = pendingVideoUri,
                    onVideoConsumed = { pendingVideoUri = null },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ViewVideo.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                com.rayoai.presentation.ui.screens.tools.VideoResultScreen(
                    videoId = id,
                    onNavigateBack = { navController.popBackStack() }
                )
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
