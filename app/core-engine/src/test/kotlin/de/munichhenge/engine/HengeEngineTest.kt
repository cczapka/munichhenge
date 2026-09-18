package de.munichhenge.engine

import de.munichhenge.solar.SunPosition
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HengeEngineTest {
    private val year = 2026
    private val jan1 = LocalDate.of(year, 1, 1)

    private fun yearEvents(engine: HengeEngine, id: String, mode: Mode): List<HengeEvent> =
        (0 until 365).map { jan1.plusDays(it.toLong()) }
            .flatMap { engine.eventsFor(it, setOf(mode)) }
            .filter { it.sightlineId == id }

    /** Groups events into runs of consecutive-ish dates (gap > 10 days starts a new cluster). */
    private fun clusters(events: List<HengeEvent>): List<List<HengeEvent>> {
        val out = mutableListOf<MutableList<HengeEvent>>()
        for (e in events.sortedBy { it.date }) {
            if (out.isEmpty() || ChronoUnit.DAYS.between(out.last().last().date, e.date) > 10) out.add(mutableListOf(e))
            else out.last().add(e)
        }
        return out
    }

    @Test
    fun `a 270 degree street has exactly two sunset seasons, around the equinoxes`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("west", 270.0, obstruction = 0.0)))
        val events = yearEvents(engine, "west", Mode.SUNSET)
        assertTrue(events.all { it.viewFrom == Endpoint.A && it.bearing == 270.0 && it.mode == Mode.SUNSET })
        val perfect = clusters(events.filter { it.grade == Grade.PERFECT })
        assertEquals(2, perfect.size, "perfect clusters: ${perfect.map { c -> c.map { it.date } }}")
        // astral: sunset azimuth 270.9° on 2026-03-20 and 270.5° on 2026-09-23; the henge moment
        // (centre at +0.27°) is ~0.6° further south than sunset, so the best day is ~Mar 19/20 and Sep 23.
        val best = perfect.map { c -> c.minBy { it.alignmentErrorDeg }.date }
        assertTrue(abs(ChronoUnit.DAYS.between(LocalDate.of(year, 3, 20), best[0])) <= 2, "spring best ${best[0]}")
        assertTrue(abs(ChronoUnit.DAYS.between(LocalDate.of(year, 9, 23), best[1])) <= 2, "autumn best ${best[1]}")
        // and every event within 3°, none outside the seasons
        assertTrue(events.all { it.alignmentErrorDeg <= 3.0 })
        assertEquals(2, clusters(events).size)
        // looking the other way (stand at B, bearing 90°) the same street sees equinox sunrises
        val rises = yearEvents(engine, "west", Mode.SUNRISE)
        assertTrue(rises.isNotEmpty() && rises.all { it.viewFrom == Endpoint.B && it.bearing == 90.0 })
        assertEquals(2, clusters(rises).size)
    }

    @Test
    fun `a 300 degree street has two sunset seasons in May and late July or August`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("nw", 300.0)))
        val c = clusters(yearEvents(engine, "nw", Mode.SUNSET))
        assertEquals(2, c.size, c.map { it.map { e -> e.date } }.toString())
        val best = c.map { cl -> cl.minBy { it.alignmentErrorDeg }.date }
        assertEquals(5, best[0].monthValue, "first season ${best[0]}")
        assertTrue(best[1].monthValue in 7..8, "second season ${best[1]}")
    }

    @Test
    fun `a 90 degree street has sunrise seasons at the equinoxes and the reverse sunset seasons`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("east", 90.0, obstruction = 0.0)))
        val rises = yearEvents(engine, "east", Mode.SUNRISE)
        assertTrue(rises.isNotEmpty() && rises.all { it.viewFrom == Endpoint.A && it.bearing == 90.0 })
        val riseClusters = clusters(rises.filter { it.grade == Grade.PERFECT })
        assertEquals(2, riseClusters.size)
        assertTrue(abs(ChronoUnit.DAYS.between(LocalDate.of(year, 3, 20), riseClusters[0].minBy { it.alignmentErrorDeg }.date)) <= 2)
        assertTrue(abs(ChronoUnit.DAYS.between(LocalDate.of(year, 9, 23), riseClusters[1].minBy { it.alignmentErrorDeg }.date)) <= 2)
        // looking the other way (stand at B, bearing 270) it is the same as the 270° street
        val sets = yearEvents(engine, "east", Mode.SUNSET)
        assertTrue(sets.isNotEmpty() && sets.all { it.viewFrom == Endpoint.B && it.bearing == 270.0 })
        val west = HengeEngine(Synthetic.data(Synthetic.street("west", 270.0, obstruction = 0.0)))
        val westSets = yearEvents(west, "west", Mode.SUNSET)
        assertEquals(westSets.map { it.date to it.grade }, sets.map { it.date to it.grade })
        for ((w, e) in westSets.zip(sets)) assertEquals(w.alignmentErrorDeg, e.alignmentErrorDeg, 0.02)
    }

    @Test
    fun `a street outside both azimuth ranges never matches`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("ssw", 200.0)))   // 200° / 20°
        assertTrue(yearEvents(engine, "ssw", Mode.SUNSET).isEmpty())
        assertTrue(yearEvents(engine, "ssw", Mode.SUNRISE).isEmpty())
    }

    @Test
    fun `grades follow the tolerance bands`() {
        val date = LocalDate.of(year, 3, 20)
        val probe = HengeEngine(Synthetic.data(Synthetic.street("p", 270.0)))
        val exact = probe.eventsFor(date, setOf(Mode.SUNSET)).single().azimuthAtFull
        val data = Synthetic.data(
            Synthetic.street("perfect", exact + 0.3), Synthetic.street("good", exact - 1.0),
            Synthetic.street("near", exact + 2.5), Synthetic.street("none", exact - 4.0),
        )
        val byId = HengeEngine(data).eventsFor(date, setOf(Mode.SUNSET)).associateBy { it.sightlineId }
        assertEquals(Grade.PERFECT, byId["perfect"]!!.grade)
        assertEquals(Grade.GOOD, byId["good"]!!.grade)
        assertEquals(Grade.NEAR, byId["near"]!!.grade)
        assertNull(byId["none"])
        assertEquals(0.3, byId["perfect"]!!.alignmentErrorDeg, 0.02)
        assertEquals(1.0 - 2.5 / 3.0, byId["near"]!!.quality, 0.01)
        assertTrue(byId["perfect"]!!.quality > byId["good"]!!.quality && byId["good"]!!.quality > byId["near"]!!.quality)
    }

    @Test
    fun `henge moments sit at their target altitudes and are ordered by mode`() {
        val sl = Synthetic.street("w", 270.0, obstruction = 2.0)
        val e = HengeEngine(Synthetic.data(sl)).eventsFor(LocalDate.of(year, 3, 19), setOf(Mode.SUNSET)).single()
        assertTrue(e.tFull < e.tHalf, "at sunset the full disk rests on the line before half of it is gone")
        assertEquals(2.0 + 0.2665, SunPosition.at(e.tFull, sl.a).apparentAltitudeDeg, 0.01)
        assertEquals(2.0, SunPosition.at(e.tHalf, sl.a).apparentAltitudeDeg, 0.01)
        assertEquals(SunPosition.at(e.tFull, sl.a).azimuthDeg, e.azimuthAtFull, 1e-9)
        assertEquals(LocalDate.of(year, 3, 19), e.date)

        val east = Synthetic.street("e", 90.0, obstruction = 2.0)
        val r = HengeEngine(Synthetic.data(east)).eventsFor(LocalDate.of(year, 3, 21), setOf(Mode.SUNRISE)).single()
        assertTrue(r.tHalf < r.tFull, "at sunrise half the disk shows before the whole disk clears the line")
        assertEquals(2.2665, SunPosition.at(r.tFull, east.a).apparentAltitudeDeg, 0.01)
    }

    @Test
    fun `higher obstruction shifts the henge moment earlier and the azimuth south at sunset`() {
        val date = LocalDate.of(year, 4, 30)
        // sunset azimuth 293.7° on 2026-04-30 (astral); at higher altitude the sun is further south
        val low = HengeEngine(Synthetic.data(Synthetic.street("l", 292.0, obstruction = 0.5))).eventsFor(date, setOf(Mode.SUNSET)).single()
        val high = HengeEngine(Synthetic.data(Synthetic.street("h", 292.0, obstruction = 2.0))).eventsFor(date, setOf(Mode.SUNSET)).single()
        assertTrue(high.tFull < low.tFull)
        assertTrue(high.azimuthAtFull < low.azimuthAtFull)
    }

    @Test
    fun `obstruction offset shifts every henge moment`() {
        val sl = Synthetic.street("w", 270.0, obstruction = 1.0)
        val date = LocalDate.of(year, 3, 19)
        val base = HengeEngine(Synthetic.data(sl)).eventsFor(date, setOf(Mode.SUNSET)).single()
        val raised = HengeEngine(Synthetic.data(sl), EngineConfig(obstructionOffsetDeg = 1.0)).eventsFor(date, setOf(Mode.SUNSET)).single()
        assertEquals(2.2665, SunPosition.at(raised.tFull, sl.a).apparentAltitudeDeg, 0.01)
        assertTrue(raised.tFull < base.tFull)
        // negative offsets never push the obstruction below the horizon
        val floor = HengeEngine(Synthetic.data(sl), EngineConfig(obstructionOffsetDeg = -5.0)).eventsFor(date, setOf(Mode.SUNSET)).single()
        assertEquals(0.2665, SunPosition.at(floor.tFull, sl.a).apparentAltitudeDeg, 0.01)
    }

    @Test
    fun `events carry the pois looking down the sightline in that direction`() {
        val sl = Synthetic.street("w", 270.0)
        val pois = listOf(
            Synthetic.poi("cafe_at_a", View("w", Endpoint.B, 10.0)),     // stands near A, looks toward B: sunset event
            Synthetic.poi("bar_at_b", View("w", Endpoint.A, 5.0)),       // looks toward A (east): only sunrise
            Synthetic.poi("both", View("w", Endpoint.A, 5.0), View("w", Endpoint.B, 5.0)),
        )
        val e = HengeEngine(Synthetic.data(sl, pois = pois)).eventsFor(LocalDate.of(year, 3, 20), setOf(Mode.SUNSET)).single()
        assertEquals(listOf("both", "cafe_at_a"), e.poiIds)
    }

    @Test
    fun `nextEventsFor agrees with eventsFor and stops at count`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0), Synthetic.street("nw", 300.0)))
        val next = engine.nextEventsFor("nw", LocalDate.of(year, 1, 1), 5, setOf(Mode.SUNSET))
        assertEquals(5, next.size)
        assertTrue(next.zipWithNext().all { (a, b) -> a.tFull < b.tFull })
        for (e in next) {
            val same = engine.eventsFor(e.date, setOf(e.mode)).filter { it.sightlineId == "nw" }
            assertEquals(listOf(e), same)
        }
        assertEquals(5, next.first().date.monthValue)
        val all = engine.nextEventsFor("nw", LocalDate.of(year, 1, 1), 1000, setOf(Mode.SUNSET))
        assertEquals(2, clusters(all).size)
        // both modes: the reverse direction (120° from B) sees mid-January sunrises first
        val first = engine.nextEventsFor("nw", LocalDate.of(year, 1, 1), 1).single()
        assertEquals(Mode.SUNRISE, first.mode)
        assertEquals(Endpoint.B, first.viewFrom)
        assertEquals(1, first.date.monthValue)
    }

    @Test
    fun `nextEventsForPoi uses the poi's views`() {
        val pois = listOf(Synthetic.poi("cafe", View("w", Endpoint.B, 10.0)))
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0), Synthetic.street("nw", 300.0), pois = pois))
        val next = engine.nextEventsForPoi("cafe", LocalDate.of(year, 1, 1), 3)
        assertEquals(3, next.size)
        assertTrue(next.all { it.sightlineId == "w" && it.mode == Mode.SUNSET && "cafe" in it.poiIds })
        assertTrue(engine.nextEventsForPoi("nope", LocalDate.of(year, 1, 1), 3).isEmpty())
    }

    @Test
    fun `bestUpcoming ranks by quality across days and honours minQuality`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0), Synthetic.street("nw", 300.0)))
        val best = engine.bestUpcoming(LocalDate.of(year, 3, 10), days = 14, minQuality = 0.7)
        assertTrue(best.isNotEmpty())
        assertTrue(best.all { it.quality >= 0.7 && it.sightlineId == "w" })
        assertTrue(best.zipWithNext().all { (a, b) -> a.quality >= b.quality })
        assertTrue(best.all { !it.date.isBefore(LocalDate.of(year, 3, 10)) && it.date.isBefore(LocalDate.of(year, 3, 24)) })
        assertTrue(engine.bestUpcoming(LocalDate.of(year, 1, 1), 14, 0.0).isEmpty())
    }

    @Test
    fun `open horizon spots get one event per day and mode but never rank as best`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.openHorizon("sl_open_hill"), Synthetic.street("w", 270.0)))
        val date = LocalDate.of(year, 6, 21)
        val today = engine.eventsFor(date)
        val open = today.filter { it.sightlineId == "sl_open_hill" }
        assertEquals(2, open.size)
        assertTrue(open.all { it.openHorizon && it.alignmentErrorDeg == 0.0 && it.grade == Grade.PERFECT })
        val sunset = open.single { it.mode == Mode.SUNSET }
        assertEquals(sunset.azimuthAtFull, sunset.bearing, 1e-9)
        assertTrue(today.filter { !it.openHorizon }.isEmpty())   // June: the 270° street is out of season
        assertTrue(engine.bestUpcoming(date, 7, 0.0).none { it.openHorizon })
        assertTrue(engine.nextEventsFor("sl_open_hill", date, 3).size == 3)
    }

    @Test
    fun `date is the Europe Berlin calendar date and results are cached`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0)))
        val date = LocalDate.of(year, 3, 20)
        val a = engine.eventsFor(date)
        val b = engine.eventsFor(date)
        assertTrue(a === b, "second call must hit the cache")
        for (e in a) assertEquals(date, e.tFull.atZone(engine.config.zone).toLocalDate())
        assertNotNull(a.firstOrNull())
    }
}
