package de.munichhenge.solar

import de.munichhenge.solar.MunichReference.MUNICH
import de.munichhenge.solar.MunichReference.instant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SunPositionTest {
    @Test
    fun `julian day of J2000 epoch`() {
        assertEquals(2451545.0, SunPosition.julianDay(Instant.parse("2000-01-01T12:00:00Z")), 1e-6)
        assertEquals(2461041.5, SunPosition.julianDay(Instant.parse("2026-01-01T00:00:00Z")), 1e-6)
    }

    @Test
    fun `declination at the solstices and equinoxes`() {
        // 2026 June solstice 2026-06-21 08:24 UTC, December solstice 2026-12-21 20:50 UTC,
        // March equinox 2026-03-20 14:46 UTC (USNO). Obliquity 23.436°.
        assertEquals(23.436, SunPosition.at(Instant.parse("2026-06-21T08:24:00Z"), MUNICH).declinationDeg, 0.01)
        assertEquals(-23.436, SunPosition.at(Instant.parse("2026-12-21T20:50:00Z"), MUNICH).declinationDeg, 0.01)
        assertEquals(0.0, SunPosition.at(Instant.parse("2026-03-20T14:46:00Z"), MUNICH).declinationDeg, 0.02)
    }

    @Test
    fun `azimuth and apparent altitude match astral at arbitrary instants`() {
        for (p in MunichReference.POSITIONS) {
            val pos = SunPosition.at(instant(p.t), MUNICH)
            assertEquals(p.az, pos.azimuthDeg, 0.1, "azimuth at ${p.t}")
            // astral applies its own refraction model; near the horizon the models differ by a few 0.01°
            assertEquals(p.alt, pos.apparentAltitudeDeg, 0.15, "altitude at ${p.t}")
        }
    }

    @Test
    fun `apparent altitude equals geometric plus refraction`() {
        val pos = SunPosition.at(instant("2026-09-23T16:45:00Z"), MUNICH)
        assertEquals(pos.geometricAltitudeDeg + Refraction.forGeometricAltitude(pos.geometricAltitudeDeg),
            pos.apparentAltitudeDeg, 1e-9)
    }

    @Test
    fun `azimuth is normalized and sun is due south near transit`() {
        for (d in MunichReference.DAYS) {
            val pos = SunPosition.at(instant(d.noon), MUNICH)
            assertEquals(180.0, pos.azimuthDeg, 0.3, "noon azimuth on ${d.date}")
            assertEquals(d.noonAlt, pos.apparentAltitudeDeg, 0.05, "noon altitude on ${d.date}")
        }
    }
}
