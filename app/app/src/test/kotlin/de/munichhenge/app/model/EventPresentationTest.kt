package de.munichhenge.app.model

import de.munichhenge.engine.Confidence
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventPresentationTest {
    @Test
    fun `titles never show a raw id`() {
        val e = Fx.event()
        assertEquals("Landwehrstraße", Presentation.title(e, Fx.sightline()))
        assertEquals("Unnamed path, 423 m", Presentation.title(e, Fx.sightline(name = null, lengthM = 423.4)))
        assertEquals("Unnamed canal, 1.2 km", Presentation.title(e, Fx.sightline(name = null, kind = "canal", lengthM = 1234.0)))
        assertEquals("Unknown place", Presentation.title(e, null))
        assertEquals("Unnamed café", Presentation.poiName(Fx.poi(null, "cafe")))
        assertEquals("Unnamed beer garden", Presentation.poiName(Fx.poi(null, "biergarten")))
        assertEquals("Kongress Bar", Presentation.poiName(Fx.poi("Kongress Bar", "bar")))
    }

    @Test
    fun `countdown to the window, rounded up to the minute`() {
        val e = Fx.event()                       // window 15:18:00-15:23:00 UTC
        val open = e.windowStart
        assertEquals("in 1 min", Presentation.countdown(e, open.minusSeconds(59)))
        assertEquals("in 1 h 1 min", Presentation.countdown(e, open.minus(Duration.ofMinutes(61))))
        assertEquals("in 2 h", Presentation.countdown(e, open.minus(Duration.ofMinutes(120))))
        assertEquals("now", Presentation.countdown(e, open))
        assertEquals("now", Presentation.countdown(e, e.tBest))
        assertNull(Presentation.countdown(e, e.windowEnd.plusSeconds(1)))
        assertNull(Presentation.countdown(e, open.minus(Duration.ofHours(25))))
        assertTrue(Presentation.isOver(e, e.windowEnd.plusSeconds(1)))
        assertTrue(!Presentation.isOver(e, e.windowEnd))
    }

    @Test
    fun `where to stand includes the side of the street`() {
        assertEquals("east end", Presentation.standLabel(Fx.event(bearing = 270.0)))
        assertEquals("east end", Presentation.standLabel(Fx.event(bearing = 270.0, stepRightM = 0.6)))
        // looking west, your right hand points north
        assertEquals("east end, north side", Presentation.standLabel(Fx.event(bearing = 270.0, stepRightM = 3.0)))
        assertEquals("east end, south side", Presentation.standLabel(Fx.event(bearing = 270.0, stepRightM = -3.0)))
        assertEquals("south-east end, north-east side", Presentation.standLabel(Fx.event(bearing = 300.0, stepRightM = 2.0)))
        assertEquals("Stand at the east end, north side, look W", Presentation.instruction(Fx.event(stepRightM = 3.0)))
        assertEquals("look WNW 287°", Presentation.lookLabel(Fx.event(bearing = 287.4)))
    }

    @Test
    fun `badges`() {
        val sl = Fx.sightline(canyon = true, featured = true, lengthM = 820.0)
        assertEquals(listOf("canyon", "featured", "820 m", "2 spots", "estimated"),
            Presentation.badges(Fx.event(spots = 2), sl).map { it.text })
        assertEquals(listOf("600 m"), Presentation.badges(Fx.event(confidence = Confidence.MEASURED), Fx.sightline()).map { it.text })
        assertEquals(listOf("600 m", "partly estimated"),
            Presentation.badges(Fx.event(confidence = Confidence.PARTIAL), Fx.sightline()).map { it.text })
        val july = Fx.event(t = "2026-07-10T19:00:00Z", trees = true, confidence = Confidence.MEASURED)
        val december = Fx.event(t = "2026-12-10T15:00:00Z", trees = true, confidence = Confidence.MEASURED)
        assertEquals("trees may hide it", Presentation.badges(july, Fx.sightline()).last().text)
        assertEquals("through bare branches", Presentation.badges(december, Fx.sightline()).last().text)
    }

    @Test
    fun `time and style lines`() {
        val e = Fx.event()
        assertEquals("16:20", Presentation.bigTime(e))
        assertEquals("sunset · on the horizon", Presentation.modeStyleLine(e))
        assertEquals("16:18–16:23", Presentation.windowLabel(e))
    }

    /** Reference: astral 3.2 for Marienplatz, 2026-11-19 -> sunrise 06:26:31Z at 118.96°,
     * sunset 15:31:06Z at 240.90°. Our solver may differ by a minute and a degree. */
    @Test
    fun `sun line for the day matches an independent calculation`() {
        val line = Presentation.sunLine(LocalDate.of(2026, 11, 19))
        val m = Regex("""Sunrise (\d\d:\d\d) at (\d+)° · Sunset (\d\d:\d\d) at (\d+)°""").matchEntire(line)
            ?: error("unexpected sun line: $line")
        fun near(hhmm: String, ref: String) =
            abs(Duration.between(LocalTime.parse(ref), LocalTime.parse(hhmm)).toMinutes()) <= 1
        assertTrue(near(m.groupValues[1], "07:26"), line)
        assertTrue(abs(m.groupValues[2].toInt() - 119) <= 1, line)
        assertTrue(near(m.groupValues[3], "16:31"), line)
        assertTrue(abs(m.groupValues[4].toInt() - 241) <= 1, line)
    }
}
