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
    fun `line width grows with quality`() {
        assertEquals(UiConfig.MAP_LINE_MIN_DP, MapModel.lineWidthDp(0.0))
        assertEquals(UiConfig.MAP_LINE_MAX_DP, MapModel.lineWidthDp(1.0))
        assertTrue(MapModel.lineWidthDp(0.5) in UiConfig.MAP_LINE_MIN_DP..UiConfig.MAP_LINE_MAX_DP)
    }

    @Test
    fun `background streets and spots only when zoomed in`() {
        assertTrue(!MapModel.showBaseLines(13.0))
        assertTrue(MapModel.showBaseLines(UiConfig.MAP_BASE_MIN_ZOOM))
        assertTrue(!MapModel.showSpots(UiConfig.MAP_BASE_MIN_ZOOM - 0.5))
        assertTrue(MapModel.showSpots(UiConfig.MAP_SPOTS_MIN_ZOOM))
    }
}
