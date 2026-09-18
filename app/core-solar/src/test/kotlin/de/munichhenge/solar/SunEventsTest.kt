package de.munichhenge.solar

import de.munichhenge.solar.MunichReference.MUNICH
import de.munichhenge.solar.MunichReference.instant
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SunEventsTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun assertWithin(expected: String, actual: java.time.Instant?, minutes: Long, what: String) {
        assertNotNull(actual, what)
        val diff = Duration.between(instant(expected), actual).abs()
        assertTrue(diff <= Duration.ofMinutes(minutes), "$what: expected $expected, got $actual (off by ${diff.seconds} s)")
    }

    @Test
    fun `sunrise and sunset within 2 minutes of astral for 2026 solstices, equinoxes and other dates`() {
        for (d in MunichReference.DAYS) {
            val date = LocalDate.parse(d.date)
            assertWithin(d.sunrise, SunEvents.sunrise(date, MUNICH, berlin), 2, "sunrise ${d.date}")
            assertWithin(d.sunset, SunEvents.sunset(date, MUNICH, berlin), 2, "sunset ${d.date}")
            assertWithin(d.noon, SunEvents.transit(date, MUNICH, berlin), 2, "transit ${d.date}")
        }
    }

    @Test
    fun `azimuth at sunrise and sunset within 0_2 degrees of astral`() {
        for (d in MunichReference.DAYS) {
            val date = LocalDate.parse(d.date)
            val rise = SunEvents.sunrise(date, MUNICH, berlin)!!
            val set = SunEvents.sunset(date, MUNICH, berlin)!!
            assertEquals(d.sunriseAz, SunPosition.at(rise, MUNICH).azimuthDeg, 0.2, "sunrise azimuth ${d.date}")
            assertEquals(d.sunsetAz, SunPosition.at(set, MUNICH).azimuthDeg, 0.2, "sunset azimuth ${d.date}")
        }
    }

    @Test
    fun `at sunrise and sunset the apparent altitude of the centre is minus one sun radius`() {
        for (d in MunichReference.DAYS) {
            val date = LocalDate.parse(d.date)
            for (t in listOf(SunEvents.sunrise(date, MUNICH, berlin)!!, SunEvents.sunset(date, MUNICH, berlin)!!)) {
                assertEquals(-SolarConstants.SUN_RADIUS_DEG, SunPosition.at(t, MUNICH).apparentAltitudeDeg, 0.01)
            }
        }
    }

    @Test
    fun `munich sunset azimuth sweeps about 234 to 308 degrees over the year`() {
        var min = 360.0
        var max = 0.0
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            val az = SunPosition.at(SunEvents.sunset(date, MUNICH, berlin)!!, MUNICH).azimuthDeg
            min = minOf(min, az)
            max = maxOf(max, az)
            date = date.plusDays(1)
        }
        assertEquals(234.5, min, 0.3)
        assertEquals(307.7, max, 0.3)
    }

    @Test
    fun `events belong to the local calendar date`() {
        // 2026-06-21 sunrise is 03:13 UTC = 05:13 CEST; asking for the Berlin date must give that instant,
        // and every event of a Berlin day must fall on that Berlin day.
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            for (t in listOfNotNull(SunEvents.sunrise(date, MUNICH, berlin), SunEvents.sunset(date, MUNICH, berlin))) {
                assertEquals(date, t.atZone(berlin).toLocalDate(), "event $t not on $date")
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `polar night gives no sunrise`() {
        val svalbard = GeoPoint(78.2, 15.6)
        assertNull(SunEvents.sunrise(LocalDate.of(2026, 12, 21), svalbard, ZoneId.of("Europe/Oslo")))
        assertNull(SunEvents.sunset(LocalDate.of(2026, 6, 21), svalbard, ZoneId.of("Europe/Oslo")))
        assertNotNull(SunEvents.transit(LocalDate.of(2026, 12, 21), svalbard, ZoneId.of("Europe/Oslo")))
    }

    @Test
    fun `sunrise is before transit is before sunset`() {
        val d = LocalDate.of(2026, 4, 30)
        val r = SunEvents.sunrise(d, MUNICH, berlin)!!
        val n = SunEvents.transit(d, MUNICH, berlin)
        val s = SunEvents.sunset(d, MUNICH, berlin)!!
        assertTrue(r < n && n < s)
        assertTrue(abs(Duration.between(r, n).seconds - Duration.between(n, s).seconds) < 5 * 60)
    }
}
