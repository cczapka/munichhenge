package de.munichhenge.app.model

import de.munichhenge.engine.Grade
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class UpcomingModelTest {
    private val sl = Fx.sightline()
    private val of = { _: String -> sl }
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val d1 = LocalDate.of(2026, 11, 19)
    private val d2 = LocalDate.of(2026, 11, 20)

    /** Seven sunsets on d2 with scores .30-.90, two on d1 (one already over), and a near one. */
    private val events = (1..7).map { Fx.event(id = "d2_$it", score = 0.2 + it / 10.0, t = "2026-11-20T15:${10 + it}:00Z") } +
        listOf(
            Fx.event(id = "d1_over", score = 0.99, t = "2026-11-19T06:40:00Z"),
            Fx.event(id = "d1_ok", score = 0.85, t = "2026-11-19T15:20:00Z"),
            Fx.event(id = "d2_near", grade = Grade.NEAR, score = 0.95, t = "2026-11-20T15:40:00Z"),
        )

    @Test
    fun `days are capped, counted and in date order`() {
        val u = UpcomingModel.build(events, EventFilter(), of, now, expanded = emptySet(), perDay = 5)
        assertEquals(listOf(d1, d2), u.days.map { it.date })
        assertEquals(listOf("d1_ok"), u.days[0].events.map { it.sightlineId })
        assertEquals(listOf("d2_7", "d2_6", "d2_5", "d2_4", "d2_3"), u.days[1].events.map { it.sightlineId })
        assertEquals(7, u.days[1].total)
        assertEquals(2, u.days[1].hidden)
    }

    @Test
    fun `an expanded day shows everything`() {
        val u = UpcomingModel.build(events, EventFilter(), of, now, expanded = setOf(d2), perDay = 5)
        assertEquals(7, u.days[1].events.size)
        assertEquals(0, u.days[1].hidden)
    }

    @Test
    fun `hero is the best three still ahead at the filter`() {
        val u = UpcomingModel.build(events, EventFilter(), of, now, expanded = emptySet(), heroCount = 3)
        assertEquals(listOf("d2_7", "d1_ok", "d2_6"), u.hero.map { it.sightlineId })
    }

    @Test
    fun `nothing at the filter gives no days`() {
        val u = UpcomingModel.build(events, EventFilter(canyonOnly = true), of, now, expanded = emptySet())
        assertEquals(emptyList(), u.days)
        assertEquals(emptyList(), u.hero)
    }
}
