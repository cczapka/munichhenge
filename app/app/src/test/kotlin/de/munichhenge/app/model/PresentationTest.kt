package de.munichhenge.app.model

import de.munichhenge.engine.Confidence
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Sightline
import de.munichhenge.engine.Style
import de.munichhenge.solar.GeoPoint
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationTest {
    private fun event(date: LocalDate, quality: Double, bearing: Double = 270.0, t: String = "2026-09-20T17:05:00Z",
                      open: Boolean = false) = HengeEvent(
        sightlineId = "sl_x", viewFrom = Endpoint.A, bearing = bearing, mode = Mode.SUNSET, date = date,
        tBest = Instant.parse(t), windowStart = Instant.parse(t).minusSeconds(120), windowEnd = Instant.parse(t).plusSeconds(180),
        style = Style.ON_HORIZON, sunAzimuthDeg = bearing, sunAltitudeDeg = 1.3, offsetDeg = 0.0, heightAboveFloorDeg = 0.3,
        discFraction = 1.0, centring = 0.0, stepRightM = null, treesInWay = false, confidence = Confidence.ESTIMATED,
        quality = quality, interest = 0.5, score = quality, grade = Grade.GOOD, poiIds = listOf("p1", "p2"),
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
        assertEquals("2026-09-20T17:00:00Z", grouped[0].second[1].tBest.toString())
    }

    @Test
    fun `stroke width grows with quality and is thin without an event`() {
        assertEquals(4f, Presentation.strokeWidthPx(null, 2f))
        assertEquals(6f, Presentation.strokeWidthPx(0.0, 2f))
        assertEquals(24f, Presentation.strokeWidthPx(1.0, 2f))
    }

    @Test
    fun `open horizon footer line`() {
        val d = LocalDate.of(2026, 9, 20)
        val open = event(d, 1.0, open = true).copy(sightlineId = "sl_open_hill", grade = Grade.PERFECT)
        val sunrise = open.copy(mode = Mode.SUNRISE, bearing = 88.0, tBest = Instant.parse("2026-09-20T05:02:00Z"))
        assertEquals("Hill: sunrise 07:02 (88°), sunset 19:05 (270°)",
            Presentation.openHorizonLine(listOf(open, sunrise)) { "Hill" })
    }

    private val street = Sightline(
        id = "sl_x", name = "Teststraße", kind = "street", a = GeoPoint(48.15, 11.55), b = GeoPoint(48.1505, 11.57),
        lengthM = 1486.0, maxOffsetM = 0.0, bearingAb = 287.0, bearingBa = 107.0,
        obstructionTowardADeg = 0.62, obstructionTowardBDeg = 1.13, canyon = true, featured = false, notes = null,
        osmWayIds = emptyList(),
    )

    @Test
    fun `direction card text for both ends`() {
        assertEquals("Stand at the east end, look WNW (287°)", Presentation.directionTitle(street, Endpoint.A))
        assertEquals("Stand at the west end, look ESE (107°)", Presentation.directionTitle(street, Endpoint.B))
        assertEquals("Far end 1486 m away, skyline there 1.1° up", Presentation.directionDetail(street, Endpoint.A, false))
        assertEquals("Far end 1486 m away, skyline there 0.6° up", Presentation.directionDetail(street, Endpoint.B, false))
        assertTrue(Presentation.directionDetail(street, Endpoint.A, true).endsWith("Street View shows the nearest bank or bridge."))
        assertEquals("Open horizon", Presentation.directionTitle(street.copy(bearingAb = null, bearingBa = null), Endpoint.A))
    }

    @Test
    fun `time line shows the best moment, the window and the style`() {
        val e = event(LocalDate.of(2026, 9, 20), 0.9)                      // tBest 17:05Z = 19:05 CEST
        assertEquals("19:03–19:08", Presentation.windowLabel(e))
        assertEquals("19:05 sunset · 19:03–19:08 · on the horizon", Presentation.timeLine(e))
        assertEquals("between the buildings", Presentation.styleWord(Style.IN_CANYON))
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
