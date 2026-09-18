package de.munichhenge.solar

import de.munichhenge.solar.MunichReference.MUNICH
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AltitudeSolverTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun `solving for minus one sun radius around sunset reproduces sunset within a second`() {
        for (d in MunichReference.DAYS) {
            val date = LocalDate.parse(d.date)
            val sunset = SunEvents.sunset(date, MUNICH, berlin)!!
            val r = AltitudeSolver.solve(MUNICH, -SolarConstants.SUN_RADIUS_DEG,
                sunset.minus(Duration.ofHours(3)), sunset.plus(Duration.ofHours(1)))
            assertNotNull(r)
            assertTrue(Duration.between(sunset, r.time).abs().seconds <= 1, "on ${d.date}")
            assertTrue(r.iterations < 40, "iterations ${r.iterations}")
        }
    }

    @Test
    fun `henge-style targets are hit to 0_01 degrees and lie before sunset`() {
        val date = LocalDate.of(2026, 6, 21)
        val sunset = SunEvents.sunset(date, MUNICH, berlin)!!
        val from = sunset.minus(Duration.ofHours(3))
        val to = sunset.plus(Duration.ofHours(1))
        var previous: java.time.Instant? = null
        for (target in listOf(3.0, 2.6, 1.0, 0.5, 0.2665, 0.0)) {
            val r = AltitudeSolver.solve(MUNICH, target, from, to)
            assertNotNull(r, "target $target")
            assertEquals(target, SunPosition.at(r.time, MUNICH).apparentAltitudeDeg, 0.01)
            assertTrue(r.time < sunset)
            if (previous != null) assertTrue(r.time > previous, "higher target must be earlier")
            previous = r.time
        }
    }

    @Test
    fun `works around sunrise too`() {
        val date = LocalDate.of(2026, 12, 21)
        val sunrise = SunEvents.sunrise(date, MUNICH, berlin)!!
        val r = AltitudeSolver.solve(MUNICH, 2.0, sunrise.minus(Duration.ofHours(1)), sunrise.plus(Duration.ofHours(3)))
        assertNotNull(r)
        assertTrue(r.time > sunrise)
        assertEquals(2.0, SunPosition.at(r.time, MUNICH).apparentAltitudeDeg, 0.01)
    }

    @Test
    fun `no sign change in the bracket gives null`() {
        val date = LocalDate.of(2026, 6, 21)
        val sunset = SunEvents.sunset(date, MUNICH, berlin)!!
        // 70° is never reached in Munich; -10° not within an hour of sunset
        assertNull(AltitudeSolver.solve(MUNICH, 70.0, sunset.minus(Duration.ofHours(3)), sunset.plus(Duration.ofHours(1))))
        assertNull(AltitudeSolver.solve(MUNICH, -10.0, sunset.minus(Duration.ofMinutes(30)), sunset.plus(Duration.ofMinutes(30))))
    }

    @Test
    fun `tolerance is honoured`() {
        val sunset = SunEvents.sunset(LocalDate.of(2026, 9, 23), MUNICH, berlin)!!
        val coarse = AltitudeSolver.solve(MUNICH, 1.0, sunset.minus(Duration.ofHours(3)), sunset, toleranceSeconds = 60.0)!!
        val fine = AltitudeSolver.solve(MUNICH, 1.0, sunset.minus(Duration.ofHours(3)), sunset, toleranceSeconds = 0.5)!!
        assertTrue(coarse.iterations < fine.iterations)
        assertTrue(Duration.between(coarse.time, fine.time).abs().seconds <= 60)
    }
}
