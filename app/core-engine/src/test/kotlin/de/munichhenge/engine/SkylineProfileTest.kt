package de.munichhenge.engine

import kotlin.math.atan
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkylineProfileTest {
    private val gate = 1.5

    @Test
    fun `encode and decode round trip in steps of 0_05 degrees`() {
        val hard = DoubleArray(SkylineProfile.SIZE) { i -> 0.05 * (i % 40) }
        val p = SkylineProfile(hard, null, Confidence.MEASURED)
        val back = SkylineProfile.decode(p.encodeHard(), null, Confidence.MEASURED)
        for (i in 0 until SkylineProfile.SIZE) assertEquals(hard[i], back.hardAt(SkylineProfile.offsetOf(i)), 1e-9)
        assertEquals(12.75, SkylineProfile.decode(SkylineProfile(DoubleArray(81) { 30.0 }, null, Confidence.MEASURED).encodeHard(),
            null, Confidence.MEASURED).floorDeg, 1e-9, "values clamp at one byte")
        assertFailsWith<IllegalArgumentException> { SkylineProfile.decode("AAAA", null, Confidence.MEASURED) }
    }

    @Test
    fun `values interpolate between samples and hold beyond the range`() {
        val hard = DoubleArray(SkylineProfile.SIZE) { i -> SkylineProfile.offsetOf(i) + 4.0 }   // 0 at -4, 8 at +4
        val p = SkylineProfile(hard, null, Confidence.MEASURED)
        assertEquals(4.0, p.hardAt(0.0), 1e-9)
        assertEquals(4.25, p.hardAt(0.25), 1e-9)
        assertEquals(0.0, p.hardAt(-9.0), 1e-9)
        assertEquals(8.0, p.hardAt(9.0), 1e-9)
        assertEquals(0.0, p.minDeg, 1e-9)
        assertEquals(8.0, p.maxDeg, 1e-9)
    }

    @Test
    fun `synthetic canyon is a V whose opening matches the closed form`() {
        val lengthM = 10.0 / tan(Math.toRadians(1.0))                     // far end spans ±1° at 20 m width
        val p = SkylineProfile.synthetic(floorDeg = 1.0, lengthM = lengthM, canyon = true, streetWidthM = 20.0)
        assertEquals(Confidence.ESTIMATED, p.confidence)
        assertEquals(1.0, p.hardAt(0.0), 1e-9)
        assertEquals(1.0, p.hardAt(0.9), 1e-9)
        assertEquals(2.0, p.hardAt(2.0), 1e-6)
        for (a in listOf(1.2, 1.5, 2.0, 3.0)) {
            val o = assertNotNull(p.opening(a, gate))
            assertEquals(-a, o.leftDeg, 0.01, "left edge at altitude $a")      // e0 * a / f with e0 = f = 1
            assertEquals(a, o.rightDeg, 0.01)
            assertEquals(0.0, o.midDeg, 0.01)
        }
        assertNull(p.opening(0.9, gate), "below the floor the axis is blocked")
    }

    @Test
    fun `open sides are capped at the gate and an asymmetric wall shifts the opening`() {
        val flat = SkylineProfile.flat(0.5)
        val o = assertNotNull(flat.opening(1.0, gate))
        assertTrue(o.leftOpen && o.rightOpen)
        assertEquals(-gate, o.leftDeg, 1e-9)
        assertEquals(gate, o.rightDeg, 1e-9)
        // a wall 3° high from 0.6° to the right, open to the left
        val hard = DoubleArray(SkylineProfile.SIZE) { i -> if (SkylineProfile.offsetOf(i) >= 0.65) 3.0 else 0.5 }
        val w = assertNotNull(SkylineProfile(hard, null, Confidence.MEASURED).opening(1.0, gate))
        assertTrue(w.leftOpen && !w.rightOpen)
        assertTrue(w.rightDeg in 0.6..0.7, "right edge ${w.rightDeg}")
        assertEquals((w.leftDeg + w.rightDeg) / 2, w.midDeg, 1e-9)
    }

    @Test
    fun `a wall rising out of the profile is not an open side`() {
        // V canyon: floor 1°, far end ±1°, so at 5° the walls stand beyond the ±4° edge
        val p = SkylineProfile.synthetic(1.0, 10.0 / tan(Math.toRadians(1.0)), canyon = true, streetWidthM = 20.0)
        val high = assertNotNull(p.opening(5.0, gate))
        assertTrue(!high.leftOpen && !high.rightOpen)
        assertEquals(-SkylineProfile.HALF_RANGE_DEG, high.leftDeg, 1e-9)
        assertEquals(SkylineProfile.HALF_RANGE_DEG, high.rightDeg, 1e-9)
        // continuous across the moment the sun passes the top of the profile's edge
        assertEquals(3.99, assertNotNull(p.opening(3.99, gate)).rightDeg, 0.01)
        assertEquals(4.0, assertNotNull(p.opening(4.01, gate)).rightDeg, 1e-9)
    }

    @Test
    fun `flat profile for open roads and shifting by an offset never goes below zero`() {
        val s = SkylineProfile.synthetic(floorDeg = 0.8, lengthM = 900.0, canyon = false, streetWidthM = 20.0)
        assertEquals(0.8, s.hardAt(-3.0), 1e-9)
        assertEquals(0.8, s.hardAt(3.0), 1e-9)
        assertEquals(1.3, s.shifted(0.5).floorDeg, 1e-9)
        assertEquals(0.0, s.shifted(-5.0).floorDeg, 1e-9)
        assertEquals(atan(10.0 / 900.0) * 180 / Math.PI, SkylineProfile.halfWidthDeg(900.0, 20.0), 1e-9)
    }
}
