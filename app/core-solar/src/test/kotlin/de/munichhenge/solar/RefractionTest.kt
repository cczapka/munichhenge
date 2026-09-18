package de.munichhenge.solar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefractionTest {
    @Test
    fun `refraction at the apparent horizon is about 34 arcminutes`() {
        // The classic 0.57°: a body seen exactly on the horizon is geometrically ~0.57° below it.
        val geometric = -0.57
        assertEquals(0.57, Refraction.forGeometricAltitude(geometric), 0.02)
        assertEquals(0.0, Refraction.apparent(geometric), 0.02)
    }

    @Test
    fun `refraction at geometric altitude zero is about 29 arcminutes`() {
        // Sæmundsson takes the geometric altitude: R(0) = 1.02 / tan(10.3/5.11 °) arcmin ≈ 0.483°
        assertEquals(0.483, Refraction.forGeometricAltitude(0.0), 0.005)
    }

    @Test
    fun `refraction is about one arcminute at 45 degrees and zero at the zenith`() {
        assertEquals(1.0 / 60.0, Refraction.forGeometricAltitude(45.0), 0.003)
        assertEquals(0.0, Refraction.forGeometricAltitude(90.0), 0.001)
    }

    @Test
    fun `refraction decreases monotonically with altitude and is never negative`() {
        var prev = Double.MAX_VALUE
        var h = -1.0
        while (h <= 90.0) {
            val r = Refraction.forGeometricAltitude(h)
            assertTrue(r >= 0.0, "R($h) = $r")
            assertTrue(r <= prev, "R not monotonic at $h")
            prev = r
            h += 0.5
        }
    }

    @Test
    fun `apparent altitude is monotonic in geometric altitude near the horizon`() {
        // needed by the bisection solver: apparent(h) must be strictly increasing
        var prev = Refraction.apparent(-2.0)
        var h = -1.99
        while (h <= 5.0) {
            val a = Refraction.apparent(h)
            assertTrue(a > prev, "apparent not increasing at $h")
            prev = a
            h += 0.01
        }
        assertTrue(abs(Refraction.apparent(30.0) - 30.0) < 0.03)
    }
}
