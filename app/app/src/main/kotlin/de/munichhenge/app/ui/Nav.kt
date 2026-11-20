package de.munichhenge.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** "Show on map" from any card; null where the card already sits on the map. */
val LocalShowOnMap = staticCompositionLocalOf<((MapFocus) -> Unit)?> { null }

object Routes {
    const val DAY = "day"
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

/** Day · Upcoming · Map (Places joins in M9); Settings sits behind the gear in the top bar. */
private val TABS = listOf(
    Tab(Routes.DAY, "Day", Icons.Filled.Today),
    Tab(Routes.UPCOMING, "Upcoming", Icons.Filled.Upcoming),
    Tab(Routes.MAP, "Map", Icons.Filled.Map),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val filter by vm.filter.collectAsStateWithLifecycle()
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val tab = TABS.firstOrNull { it.route == currentRoute }

    Scaffold(
        topBar = {
            if (tab != null) {
                // The tab is named in the bottom bar; the top bar carries the filter, so nothing wraps.
                TopAppBar(
                    title = {
                        AssistChip(
                            onClick = { showFilter = true },
                            label = { Text(filter.summary(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                    },
                    actions = {
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                for (t in TABS) {
                    NavigationBarItem(
                        selected = currentRoute == t.route,
                        onClick = { navigateToTab(nav, t.route) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
      CompositionLocalProvider(LocalShowOnMap provides { f: MapFocus -> vm.showOnMap(f); navigateToTab(nav, Routes.MAP) }) {
        NavHost(nav, startDestination = Routes.DAY, modifier = Modifier.padding(padding)) {
            composable(Routes.DAY) {
                DayScreen(vm, onOpenSightline = { id, from -> nav.navigate(Routes.sightline(id, from)) })
            }
            composable(Routes.UPCOMING) {
                UpcomingScreen(vm, onOpenSightline = { id, from -> nav.navigate(Routes.sightline(id, from)) })
            }
            composable(Routes.MAP) {
                MapScreen(vm, onOpenSightline = { id, from -> nav.navigate(Routes.sightline(id, from)) },
                    onOpenPoi = { nav.navigate(Routes.poi(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
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

    if (showFilter) {
        FilterSheet(filter, onChange = vm::updateFilter, onDismiss = { showFilter = false })
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
