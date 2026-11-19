package de.munichhenge.app.model

import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayModelTest {
    private val sl = Fx.sightline()
    private val of = { _: String -> sl }

    private val rise = Fx.event(id = "rise", mode = Mode.SUNRISE, t = "2026-11-19T06:40:00Z", score = 0.7)
    private val set1 = Fx.event(id = "set1", t = "2026-11-19T15:20:00Z", score = 0.9)
    private val set2 = Fx.event(id = "set2", t = "2026-11-19T15:25:00Z", score = 0.8)
    private val near = Fx.event(id = "near", grade = Grade.NEAR, t = "2026-11-19T15:22:00Z")
    private val open = Fx.event(id = "olympiaberg", open = true, t = "2026-11-19T15:30:00Z")
    private val day = listOf(set1, near, rise, open, set2)

    @Test
    fun `sections by mode with the over ones last, open horizon apart`() {
        val noon = Instant.parse("2026-11-19T11:00:00Z")
        val s = DayModel.sections(day, EventFilter(), of, noon)
        assertEquals(listOf("rise"), s.earlier.map { it.sightlineId })
        assertEquals(emptyList(), s.sunrise)
        assertEquals(listOf("set1", "set2"), s.sunset.map { it.sightlineId })
        assertEquals(listOf("olympiaberg"), s.openHorizon.map { it.sightlineId })
        assertEquals(1, s.hiddenCount)          // the near one
        assertTrue(!s.isEmpty)
    }

    @Test
    fun `only today has an earlier group`() {
        val nextMorning = Instant.parse("2026-11-20T05:00:00Z")
        val s = DayModel.sections(day, EventFilter(), of, nextMorning)
        assertEquals(emptyList(), s.earlier)
        assertEquals(listOf("rise"), s.sunrise.map { it.sightlineId })
    }

    @Test
    fun `open horizon follows the mode filter`() {
        val s = DayModel.sections(day, EventFilter(modes = setOf(Mode.SUNRISE)), of, Instant.parse("2026-11-19T00:00:00Z"))
        assertEquals(emptyList(), s.openHorizon)
        assertEquals(listOf("rise"), s.sunrise.map { it.sightlineId })
        assertEquals(emptyList(), s.sunset)
    }

    @Test
    fun `next date with an event at the filter`() {
        val d = LocalDate.of(2026, 11, 19)
        val byDate: Map<LocalDate, List<HengeEvent>> = mapOf(
            d.plusDays(1) to listOf(Fx.event(grade = Grade.NEAR)),
            d.plusDays(3) to listOf(Fx.event(grade = Grade.GOOD)),
        )
        val eventsFor = { x: LocalDate -> byDate[x] ?: emptyList() }
        assertEquals(d.plusDays(3), DayModel.nextDateWithEvent(d, EventFilter(), of, 10, eventsFor))
        assertEquals(d.plusDays(1), DayModel.nextDateWithEvent(d, EventFilter(minGrade = Grade.NEAR), of, 10, eventsFor))
        assertNull(DayModel.nextDateWithEvent(d, EventFilter(), of, 2, eventsFor))
        assertNull(DayModel.nextDateWithEvent(d, EventFilter(minGrade = Grade.PERFECT), of, 10, eventsFor))
    }
}
