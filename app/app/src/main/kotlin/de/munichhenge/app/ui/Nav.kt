package de.munichhenge.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val TODAY = "today"
    const val UPCOMING = "upcoming"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val SIGHTLINE = "sightline/{id}"
    const val POI = "poi/{id}"
    fun sightline(id: String) = "sightline/$id"
    fun poi(id: String) = "poi/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.TODAY, "Today", Icons.Filled.Today),
    Tab(Routes.UPCOMING, "Upcoming", Icons.Filled.Upcoming),
    Tab(Routes.MAP, "Map", Icons.Filled.Map),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (tab in TABS) {
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navigateToTab(nav, tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(vm, onOpenSightline = { nav.navigate(Routes.sightline(it)) })
            }
            composable(Routes.UPCOMING) {
                UpcomingScreen(vm, onOpenSightline = { nav.navigate(Routes.sightline(it)) })
            }
            composable(Routes.MAP) {
                MapScreen(vm, onOpenSightline = { nav.navigate(Routes.sightline(it)) },
                    onOpenPoi = { nav.navigate(Routes.poi(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            composable(Routes.SIGHTLINE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                SightlineDetailScreen(vm, id = entry.arguments?.getString("id") ?: "",
                    onOpenPoi = { nav.navigate(Routes.poi(it)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.POI, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                PoiDetailScreen(vm, id = entry.arguments?.getString("id") ?: "",
                    onOpenSightline = { nav.navigate(Routes.sightline(it)) }, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun navigateToTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
