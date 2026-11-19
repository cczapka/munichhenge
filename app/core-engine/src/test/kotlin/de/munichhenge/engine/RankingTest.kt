package de.munichhenge.engine

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankingTest {
    private val cfg = EngineConfig()

    @Test
    fun `10 - a featured long framed named street with spots beats an unnamed short path`() {
        val great = Interest.of(lengthM = 1500.0, featured = true, named = true, framed = 1.0, spots = 5, config = cfg)
        val plain = Interest.of(lengthM = 420.0, featured = false, named = false, framed = 0.0, spots = 0, config = cfg)
        assertEquals(1.0, great, 1e-9)
        assertTrue(plain < 0.05, "plain $plain")
        assertEquals(cfg.interestFloor, HengeEngine.score(1.0, 0.0, cfg), 1e-9, "a superb alignment on a plain street still shows")
        assertTrue(HengeEngine.score(0.8, great, cfg) > HengeEngine.score(0.8, plain, cfg))
        assertTrue(HengeEngine.score(0.9, 0.5, cfg) > HengeEngine.score(0.8, 0.5, cfg), "equal interest sorts by quality")
    }

    @Test
    fun `10 - weights are respected and inputs are normalised`() {
        val onlyLength = cfg.copy(wFeatured = 0.0, wLength = 1.0, wFramed = 0.0, wSpots = 0.0, wNamed = 0.0)
        assertEquals(0.0, Interest.of(400.0, true, true, 1.0, 9, onlyLength), 1e-9)
        assertEquals(0.5, Interest.of(950.0, false, false, 0.0, 0, onlyLength), 1e-9)
        assertEquals(1.0, Interest.of(9000.0, false, false, 0.0, 0, onlyLength), 1e-9)
        val onlySpots = cfg.copy(wFeatured = 0.0, wLength = 0.0, wFramed = 0.0, wSpots = 1.0, wNamed = 0.0)
        assertEquals(0.4, Interest.of(500.0, false, false, 0.0, 2, onlySpots), 1e-9)
    }

    @Test
    fun `framing comes from the profile`() {
        assertEquals(0.0, Interest.framed(SkylineProfile.flat(1.0), cfg), 1e-9)
        val canyon = SkylineProfile.synthetic(1.0, 10.0 / Math.tan(Math.toRadians(0.5)), canyon = true, streetWidthM = 20.0)
        assertEquals(1.0, Interest.framed(canyon, cfg), 1e-9, "walls at ±1.5° tower over a narrow far end")
    }

    @Test
    fun `eventsFor sorts by score, so the better place comes first at equal alignment`() {
        val date = LocalDate.of(2026, 3, 20)
        val (_, az) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val plain = Synthetic.street("plain", az, name = null, lengthM = 420.0)
        val great = Synthetic.street("great", az, featured = true, lengthM = 1500.0).copy(b = plain.b)
        val events = HengeEngine(Synthetic.data(plain, great)).eventsFor(date, setOf(Mode.SUNSET))
        assertEquals(listOf("great", "plain"), events.map { it.sightlineId })
        assertTrue(events[0].interest > events[1].interest)
    }
}
