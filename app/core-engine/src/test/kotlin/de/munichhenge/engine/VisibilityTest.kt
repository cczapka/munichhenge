package de.munichhenge.engine

import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The event definition of IMPROVEMENT-PLAN.md 3.2 on synthetic canyons. Floor 1°, far end ±1° wide,
 * so the opening at altitude a is ±a degrees. Near the equinox in Munich the sun moves ~1.12° in
 * azimuth per degree of altitude, faster than the opening grows: that decides tests 3 and 4.
 */
class VisibilityTest {
    private val date = LocalDate.of(2026, 3, 20)
    private val r = 0.2665

    private fun only(engine: HengeEngine, id: String, mode: Mode): HengeEvent? =
        engine.eventsFor(date, setOf(mode)).singleOrNull { it.sightlineId == id && it.viewFrom == Endpoint.A }

    private fun engineFor(vararg sl: Sightline, config: EngineConfig = EngineConfig()) = HengeEngine(Synthetic.data(*sl), config)

    @Test
    fun `2 - canyon aligned at the floor gives a perfect event on the horizon`() {
        val (tFloor, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val e = assertNotNull(only(engineFor(Synthetic.canyon("c", azFloor)), "c", Mode.SUNSET))
        assertEquals(Style.ON_HORIZON, e.style)
        assertEquals(Grade.PERFECT, e.grade)
        assertEquals(Confidence.ESTIMATED, e.confidence)
        assertTrue(e.windowStart < e.tBest && e.tBest < e.windowEnd)
        assertTrue(Duration.between(e.windowEnd, tFloor).abs() <= Duration.ofSeconds(60), "window ends ${e.windowEnd}, floor at $tFloor")
        assertTrue(e.offsetDeg in -0.6..0.0, "sun slightly left of the axis just before it reaches the floor: ${e.offsetDeg}")
        assertTrue(e.heightAboveFloorDeg in (r - 0.05)..(2 * r), "disc resting on the floor: ${e.heightAboveFloorDeg}")
        assertTrue(e.discFraction >= 0.9)
    }

    @Test
    fun `3 - canyon the sun has already crossed gives an earlier event between the buildings`() {
        val (_, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val (tFullOld, _) = Synthetic.sunAt(date, 1.0 + r, Mode.SUNSET)
        val e = assertNotNull(only(engineFor(Synthetic.canyon("c", azFloor - 1.5)), "c", Mode.SUNSET))
        assertEquals(Style.IN_CANYON, e.style)
        assertTrue(abs(e.offsetDeg) < 0.25, "centred at its best moment: ${e.offsetDeg}")
        assertTrue(Duration.between(e.tBest, tFullOld) > Duration.ofMinutes(3), "earlier than the old full-disc time")
        assertEquals(Grade.GOOD, e.grade)
    }

    @Test
    fun `4 - canyon the sun never reaches gives no event`() {
        val (_, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        assertNull(only(engineFor(Synthetic.canyon("c", azFloor + 1.5)), "c", Mode.SUNSET))
    }

    @Test
    fun `5 - an open road accepts the sun only within the gate`() {
        val (_, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val aligned = assertNotNull(only(engineFor(Synthetic.street("o", azFloor)), "o", Mode.SUNSET))
        assertEquals(Style.ON_HORIZON, aligned.style)
        assertTrue(abs(aligned.offsetDeg) <= EngineConfig().openSideGateDeg)
        assertNull(only(engineFor(Synthetic.street("o", azFloor + 3.0)), "o", Mode.SUNSET))
    }

    @Test
    fun `6 - sunrise mirrors the canyon cases with the sides swapped`() {
        val (tFloor, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNRISE)
        val (tFullOld, _) = Synthetic.sunAt(date, 1.0 + r, Mode.SUNRISE)
        val aligned = assertNotNull(only(engineFor(Synthetic.canyon("c", azFloor)), "c", Mode.SUNRISE))
        assertEquals(Style.ON_HORIZON, aligned.style)
        assertEquals(Grade.PERFECT, aligned.grade)
        assertTrue(Duration.between(aligned.windowStart, tFloor).abs() <= Duration.ofSeconds(60))
        // sun left of the axis at the floor moves right as it rises: centred later, higher up
        val later = assertNotNull(only(engineFor(Synthetic.canyon("c", azFloor + 1.5)), "c", Mode.SUNRISE))
        assertEquals(Style.IN_CANYON, later.style)
        assertTrue(Duration.between(tFullOld, later.tBest) > Duration.ofMinutes(3))
        // right of the axis at the floor only moves further right
        assertNull(only(engineFor(Synthetic.canyon("c", azFloor - 1.5)), "c", Mode.SUNRISE))
    }

    @Test
    fun `7 - which way to step to put the sun over the middle of the far end`() {
        val l = 10.0 / tan(Math.toRadians(1.0))
        assertEquals(l * tan(Math.toRadians(0.25)), HengeEngine.stepRightM(l, -0.25, 20.0)!!, 1e-9, "sun left -> step right")
        assertEquals(-l * tan(Math.toRadians(0.25)), HengeEngine.stepRightM(l, 0.25, 20.0)!!, 1e-9)
        assertNull(HengeEngine.stepRightM(l, 2.0, 20.0), "more than half the street width away")
        val (_, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val e = assertNotNull(only(engineFor(Synthetic.canyon("c", azFloor)), "c", Mode.SUNSET))
        assertEquals(HengeEngine.stepRightM(l, e.offsetDeg, 20.0)!!, e.stepRightM!!, 1e-9)
        assertTrue(e.stepRightM!! > 0)
    }

    @Test
    fun `8 - trees above the sun are flagged and halve the quality only in leaf`() {
        val (_, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val plain = Synthetic.canyon("c", azFloor)
        val hard = SkylineProfile.synthetic(1.0, plain.lengthM, canyon = true, streetWidthM = 20.0)
        val treed = plain.copy(profileAb = hard.withSoft(DoubleArray(SkylineProfile.SIZE) { 6.0 }))
        val base = assertNotNull(only(engineFor(plain), "c", Mode.SUNSET))
        val bare = assertNotNull(only(engineFor(treed, config = EngineConfig(leafOnMonths = setOf(5))), "c", Mode.SUNSET))
        val leaf = assertNotNull(only(engineFor(treed, config = EngineConfig(leafOnMonths = setOf(3))), "c", Mode.SUNSET))
        assertTrue(!base.treesInWay && bare.treesInWay && leaf.treesInWay)
        assertEquals(base.quality, bare.quality, 1e-9)
        assertEquals(base.quality * EngineConfig().treesQualityFactor, leaf.quality, 1e-9)
    }

    @Test
    fun `measured profiles carry their confidence and the offset shifts them`() {
        val (_, azFloor) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val plain = Synthetic.canyon("c", azFloor)
        val measured = SkylineProfile(DoubleArray(SkylineProfile.SIZE) { i ->
            SkylineProfile.synthetic(1.0, plain.lengthM, true, 20.0).hardAt(SkylineProfile.offsetOf(i))
        }, null, Confidence.MEASURED)
        val e = assertNotNull(only(engineFor(plain.copy(profileAb = measured)), "c", Mode.SUNSET))
        assertEquals(Confidence.MEASURED, e.confidence)
        val raised = assertNotNull(only(engineFor(plain.copy(profileAb = measured), config = EngineConfig(obstructionOffsetDeg = 0.5)),
            "c", Mode.SUNSET))
        assertTrue(raised.tBest < e.tBest)
    }
}
