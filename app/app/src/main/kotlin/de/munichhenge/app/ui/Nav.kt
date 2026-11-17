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
import de.munichhenge.engine.Endpoint

object Routes {
    const val TODAY = "today"
    const val UPCOMING = "upcoming"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val SIGHTLINE = "sightline/{id}?from={from}"
    const val POI = "poi/{id}"
    /** `from` = the end the user stands at for the event they tapped; that card comes first. */
    fun sightline(id: String, from: Endpoint? = null) = if (from == null) "sightline/$id" else "sightline/$id?from=${from.name}"
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
                TodayScreen(vm, onOpenSightline = { id, from -> nav.navigate(Routes.sightline(id, from)) })
            }
            composable(Routes.UPCOMING) {
                UpcomingScreen(vm, onOpenSightline = { id, from -> nav.navigate(Routes.sightline(id, from)) })
            }
            composable(Routes.MAP) {
                MapScreen(vm, onOpenSightline = { nav.navigate(Routes.sightline(it)) },
                    onOpenPoi = { nav.navigate(Routes.poi(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            composable(
                Routes.SIGHTLINE,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val from = entry.arguments?.getString("from")?.let { s -> Endpoint.entries.firstOrNull { it.name == s } }
                SightlineDetailScreen(vm, id = entry.arguments?.getString("id") ?: "", from = from,
                    onOpenPoi = { nav.navigate(Routes.poi(it)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.POI, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                PoiDetailScreen(vm, id = entry.arguments?.getString("id") ?: "",
                    onOpenSightline = { id, from -> nav.navigate(Routes.sightline(id, from)) }, onBack = { nav.popBackStack() })
            }
        }
    }
}

private val TAB_ROUTES = TABS.map { it.route }.toSet()

private fun navigateToTab(nav: NavHostController, route: String) {
    // Leave detail screens first: otherwise they are saved with the tab's state and come
    // back on top of it, so the tab tap seems to do nothing.
    while (nav.currentDestination?.route?.let { it !in TAB_ROUTES } == true) {
        if (!nav.popBackStack()) break
    }
    if (nav.currentDestination?.route == route) return
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
