package de.munichhenge.app.model

import de.munichhenge.engine.Grade
import de.munichhenge.engine.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventFilterTest {
    private val street = Fx.sightline()
    private val canyon = Fx.sightline(id = "sl_c", canyon = true)

    @Test
    fun `default shows good and perfect events in both modes, never near or open horizon`() {
        val f = EventFilter()
        assertTrue(f.accepts(Fx.event(grade = Grade.PERFECT), street))
        assertTrue(f.accepts(Fx.event(grade = Grade.GOOD, mode = Mode.SUNRISE), street))
        assertFalse(f.accepts(Fx.event(grade = Grade.NEAR), street))
        assertFalse(f.accepts(Fx.event(grade = Grade.PERFECT, open = true), street))
    }

    @Test
    fun `minimum grade, modes, spots and canyon rules`() {
        assertFalse(EventFilter(minGrade = Grade.PERFECT).accepts(Fx.event(grade = Grade.GOOD), street))
        assertTrue(EventFilter(minGrade = Grade.NEAR).accepts(Fx.event(grade = Grade.NEAR), street))
        assertFalse(EventFilter(modes = setOf(Mode.SUNSET)).accepts(Fx.event(mode = Mode.SUNRISE), street))
        assertFalse(EventFilter(withSpots = true).accepts(Fx.event(spots = 0), street))
        assertTrue(EventFilter(withSpots = true).accepts(Fx.event(spots = 2), street))
        assertFalse(EventFilter(canyonOnly = true).accepts(Fx.event(), street))
        assertFalse(EventFilter(canyonOnly = true).accepts(Fx.event(), null))
        assertTrue(EventFilter(canyonOnly = true).accepts(Fx.event(id = "sl_c"), canyon))
    }

    @Test
    fun `at least one mode stays on`() {
        val both = EventFilter()
        val sunset = both.toggleMode(Mode.SUNRISE)
        assertEquals(setOf(Mode.SUNSET), sunset.modes)
        assertEquals(setOf(Mode.SUNSET), sunset.toggleMode(Mode.SUNSET).modes)
        assertEquals(setOf(Mode.SUNRISE, Mode.SUNSET), sunset.toggleMode(Mode.SUNRISE).modes)
    }

    @Test
    fun `apply filters and sorts by the chosen order`() {
        val a = Fx.event(id = "a", score = 0.6, quality = 0.95, t = "2026-11-19T15:30:00Z")
        val b = Fx.event(id = "b", score = 0.9, quality = 0.7, t = "2026-11-19T15:20:00Z")
        val c = Fx.event(id = "c", score = 0.7, quality = 0.8, t = "2026-11-19T06:30:00Z", mode = Mode.SUNRISE)
        val near = Fx.event(id = "n", grade = Grade.NEAR, score = 0.99)
        val all = listOf(a, b, c, near)
        val of = { _: String -> street }
        assertEquals(listOf("b", "c", "a"), EventFilter().apply(all, of).map { it.sightlineId })
        assertEquals(listOf("a", "c", "b"), EventFilter(sort = SortOrder.ALIGNMENT).apply(all, of).map { it.sightlineId })
        assertEquals(listOf("c", "b", "a"), EventFilter(sort = SortOrder.TIME).apply(all, of).map { it.sightlineId })
    }

    @Test
    fun `summary chip text`() {
        assertEquals("Sunrise & sunset · good+", EventFilter().summary())
        assertEquals("Sunset · perfect", EventFilter(modes = setOf(Mode.SUNSET), minGrade = Grade.PERFECT).summary())
        assertEquals("Sunrise · all grades · spots · canyons · by time",
            EventFilter(modes = setOf(Mode.SUNRISE), minGrade = Grade.NEAR, withSpots = true, canyonOnly = true,
                sort = SortOrder.TIME).summary())
    }

    @Test
    fun `encoding round trips and bad input falls back to the defaults`() {
        val filters = listOf(
            EventFilter(),
            EventFilter(modes = setOf(Mode.SUNRISE), minGrade = Grade.NEAR, withSpots = true, canyonOnly = true, sort = SortOrder.TIME),
            EventFilter(modes = setOf(Mode.SUNSET), minGrade = Grade.PERFECT, sort = SortOrder.ALIGNMENT),
        )
        for (f in filters) assertEquals(f, EventFilter.decode(f.encode()))
        assertEquals(EventFilter(), EventFilter.decode(null))
        assertEquals(EventFilter(), EventFilter.decode(""))
        assertEquals(EventFilter(), EventFilter.decode("garbage;;=x"))
        assertEquals(EventFilter(), EventFilter.decode("modes=;grade=AWESOME;sort=RANDOM"))
        assertEquals(EventFilter(withSpots = true), EventFilter.decode("spots=1"))
    }
}
