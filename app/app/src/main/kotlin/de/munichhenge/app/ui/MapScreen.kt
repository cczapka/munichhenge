package de.munichhenge.app.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.R
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Geo
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeData
import de.munichhenge.engine.HengeEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * osmdroid map: every sightline as a line (thick and coloured when it has an event on the
 * selected date), a sun-direction arrow at the viewing end of each event, and the spots
 * attached to today's events as markers.
 */
@Composable
fun MapScreen(vm: AppViewModel, onOpenSightline: (String) -> Unit, onOpenPoi: (String) -> Unit) {
    val context = LocalContext.current
    val events by vm.dayEvents.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val density = context.resources.displayMetrics.density

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(12.5)
            controller.setCenter(GeoPoint(48.14, 11.58))
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                drawOverlays(map, vm.data, events ?: emptyList(), density, onOpenSightline, onOpenPoi)
            },
        )
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.small,
        ) {
            val n = events?.count { !it.openHorizon }
            Text(
                if (n == null) "${Presentation.dateLabel(date)} · computing…"
                else "${Presentation.dateLabel(date)} · $n aligned",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun gradeColor(grade: Grade): Int = when (grade) {
    Grade.PERFECT -> AColor.rgb(46, 125, 50)
    Grade.GOOD -> AColor.rgb(249, 168, 37)
    Grade.NEAR -> AColor.rgb(158, 158, 158)
}

private fun drawOverlays(
    map: MapView, data: HengeData, events: List<HengeEvent>, density: Float,
    onOpenSightline: (String) -> Unit, onOpenPoi: (String) -> Unit,
) {
    map.overlays.clear()
    val best: Map<String, HengeEvent> = events.filter { !it.openHorizon }
        .groupBy { it.sightlineId }.mapValues { (_, es) -> es.maxBy { it.quality } }

    // lines: plain sightlines first so event lines draw on top
    for (sl in data.sightlines) {
        if (sl.openHorizon) continue
        val e = best[sl.id]
        val line = Polyline(map).apply {
            setPoints(listOf(GeoPoint(sl.a.lat, sl.a.lon), GeoPoint(sl.b.lat, sl.b.lon)))
            outlinePaint.strokeWidth = Presentation.strokeWidthPx(e?.quality, density)
            outlinePaint.color = if (e == null) AColor.argb(110, 27, 42, 65) else gradeColor(e.grade)
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            setOnClickListener { _, _, _ -> onOpenSightline(sl.id); true }
        }
        if (e == null) map.overlays.add(line) else map.overlays.add(map.overlays.size, line)
    }

    // sun direction at the viewing end: a short ray along the bearing (unambiguous) plus an arrow head
    val arrow = ContextCompat.getDrawable(map.context, R.drawable.ic_sun_arrow)
    for (e in best.values) {
        val sl = data.sightline(e.sightlineId) ?: continue
        val stand = sl.standingPoint(e.viewFrom)
        val rayEnd = Geo.destination(stand, e.bearing, (sl.lengthM * 0.35).coerceIn(80.0, 400.0))
        map.overlays.add(Polyline(map).apply {
            setPoints(listOf(GeoPoint(stand.lat, stand.lon), GeoPoint(rayEnd.lat, rayEnd.lon)))
            outlinePaint.strokeWidth = 5f * density
            outlinePaint.color = AColor.rgb(242, 177, 52)
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            setOnClickListener { _, _, _ -> onOpenSightline(sl.id); true }
        })
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(rayEnd.lat, rayEnd.lon)
            icon = arrow
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            rotation = -e.bearing.toFloat()   // osmdroid rotates counter-clockwise; bearings are clockwise
            title = sl.name ?: sl.id
            snippet = "${Presentation.localTime(e.tFull)} ${Presentation.modeWord(e.mode)} · ${Presentation.gradeWord(e.grade)}"
            setOnMarkerClickListener { _, _ -> onOpenSightline(sl.id); true }
        })
    }

    // spots attached to today's events
    val poiIds = best.values.flatMap { it.poiIds }.toSet()
    for (id in poiIds) {
        val p = data.poi(id) ?: continue
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(p.at.lat, p.at.lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = p.name ?: p.id
            snippet = p.kind
            setOnMarkerClickListener { _, _ -> onOpenPoi(p.id); true }
        })
    }
    map.invalidate()
}
