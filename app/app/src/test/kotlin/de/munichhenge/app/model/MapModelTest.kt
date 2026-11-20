package de.munichhenge.app.model

import de.munichhenge.engine.Grade
import de.munichhenge.engine.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapModelTest {
    private val sl = Fx.sightline()
    private val of = { _: String -> sl }

    @Test
    fun `one line per sightline, its best event, best drawn last`() {
        val events = listOf(
            Fx.event(id = "a", mode = Mode.SUNRISE, score = 0.6, t = "2026-11-19T06:40:00Z"),
            Fx.event(id = "a", mode = Mode.SUNSET, score = 0.9),
            Fx.event(id = "b", score = 0.7),
            Fx.event(id = "c", score = 0.99, grade = Grade.NEAR),   // filtered out
            Fx.event(id = "olympiaberg", score = 1.0, open = true),  // never a line
        )
        val lines = MapModel.eventLines(events, EventFilter(), of)
        assertEquals(listOf("b" to Mode.SUNSET, "a" to Mode.SUNSET), lines.map { it.sightlineId to it.mode })
    }

    @Test
    fun `line width follows the grade, visibly`() {
        val p = MapModel.lineWidthDp(Grade.PERFECT)
        val g = MapModel.lineWidthDp(Grade.GOOD)
        val n = MapModel.lineWidthDp(Grade.NEAR)
        assertTrue(p - g >= 2f && g - n >= 2f, "$p / $g / $n")
    }

    @Test
    fun `quality ring sweeps with quality and never vanishes`() {
        assertEquals(360f, MapModel.ringSweepDeg(1.0))
        assertEquals(UiConfig.MAP_RING_MIN_SWEEP_DEG, MapModel.ringSweepDeg(0.0))
        assertEquals(180f, MapModel.ringSweepDeg(0.5))
        assertEquals(360f, MapModel.ringSweepDeg(1.7))
        // markers are cached per step, so nearby qualities share one drawable
        assertEquals(MapModel.ringStep(0.81), MapModel.ringStep(0.83))
        assertTrue(MapModel.ringStep(0.5) < MapModel.ringStep(0.9))
    }

    @Test
    fun `background streets and spots only when zoomed in`() {
        assertTrue(!MapModel.showBaseLines(13.0))
        assertTrue(MapModel.showBaseLines(UiConfig.MAP_BASE_MIN_ZOOM))
        assertTrue(!MapModel.showSpots(UiConfig.MAP_BASE_MIN_ZOOM - 0.5))
        assertTrue(MapModel.showSpots(UiConfig.MAP_SPOTS_MIN_ZOOM))
    }
}
