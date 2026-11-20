package de.munichhenge.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color as AColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import de.munichhenge.app.model.UiConfig
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.HengeData
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Standard OSM tiles, muted on the phone so the coloured event lines stand out: washed out
 * and lightened in light mode, inverted and dimmed in dark mode. (CARTO's pale basemaps now
 * need an API key: the emulator tour showed "API KEY REQUIRED" tiles.)
 */
private fun mutedTiles(dark: Boolean): ColorMatrixColorFilter {
    val m = ColorMatrix().apply { setSaturation(0.2f) }
    if (dark) {
        m.postConcat(ColorMatrix(floatArrayOf(      // invert, then dim to a dark grey
            -0.75f, 0f, 0f, 0f, 215f,
            0f, -0.75f, 0f, 0f, 215f,
            0f, 0f, -0.75f, 0f, 215f,
            0f, 0f, 0f, 1f, 0f,
        )))
    } else {
        m.postConcat(ColorMatrix(floatArrayOf(      // lighten toward white
            0.7f, 0f, 0f, 0f, 75f,
            0f, 0.7f, 0f, 0f, 75f,
            0f, 0f, 0.7f, 0f, 75f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    return ColorMatrixColorFilter(m)
}

/**
 * The selected day on a map (PLAN-links-and-ui.md 2.9): each event as a line coloured by
 * sunrise (blue) or sunset (violet), wider for a better grade, with a marker where you stand
 * whose ring shows today's quality. Sunrise / sunset chips filter in place. All other streets
 * and the spots appear only when zoomed in. Tapping a line or marker shows its card; "Show on
 * map" elsewhere lands here centred on that sightline. "My location" asks for permission only
 * when pressed; the position never leaves the phone.
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
    val focus by vm.mapFocus.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<HengeEvent?>(null) }
    LaunchedEffect(date) { selected = null }
    var showBase by remember { mutableStateOf(false) }
    var showSpots by remember { mutableStateOf(false) }
    val openSightline by rememberUpdatedState(onOpenSightline)
    val openPoi by rememberUpdatedState(onOpenPoi)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            overlayManager.tilesOverlay.setColorFilter(mutedTiles(dark))
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
    val myLocation = remember(mapView) { MyLocationNewOverlay(GpsMyLocationProvider(context), mapView) }
    var locating by remember { mutableStateOf(false) }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            myLocation.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // "Show on map" from a card or a detail screen: centre on the sightline, open the event's card
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        vm.sightline(f.sightlineId)?.let { sl ->
            mapView.controller.setZoom(UiConfig.MAP_FOCUS_ZOOM)
            mapView.controller.animateTo(GeoPoint((sl.a.lat + sl.b.lat) / 2, (sl.a.lon + sl.b.lon) / 2))
        }
        selected = f.event
        vm.mapFocus.value = null
    }

    fun goToMyLocation() {
        myLocation.enableMyLocation()
        locating = true
        myLocation.runOnFirstFix {
            mapView.post {
                myLocation.myLocation?.let {
                    mapView.controller.setZoom(UiConfig.MAP_FOCUS_ZOOM)
                    mapView.controller.animateTo(it)
                }
            }
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) goToMyLocation()
        else Toast.makeText(context, "Without location access the map cannot show where you are", Toast.LENGTH_SHORT).show()
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
                if (locating) map.overlays.add(myLocation)
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
                        Presentation.dateLabel(date) + (lines?.let { " · ${if (it.size == 1) "1 event" else "${it.size} events"}" } ?: " · …"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(onClick = { vm.shiftDate(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "next day") }
                }
                // the chips are the legend and the quick filter at once
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)) {
                    for (m in listOf(Mode.SUNRISE, Mode.SUNSET)) {
                        FilterChip(
                            selected = m in filter.modes,
                            onClick = { vm.updateFilter { it.toggleMode(m) } },
                            label = { Text(Presentation.modeWord(m)) },
                            leadingIcon = { ModeIcon(m, size = 18.dp) },
                        )
                    }
                    Text("ring = quality", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SmallFloatingActionButton(
            onClick = {
                val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                if (granted) goToMyLocation()
                else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = if (selected != null) 230.dp else 28.dp),
        ) { Icon(Icons.Filled.MyLocation, contentDescription = "My location") }
        selected?.let { e ->
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp)) {
                CompositionLocalProvider(LocalShowOnMap provides null) {      // already on the map
                    EventCard(vm, e, vm.sightline(e.sightlineId), now, onOpen = { onOpenSightline(e.sightlineId, e.viewFrom) })
                }
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
        for (e in events) {                       // lowest score first: the best ends on top
            val sl = data.sightline(e.sightlineId) ?: continue
            val pts = listOf(GeoPoint(sl.a.lat, sl.a.lon), GeoPoint(sl.b.lat, sl.b.lon))
            val w = MapModel.lineWidthDp(e.grade) * density
            add(Polyline(map).apply { setPoints(pts); style(AColor.argb(230, 255, 255, 255), w + 3f * density) })
            add(Polyline(map).apply {
                setPoints(pts)
                style(modeColor(e.mode), w)
                setOnClickListener { _, _, _ -> onTap(e); true }
            })
            val stand = sl.standingPoint(e.viewFrom)
            add(Marker(map).apply {
                position = GeoPoint(stand.lat, stand.lon)
                icon = QualityMarker.of(map.context, e.mode, e.quality)
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
