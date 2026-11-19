package de.munichhenge.engine

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Loads the committed sightlines.json and pois.json (built by the pipeline): budgets of IMPROVEMENT-PLAN.md 3.3. */
class RealDataSmokeTest {
    private val dataDir = File("../../data")

    private fun load(): HengeData {
        val s = File(dataDir, "sightlines.json")
        val p = File(dataDir, "pois.json")
        assumeTrue(s.exists() && p.exists(), "data/*.json not built")
        return HengeData.fromJson(s.readText(), p.readText())
    }

    private fun seconds(block: () -> Unit): Double {
        val t0 = System.nanoTime(); block(); return (System.nanoTime() - t0) / 1e9
    }

    @Test
    fun `12 - performance budgets on the real data`() {
        val engine = HengeEngine(load())
        assertTrue(engine.data.sightlines.size > 500)
        engine.eventsFor(LocalDate.of(2025, 6, 1)); engine.eventsFor(LocalDate.of(2025, 6, 2))   // warm-up (JIT)
        val oneDay = seconds { engine.eventsFor(LocalDate.of(2026, 3, 20)) }
        val fortnight = seconds { engine.bestUpcoming(LocalDate.of(2026, 4, 1), 14, 0.0) }
        val sl = engine.data.sightlines.first { it.name == "Prinzregentenstraße" && it.bearingAb != null }
        val oneSightlineYear = seconds { engine.nextEventsFor(sl.id, LocalDate.of(2026, 1, 1), 10_000) }
        var n = 0
        val fullYear = seconds {
            var d = LocalDate.of(2027, 1, 1)
            while (d.year == 2027) { n += engine.eventsFor(d).size; d = d.plusDays(1) }
        }
        println("real data: ${engine.data.sightlines.size} sightlines; one day %.3f s, 14 days %.2f s, one sightline year %.3f s, full year %.2f s, $n events"
            .format(oneDay, fortnight, oneSightlineYear, fullYear))
        assertTrue(oneDay < 0.05, "one day $oneDay s")
        assertTrue(fortnight < 1.0, "14 days $fortnight s")
        assertTrue(oneSightlineYear < 0.3, "one sightline year $oneSightlineYear s")
        assertTrue(fullYear < 6.0, "full year $fullYear s")
        assertTrue(n > 1000)
    }

    @Test
    fun `prinzregentenstrasse looking west has sunset seasons`() {
        val engine = HengeEngine(load())
        val sl = engine.data.sightlines.firstOrNull { it.name == "Prinzregentenstraße" && it.bearingAb != null &&
            (kotlin.math.abs(it.bearingAb!! - 287.0) < 1.0 || kotlin.math.abs(it.bearingBa!! - 287.0) < 1.0) }
        assumeTrue(sl != null, "no 287° Prinzregentenstraße run in the data")
        val next = engine.nextEventsFor(sl!!.id, LocalDate.of(2026, 1, 1), 200).filter { it.mode == Mode.SUNSET }
        assertTrue(next.isNotEmpty())
        assertTrue(next.all { it.bearing in 286.0..288.0 })
        val months = next.map { it.date.monthValue }.toSet()
        assertTrue(months.any { it in 4..5 } && months.any { it in 7..8 }, "months $months")
    }
}
