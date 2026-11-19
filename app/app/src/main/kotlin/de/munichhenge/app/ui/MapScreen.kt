package de.munichhenge.app.ui

import android.graphics.Paint
import android.graphics.Color as AColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.R
import de.munichhenge.app.model.MapModel
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.HengeData
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Muted OSM basemaps (CARTO), so the coloured event lines stand out; dark variant in dark mode. */
private fun cartoTiles(dark: Boolean) = XYTileSource(
    if (dark) "CartoDarkMatter" else "CartoPositron", 0, 20, 256, ".png",
    arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/${if (dark) "dark_all" else "light_all"}/" }.toTypedArray(),
    "© OpenStreetMap contributors © CARTO",
)

/**
 * The selected day on a map (PLAN-links-and-ui.md 2.9): each event as a line coloured by
 * sunrise or sunset (thicker = better) with a marker where you stand. All other streets and
 * the spots appear only when zoomed in. Tapping a line or marker shows its card.
 */
@Composable
fun MapScreen(vm: AppViewModel, onOpenSightline: (String, Endpoint?) -> Unit, onOpenPoi: (String) -> Unit) {
    val context = LocalContext.current
    val all by vm.dayEvents.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val density = context.resources.displayMetrics.density

    val lines = remember(all, filter) { all?.let { MapModel.eventLines(it, filter, vm::sightline) } }
    var selected by remember { mutableStateOf<HengeEvent?>(null) }
    LaunchedEffect(date) { selected = null }
    var showBase by remember { mutableStateOf(false) }
    var showSpots by remember { mutableStateOf(false) }
    val openSightline by rememberUpdatedState(onOpenSightline)
    val openPoi by rememberUpdatedState(onOpenPoi)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(cartoTiles(dark))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(12.5)
            controller.setCenter(GeoPoint(48.14, 11.58))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?) = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    val z = event?.zoomLevel ?: return false
                    if (MapModel.showBaseLines(z) != showBase) showBase = MapModel.showBaseLines(z)
                    if (MapModel.showSpots(z) != showSpots) showSpots = MapModel.showSpots(z)
                    return false
                }
            })
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // layers: built once (streets) or when the day's lines change (events, spots)
    val tapEmpty = remember(mapView) {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { selected = null; return false }
            override fun longPressHelper(p: GeoPoint?) = false
        })
    }
    val baseLayer = remember(mapView, dark) { baseLayer(mapView, vm.data, dark, density) { openSightline(it, null) } }
    val eventLayer = remember(mapView, lines) { eventLayer(mapView, vm.data, lines.orEmpty(), density) { selected = it } }
    val spotLayer = remember(mapView, lines) { spotLayer(mapView, vm.data, lines.orEmpty()) { openPoi(it) } }
    val copyright = remember(mapView) { CopyrightOverlay(context) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()
                map.overlays.add(tapEmpty)
                if (showBase) map.overlays.add(baseLayer)
                map.overlays.add(eventLayer)
                if (showSpots) map.overlays.add(spotLayer)
                map.overlays.add(copyright)
                map.invalidate()
            },
        )
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 2.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftDate(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "previous day") }
                    Text(
                        Presentation.dateLabel(date) + (lines?.let { " · ${it.size}" } ?: " · …"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(onClick = { vm.shiftDate(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "next day") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeIcon(Mode.SUNRISE, size = 18.dp)
                    Text(" sunrise   ", style = MaterialTheme.typography.labelMedium)
                    ModeIcon(Mode.SUNSET, size = 18.dp)
                    Text(" sunset   · thicker = better", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        selected?.let { e ->
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp)) {
                EventCard(vm, e, vm.sightline(e.sightlineId), now, onOpen = { onOpenSightline(e.sightlineId, e.viewFrom) })
            }
        }
    }
}

private fun modeColor(mode: Mode): Int = (if (mode == Mode.SUNSET) ModeColors.sunset else ModeColors.sunrise).toArgb()

private fun Polyline.style(color: Int, widthPx: Float) {
    outlinePaint.color = color
    outlinePaint.strokeWidth = widthPx
    outlinePaint.strokeCap = Paint.Cap.ROUND
}

/** Every sightline, faint; shown from MapModel.showBaseLines on. */
private fun baseLayer(map: MapView, data: HengeData, dark: Boolean, density: Float, onTap: (String) -> Unit) =
    FolderOverlay().apply {
        val color = if (dark) AColor.argb(120, 200, 200, 210) else AColor.argb(110, 27, 42, 65)
        for (sl in data.sightlines) {
            if (sl.openHorizon) continue
            add(Polyline(map).apply {
                setPoints(listOf(GeoPoint(sl.a.lat, sl.a.lon), GeoPoint(sl.b.lat, sl.b.lon)))
                style(color, 2f * density)
                setOnClickListener { _, _, _ -> onTap(sl.id); true }
            })
        }
    }

/** The day's events: a white-cased line in the mode colour and a marker where you stand. */
private fun eventLayer(map: MapView, data: HengeData, events: List<HengeEvent>, density: Float, onTap: (HengeEvent) -> Unit) =
    FolderOverlay().apply {
        val rise = ContextCompat.getDrawable(map.context, R.drawable.ic_map_sunrise)
        val set = ContextCompat.getDrawable(map.context, R.drawable.ic_map_sunset)
        for (e in events) {                       // lowest score first: the best ends on top
            val sl = data.sightline(e.sightlineId) ?: continue
            val pts = listOf(GeoPoint(sl.a.lat, sl.a.lon), GeoPoint(sl.b.lat, sl.b.lon))
            val w = MapModel.lineWidthDp(e.quality) * density
            add(Polyline(map).apply { setPoints(pts); style(AColor.argb(230, 255, 255, 255), w + 3f * density) })
            add(Polyline(map).apply {
                setPoints(pts)
                style(modeColor(e.mode), w)
                setOnClickListener { _, _, _ -> onTap(e); true }
            })
            val stand = sl.standingPoint(e.viewFrom)
            add(Marker(map).apply {
                position = GeoPoint(stand.lat, stand.lon)
                icon = if (e.mode == Mode.SUNSET) set else rise
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onTap(e); true }
            })
        }
    }

/** Spots looking down today's lines, as small dots; shown from MapModel.showSpots on. */
private fun spotLayer(map: MapView, data: HengeData, events: List<HengeEvent>, onTap: (String) -> Unit) =
    FolderOverlay().apply {
        val dot = ContextCompat.getDrawable(map.context, R.drawable.ic_spot)
        for (id in events.flatMap { it.poiIds }.toSortedSet()) {
            val p = data.poi(id) ?: continue
            add(Marker(map).apply {
                position = GeoPoint(p.at.lat, p.at.lon)
                icon = dot
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = Presentation.poiName(p)
                setOnMarkerClickListener { _, _ -> onTap(p.id); true }
            })
        }
    }
