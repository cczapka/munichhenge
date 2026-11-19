package de.munichhenge.engine

import de.munichhenge.solar.SunPosition
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class SunPathTest {
    @Test
    fun `interpolated low sun stays within 0_005 degrees of the exact position`() {
        for (date in listOf(LocalDate.of(2026, 12, 21), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 21))) {
            for (mode in Mode.entries) {
                val (t0, _) = Synthetic.sunAt(date, -0.3, mode)
                val (t1, _) = Synthetic.sunAt(date, 12.0, mode)
                val (start, end) = if (t0 < t1) t0 to t1 else t1 to t0
                val path = SunPath(Synthetic.CENTER, start, end, 180)
                var s = 0.0
                while (s <= path.spanSeconds) {
                    val exact = SunPosition.at(path.instantAt(s), Synthetic.CENTER)
                    assertTrue(kotlin.math.abs(exact.apparentAltitudeDeg - path.altitudeAt(s)) < 0.005, "$date $mode alt at $s s")
                    assertTrue(Geo.angleDiffDeg(exact.azimuthDeg, path.azimuthAt(s)) < 0.005, "$date $mode az at $s s")
                    s += 37.0
                }
                assertTrue(Duration.between(start, end) > Duration.ofMinutes(30))
            }
        }
    }
}
