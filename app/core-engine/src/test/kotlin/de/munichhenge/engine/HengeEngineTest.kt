package de.munichhenge.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HengeEngineTest {
    private val year = 2026
    private val jan1 = LocalDate.of(year, 1, 1)

    private fun yearEvents(engine: HengeEngine, id: String, mode: Mode): List<HengeEvent> =
        (0 until 365).map { jan1.plusDays(it.toLong()) }
            .flatMap { engine.eventsFor(it, setOf(mode)) }
            .filter { it.sightlineId == id }

    /** Groups events into runs of nearby dates (a gap > 10 days starts a new cluster). */
    private fun clusters(events: List<HengeEvent>): List<List<HengeEvent>> {
        val out = mutableListOf<MutableList<HengeEvent>>()
        for (e in events.sortedBy { it.date }) {
            if (out.isEmpty() || ChronoUnit.DAYS.between(out.last().last().date, e.date) > 10) out.add(mutableListOf(e))
            else out.last().add(e)
        }
        return out
    }

    private fun bestDay(c: List<HengeEvent>) = c.maxBy { it.quality }.date

    @Test
    fun `9 - a 270 degree street has two sunset seasons with best days at the equinoxes`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("west", 270.0, obstruction = 0.0)))
        val events = yearEvents(engine, "west", Mode.SUNSET)
        assertTrue(events.all { it.viewFrom == Endpoint.A && it.bearing == 270.0 && it.mode == Mode.SUNSET })
        val c = clusters(events)
        assertEquals(2, c.size, "clusters: ${c.map { cl -> cl.first().date to cl.last().date }}")
        assertEquals(2, clusters(events.filter { it.grade == Grade.PERFECT }).size)
        assertTrue(abs(ChronoUnit.DAYS.between(LocalDate.of(year, 3, 20), bestDay(c[0]))) <= 2, "spring best ${bestDay(c[0])}")
        assertTrue(abs(ChronoUnit.DAYS.between(LocalDate.of(year, 9, 23), bestDay(c[1]))) <= 2, "autumn best ${bestDay(c[1])}")
        val rises = yearEvents(engine, "west", Mode.SUNRISE)
        assertTrue(rises.isNotEmpty() && rises.all { it.viewFrom == Endpoint.B && it.bearing == 90.0 })
        assertEquals(2, clusters(rises).size)
    }

    @Test
    fun `a 300 degree street has two good sunset seasons in May and July or August`() {
        // Around midsummer the sun stands within the gate 3-4° up (NEAR, between the buildings),
        // so all events form one long season; the good ones near the skyline are two.
        val engine = HengeEngine(Synthetic.data(Synthetic.street("nw", 300.0)))
        val all = yearEvents(engine, "nw", Mode.SUNSET)
        assertTrue(all.filter { it.date.monthValue == 6 }.all { it.grade == Grade.NEAR && it.style == Style.IN_CANYON })
        val c = clusters(all.filter { it.grade != Grade.NEAR })
        assertEquals(2, c.size, c.map { it.map { e -> e.date } }.toString())
        assertEquals(5, bestDay(c[0]).monthValue, "first season ${bestDay(c[0])}")
        assertTrue(bestDay(c[1]).monthValue in 7..8, "second season ${bestDay(c[1])}")
    }

    @Test
    fun `a 90 degree street mirrors the 270 degree one`() {
        val east = HengeEngine(Synthetic.data(Synthetic.street("east", 90.0, obstruction = 0.0)))
        val west = HengeEngine(Synthetic.data(Synthetic.street("west", 270.0, obstruction = 0.0)))
        val rises = clusters(yearEvents(east, "east", Mode.SUNRISE))
        assertEquals(2, rises.size)
        assertTrue(rises.flatten().all { it.viewFrom == Endpoint.A && it.bearing == 90.0 })
        val sets = clusters(yearEvents(east, "east", Mode.SUNSET))
        val westSets = clusters(yearEvents(west, "west", Mode.SUNSET))
        assertTrue(sets.flatten().all { it.viewFrom == Endpoint.B && it.bearing == 270.0 })
        for ((a, b) in sets.zip(westSets)) assertTrue(abs(ChronoUnit.DAYS.between(bestDay(a), bestDay(b))) <= 1)
    }

    @Test
    fun `9 - a street outside both azimuth ranges never has an event`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("ssw", 200.0)))
        assertTrue(yearEvents(engine, "ssw", Mode.SUNSET).isEmpty())
        assertTrue(yearEvents(engine, "ssw", Mode.SUNRISE).isEmpty())
    }

    @Test
    fun `higher obstruction moves the best moment earlier and the sun further south at sunset`() {
        val date = LocalDate.of(year, 4, 30)
        val (_, az) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val low = HengeEngine(Synthetic.data(Synthetic.street("l", az - 0.5, obstruction = 0.5))).eventsFor(date, setOf(Mode.SUNSET)).single()
        val high = HengeEngine(Synthetic.data(Synthetic.street("h", az - 0.5, obstruction = 2.0))).eventsFor(date, setOf(Mode.SUNSET)).single()
        assertTrue(high.tBest < low.tBest)
        assertTrue(high.sunAzimuthDeg < low.sunAzimuthDeg)
    }

    @Test
    fun `obstruction offset shifts every best moment and never pushes the floor below zero`() {
        val date = LocalDate.of(year, 3, 19)
        val (_, az) = Synthetic.sunAt(date, 1.0, Mode.SUNSET)
        val sl = Synthetic.street("w", az - 0.55, obstruction = 1.0)          // in the gate at floors 0, 1 and 2
        val base = HengeEngine(Synthetic.data(sl)).eventsFor(date, setOf(Mode.SUNSET)).single()
        val raised = HengeEngine(Synthetic.data(sl), EngineConfig(obstructionOffsetDeg = 1.0)).eventsFor(date, setOf(Mode.SUNSET)).single()
        assertTrue(raised.tBest < base.tBest)
        assertEquals(2.0, raised.sunAltitudeDeg - raised.heightAboveFloorDeg, 1e-6, "floor raised to 2°")
        val floor = HengeEngine(Synthetic.data(sl), EngineConfig(obstructionOffsetDeg = -5.0)).eventsFor(date, setOf(Mode.SUNSET)).single()
        assertEquals(0.0, floor.sunAltitudeDeg - floor.heightAboveFloorDeg, 1e-6, "floor clamped at the horizon")
    }

    @Test
    fun `events carry the pois looking down the sightline in that direction`() {
        val sl = Synthetic.street("w", 270.0)
        val pois = listOf(
            Synthetic.poi("cafe_at_a", View("w", Endpoint.B, 10.0)),
            Synthetic.poi("bar_at_b", View("w", Endpoint.A, 5.0)),
            Synthetic.poi("both", View("w", Endpoint.A, 5.0), View("w", Endpoint.B, 5.0)),
        )
        val e = HengeEngine(Synthetic.data(sl, pois = pois)).eventsFor(LocalDate.of(year, 3, 20), setOf(Mode.SUNSET)).single()
        assertEquals(listOf("both", "cafe_at_a"), e.poiIds)
    }

    @Test
    fun `nextEventsFor agrees with eventsFor and stops at count`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0), Synthetic.street("nw", 300.0)))
        val next = engine.nextEventsFor("nw", jan1, 5, setOf(Mode.SUNSET))
        assertEquals(5, next.size)
        assertTrue(next.zipWithNext().all { (a, b) -> a.tBest < b.tBest })
        for (e in next) assertEquals(listOf(e), engine.eventsFor(e.date, setOf(e.mode)).filter { it.sightlineId == "nw" })
        assertEquals(5, next.first().date.monthValue)
        val first = engine.nextEventsFor("nw", jan1, 1).single()
        assertEquals(Mode.SUNRISE, first.mode)
        assertEquals(Endpoint.B, first.viewFrom)
        assertEquals(1, first.date.monthValue)
    }

    @Test
    fun `nextEventsForPoi uses the poi's views`() {
        val pois = listOf(Synthetic.poi("cafe", View("w", Endpoint.B, 10.0)))
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0), Synthetic.street("nw", 300.0), pois = pois))
        val next = engine.nextEventsForPoi("cafe", jan1, 3)
        assertEquals(3, next.size)
        assertTrue(next.all { it.sightlineId == "w" && it.mode == Mode.SUNSET && "cafe" in it.poiIds })
        assertTrue(engine.nextEventsForPoi("nope", jan1, 3).isEmpty())
    }

    @Test
    fun `bestUpcoming ranks by score and filters by quality`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0), Synthetic.street("nw", 300.0)))
        val from = LocalDate.of(year, 3, 10)
        val best = engine.bestUpcoming(from, days = 14, minQuality = 0.5)
        assertTrue(best.isNotEmpty())
        assertTrue(best.all { it.quality >= 0.5 && it.sightlineId == "w" && !it.openHorizon })
        assertTrue(best.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(best.all { !it.date.isBefore(from) && it.date.isBefore(from.plusDays(14)) })
        assertTrue(engine.bestUpcoming(jan1, 14, 0.0).isEmpty())
    }

    @Test
    fun `open horizon spots get one event per day and mode but never rank as best`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.openHorizon("sl_open_hill"), Synthetic.street("w", 270.0)))
        val date = LocalDate.of(year, 6, 21)
        val open = engine.eventsFor(date).filter { it.sightlineId == "sl_open_hill" }
        assertEquals(setOf(Mode.SUNRISE, Mode.SUNSET), open.map { it.mode }.toSet())
        assertTrue(open.all { it.openHorizon && it.grade == Grade.PERFECT && it.style == Style.ON_HORIZON })
        val sunset = open.single { it.mode == Mode.SUNSET }
        assertEquals(sunset.sunAzimuthDeg, sunset.bearing, 1e-9)
        assertTrue(sunset.windowStart < sunset.tBest && sunset.tBest < sunset.windowEnd)
        assertTrue(engine.eventsFor(date).none { !it.openHorizon }, "June: the 270° street is out of season")
        assertTrue(engine.bestUpcoming(date, 7, 0.0).none { it.openHorizon })
        assertEquals(3, engine.nextEventsFor("sl_open_hill", date, 3).size)
    }

    @Test
    fun `dates are Europe Berlin calendar dates and results are cached`() {
        val engine = HengeEngine(Synthetic.data(Synthetic.street("w", 270.0)))
        val date = LocalDate.of(year, 3, 20)
        val a = engine.eventsFor(date)
        assertTrue(a === engine.eventsFor(date), "second call must hit the cache")
        assertNotNull(a.firstOrNull())
        for (e in a) assertEquals(date, e.tBest.atZone(engine.config.zone).toLocalDate())
    }
}
