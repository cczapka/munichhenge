package de.munichhenge.engine

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Loads the committed sightlines.json and pois.json (built by the pipeline) and computes a full year. */
class RealDataSmokeTest {
    private val dataDir = File("../../data")

    private fun load(): HengeData {
        val s = File(dataDir, "sightlines.json")
        val p = File(dataDir, "pois.json")
        assumeTrue(s.exists() && p.exists(), "data/*.json not built")
        return HengeData.fromJson(s.readText(), p.readText())
    }

    @Test
    fun `full year over the real data in under 2 seconds`() {
        val engine = HengeEngine(load())
        assertTrue(engine.data.sightlines.size > 500)
        engine.eventsFor(LocalDate.of(2026, 1, 1))   // warm-up (JIT)
        val t0 = System.nanoTime()
        var n = 0
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            n += engine.eventsFor(date).size
            date = date.plusDays(1)
        }
        val seconds = (System.nanoTime() - t0) / 1e9
        println("real data: ${engine.data.sightlines.size} sightlines, $n events in 2026, ${"%.2f".format(seconds)} s")
        assertTrue(seconds < 2.0, "took $seconds s")
        assertTrue(n > 1000)
    }

    @Test
    fun `prinzregentenstrasse looking west has sunset seasons`() {
        val engine = HengeEngine(load())
        val sl = engine.data.sightlines.firstOrNull { it.name == "Prinzregentenstraße" && it.bearingAb != null &&
            (kotlin.math.abs(it.bearingAb!! - 287.0) < 1.0 || kotlin.math.abs(it.bearingBa!! - 287.0) < 1.0) }
        assumeTrue(sl != null, "no 287° Prinzregentenstraße run in the data")
        val next = engine.nextEventsFor(sl!!.id, LocalDate.of(2026, 1, 1), 50).filter { it.mode == Mode.SUNSET }
        assertTrue(next.isNotEmpty())
        assertTrue(next.all { it.bearing in 286.0..288.0 })
        val months = next.map { it.date.monthValue }.toSet()
        assertTrue(months.any { it in 4..5 } && months.any { it in 7..8 }, "months $months")
    }
}
