package de.munichhenge.app.model

import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationTest {
    private fun event(date: LocalDate, quality: Double, bearing: Double = 270.0, t: String = "2026-09-20T17:05:00Z",
                      open: Boolean = false) = HengeEvent(
        sightlineId = "sl_x", viewFrom = Endpoint.A, bearing = bearing, mode = Mode.SUNSET, date = date,
        tFull = Instant.parse(t), tHalf = Instant.parse(t).plusSeconds(120), azimuthAtFull = bearing,
        alignmentErrorDeg = (1 - quality) * 3, quality = quality, grade = Grade.GOOD, poiIds = listOf("p1", "p2"),
        openHorizon = open,
    )

    @Test
    fun `compass names at the band boundaries`() {
        assertEquals("N", Presentation.compass16(0.0))
        assertEquals("N", Presentation.compass16(359.0))
        assertEquals("NNE", Presentation.compass16(11.3))
        assertEquals("E", Presentation.compass16(90.0))
        assertEquals("W", Presentation.compass16(270.0))
        assertEquals("W", Presentation.compass16(281.0))
        assertEquals("WNW", Presentation.compass16(282.0))
        assertEquals("WNW", Presentation.compass16(292.5))
        assertEquals("NW", Presentation.compass16(310.0))
        assertEquals("east", Presentation.compass8Word(90.0))
        assertEquals("south-west", Presentation.compass8Word(233.0))
    }

    @Test
    fun `standing end is opposite to the viewing direction`() {
        assertEquals("east", Presentation.standingEndWord(270.0))
        assertEquals("west", Presentation.standingEndWord(90.0))
        assertEquals("south-east", Presentation.standingEndWord(300.0))
        assertEquals("Stand at the east end, look W", Presentation.instruction(event(LocalDate.of(2026, 9, 20), 0.9)))
        assertEquals("Stand at the south-east end, look WNW", Presentation.instruction(event(LocalDate.of(2026, 9, 20), 0.9, 300.0)))
        assertEquals("Open horizon, look W", Presentation.instruction(event(LocalDate.of(2026, 9, 20), 1.0, open = true)))
    }

    @Test
    fun `local time and date labels are Europe Berlin`() {
        assertEquals("19:05", Presentation.localTime(Instant.parse("2026-09-20T17:05:00Z")))   // CEST
        assertEquals("16:22", Presentation.localTime(Instant.parse("2026-12-21T15:22:00Z")))   // CET
        assertEquals("Sun, 20 Sep 2026", Presentation.dateLabel(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun `grouping by day keeps date order and ranks within a day`() {
        val d1 = LocalDate.of(2026, 9, 21)
        val d2 = LocalDate.of(2026, 9, 20)
        val grouped = Presentation.groupByDay(listOf(event(d1, 0.5), event(d2, 0.7, t = "2026-09-20T17:10:00Z"),
            event(d2, 0.9), event(d2, 0.7, t = "2026-09-20T17:00:00Z")))
        assertEquals(listOf(d2, d1), grouped.map { it.first })
        assertEquals(listOf(0.9, 0.7, 0.7), grouped[0].second.map { it.quality })
        assertEquals("2026-09-20T17:00:00Z", grouped[0].second[1].tFull.toString())
    }

    @Test
    fun `stroke width grows with quality and is thin without an event`() {
        assertEquals(4f, Presentation.strokeWidthPx(null, 2f))
        assertEquals(6f, Presentation.strokeWidthPx(0.0, 2f))
        assertEquals(24f, Presentation.strokeWidthPx(1.0, 2f))
    }

    @Test
    fun `labels`() {
        assertEquals("no spots", Presentation.spotsLabel(0))
        assertEquals("1 spot", Presentation.spotsLabel(1))
        assertEquals("14 spots", Presentation.spotsLabel(14))
        assertEquals("perfect", Presentation.gradeWord(Grade.PERFECT))
        assertEquals("sunset", Presentation.modeWord(Mode.SUNSET))
    }
}
