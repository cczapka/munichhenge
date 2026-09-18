package de.munichhenge.app.notify

import de.munichhenge.app.weather.CloudCover
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestTest {
    private val d = LocalDate.of(2026, 9, 21)
    private fun ev(id: String, q: Double, t: String = "2026-09-21T17:04:00Z", open: Boolean = false) = HengeEvent(
        sightlineId = id, viewFrom = Endpoint.A, bearing = 270.0, mode = Mode.SUNSET, date = d, tFull = Instant.parse(t),
        tHalf = Instant.parse(t).plusSeconds(100), azimuthAtFull = 270.0, alignmentErrorDeg = (1 - q) * 3, quality = q,
        grade = if (q > 0.83) Grade.PERFECT else if (q > 0.5) Grade.GOOD else Grade.NEAR, poiIds = emptyList(), openHorizon = open,
    )

    @Test
    fun `selects the top three by quality, dropping low quality, cloudy and open horizon`() {
        val events = listOf(ev("a", 0.95), ev("b", 0.9), ev("cloudy", 0.99), ev("c", 0.8), ev("d", 0.75), ev("low", 0.5),
            ev("open", 1.0, open = true))
        val clouds = mapOf("cloudy" to CloudCover(80, 70), "a" to CloudCover(10, 0))
        val chosen = Digest.select(events, minQuality = 0.7) { clouds[it.sightlineId] }
        assertEquals(listOf("a", "b", "c"), chosen.map { it.sightlineId })
    }

    @Test
    fun `text has a title and one line per event with the cloud hint`() {
        val (title, body) = Digest.text(d, listOf(ev("x", 0.95), ev("y", 0.8, "2026-09-21T05:10:00Z")),
            { mapOf("x" to "Landwehrstraße", "y" to "Einsteinstraße")[it] }) { if (it.sightlineId == "x") CloudCover(20, 0) else null }
        assertEquals("2 henge moments tomorrow (Mon, 21 Sep 2026)", title)
        assertEquals("19:04 sunset · Landwehrstraße · perfect · ☀ 20%\n07:10 sunset · Einsteinstraße · good", body)
        assertEquals("Henge tomorrow: Landwehrstraße", Digest.text(d, listOf(ev("x", 0.95)), { "Landwehrstraße" }) { null }.first)
    }

    @Test
    fun `delay to the next 10 o clock local`() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals(Duration.ofHours(2), Digest.delayUntilNextRun(ZonedDateTime.of(2026, 9, 21, 8, 0, 0, 0, zone), zone))
        assertEquals(Duration.ofHours(23), Digest.delayUntilNextRun(ZonedDateTime.of(2026, 9, 21, 11, 0, 0, 0, zone), zone))
        assertEquals(Duration.ofHours(24), Digest.delayUntilNextRun(ZonedDateTime.of(2026, 9, 21, 10, 0, 0, 0, zone), zone))
        // called in UTC: 09:00Z is 11:00 CEST -> 23 h
        assertEquals(Duration.ofHours(23), Digest.delayUntilNextRun(ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, ZoneId.of("UTC")), zone))
    }
}
