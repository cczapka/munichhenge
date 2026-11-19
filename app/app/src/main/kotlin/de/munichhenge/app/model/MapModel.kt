package de.munichhenge.app.model

import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Sightline

/** What the map draws for a day (PLAN-links-and-ui.md 2.9), without osmdroid. */
object MapModel {
    /** The best event per sightline that the filter passes, lowest score first so the best draws on top. */
    fun eventLines(events: List<HengeEvent>, filter: EventFilter, sightlineOf: (String) -> Sightline?): List<HengeEvent> =
        events.filter { filter.accepts(it, sightlineOf(it.sightlineId)) }
            .groupBy { it.sightlineId }
            .map { (_, es) -> es.maxWith(compareBy<HengeEvent> { it.score }.thenByDescending { it.tBest }) }
            .sortedWith(compareBy<HengeEvent> { it.score }.thenBy { it.sightlineId })

    fun lineWidthDp(quality: Double): Float =
        UiConfig.MAP_LINE_MIN_DP + (UiConfig.MAP_LINE_MAX_DP - UiConfig.MAP_LINE_MIN_DP) * quality.coerceIn(0.0, 1.0).toFloat()

    fun showBaseLines(zoom: Double): Boolean = zoom >= UiConfig.MAP_BASE_MIN_ZOOM

    fun showSpots(zoom: Double): Boolean = zoom >= UiConfig.MAP_SPOTS_MIN_ZOOM
}
