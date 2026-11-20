package de.munichhenge.app.model

import de.munichhenge.engine.Grade
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

    /** Line width by grade: three clearly different widths instead of a smooth scale. */
    fun lineWidthDp(grade: Grade): Float = when (grade) {
        Grade.PERFECT -> UiConfig.MAP_LINE_PERFECT_DP
        Grade.GOOD -> UiConfig.MAP_LINE_GOOD_DP
        Grade.NEAR -> UiConfig.MAP_LINE_NEAR_DP
    }

    /** How much of the ring around a marker is drawn: the full circle for quality 1. */
    fun ringSweepDeg(quality: Double): Float =
        (360f * quality.coerceIn(0.0, 1.0).toFloat()).coerceAtLeast(UiConfig.MAP_RING_MIN_SWEEP_DEG)

    /** Quality rounded to MAP_RING_STEPS; markers are drawn once per step. */
    fun ringStep(quality: Double): Int =
        Math.round(quality.coerceIn(0.0, 1.0) * UiConfig.MAP_RING_STEPS).toInt()

    fun showBaseLines(zoom: Double): Boolean = zoom >= UiConfig.MAP_BASE_MIN_ZOOM

    fun showSpots(zoom: Double): Boolean = zoom >= UiConfig.MAP_SPOTS_MIN_ZOOM
}
