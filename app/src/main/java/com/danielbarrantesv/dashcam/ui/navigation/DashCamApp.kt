package com.danielbarrantesv.dashcam.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.danielbarrantesv.dashcam.R
import com.danielbarrantesv.dashcam.ui.camera.CameraScreen
import com.danielbarrantesv.dashcam.ui.reports.ReportsScreen
import com.danielbarrantesv.dashcam.ui.settings.SettingsScreen
import com.danielbarrantesv.dashcam.ui.transfer.TransferScreen
import com.danielbarrantesv.dashcam.ui.trips.TripsScreen

object Routes {
    const val CAMERA = "camera"
    const val TRIPS = "trips"
    const val REPORTS = "reports"
    const val TRANSFER = "transfer"
    const val SETTINGS = "settings"
}

private data class BottomItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.CAMERA, R.string.nav_camera, Icons.Filled.PhotoCamera),
    BottomItem(Routes.TRIPS, R.string.nav_trips, Icons.Filled.VideoLibrary),
    BottomItem(Routes.REPORTS, R.string.nav_reports, Icons.Filled.Insights),
    BottomItem(Routes.TRANSFER, R.string.nav_transfer, Icons.Filled.Wifi),
    BottomItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun DashCamApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomItems.forEach { item ->
                    val selected = current == item.route ||
                        backStack?.destination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                        label = { Text(stringResource(item.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAMERA,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CAMERA) { CameraScreen() }
            composable(Routes.TRIPS) { TripsScreen() }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.TRANSFER) { TransferScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
