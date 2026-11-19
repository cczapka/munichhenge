package de.munichhenge.engine

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Debug CLI over the engine (see core-engine/build.gradle.kts for usage). Loads sightlines.json and pois.json
 * relative to the repo root and prints events in Europe/Berlin local time.
 */
fun main(args: Array<String>) {
    val dataDir = File(System.getProperty("munichhenge.data") ?: "data")
    val data = HengeData.fromJson(File(dataDir, "sightlines.json").readText(), File(dataDir, "pois.json").readText())
    val engine = HengeEngine(data)
    val cmd = args.getOrNull(0) ?: "upcoming"
    val date = args.getOrNull(1)?.let(LocalDate::parse) ?: LocalDate.now(engine.config.zone)
    when (cmd) {
        "upcoming" -> {
            val days = args.getOrNull(2)?.toInt() ?: 14
            val minQ = args.getOrNull(3)?.toDouble() ?: 0.7
            println("Best events $date + $days days, quality >= $minQ")
            engine.bestUpcoming(date, days, minQ).take(40).forEach { println(format(it, data, engine)) }
        }
        "date" -> {
            println("Events on $date")
            engine.eventsFor(date).filter { !it.openHorizon }.forEach { println(format(it, data, engine)) }
        }
        "next" -> {
            val id = args.getOrNull(2) ?: error("usage: next <date> <sightlineId|poiId> [count]")
            val count = args.getOrNull(3)?.toInt() ?: 5
            val events = if (id.startsWith("sl_") || id.startsWith("f_")) engine.nextEventsFor(id, date, count)
            else engine.nextEventsForPoi(id, date, count)
            println("Next $count events for $id from $date")
            events.forEach { println(format(it, data, engine)) }
        }
        "check" -> {
            // The best events of a day with links to check each one before going (M5)
            val n = args.getOrNull(2)?.toInt() ?: 10
            println("Top $n events on $date, with links")
            engine.eventsFor(date).filter { !it.openHorizon }.take(n).forEach { e ->
                val sl = data.sightline(e.sightlineId) ?: return@forEach
                val t = LinkTarget.forDirection(sl, e.viewFrom)
                println(format(e, data, engine))
                println("    Street View: ${Links.streetView(t)}" + if (t.onWater) "  (on water: nearest imagery)" else "")
                println("    Walk there:  ${Links.walk(t.point)}")
            }
        }
        "dump" -> {
            // JSON lines for the independent cross-check (IMPROVEMENT-PLAN.md 3.5)
            val days = args.getOrNull(2)?.toInt() ?: 7
            for (i in 0 until days) {
                engine.eventsFor(date.plusDays(i.toLong())).filter { !it.openHorizon }.forEach { println(dumpJson(it, data, engine)) }
            }
        }
        else -> error("unknown command $cmd (upcoming | date | next | check | dump)")
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun format(e: HengeEvent, data: HengeData, engine: HengeEngine): String {
    val sl = data.sightline(e.sightlineId)
    val z = engine.config.zone
    val best = e.tBest.atZone(z).format(timeFmt)
    val window = e.windowStart.atZone(z).format(clockFmt) + "-" + e.windowEnd.atZone(z).format(clockFmt)
    val stand = if (e.viewFrom == Endpoint.A) "A" else "B"
    return "%s (%s) %-7s %-7s %-10s q=%.2f s=%.2f off=%+.2f° h=%.2f° %-12s %s  [stand at %s, look %.0f°]  %d spots%s%s".format(
        best, window, e.mode, e.grade, e.style, e.quality, e.score, e.offsetDeg, e.heightAboveFloorDeg, e.sightlineId,
        sl?.name ?: "?", stand, e.bearing, e.poiIds.size, if (sl?.canyon == true) "  canyon" else "",
        if (e.confidence == Confidence.ESTIMATED) "" else "  " + e.confidence.name.lowercase(),
    )
}

private val clockFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** One JSON object per event with what an independent check needs (sun, standing point, profile). */
private fun dumpJson(e: HengeEvent, data: HengeData, engine: HengeEngine): String {
    val sl = data.sightline(e.sightlineId)!!
    val p = sl.standingPoint(e.viewFrom)
    val profile = sl.profile(e.viewFrom)?.shifted(engine.config.obstructionOffsetDeg)
        ?: SkylineProfile.synthetic(sl.obstruction(e.viewFrom) + engine.config.obstructionOffsetDeg, sl.lengthM, sl.canyon,
            engine.config.assumedStreetWidthM)
    val hard = (0 until SkylineProfile.SIZE).joinToString(",") { "%.4f".format(java.util.Locale.ROOT, profile.hardAt(SkylineProfile.offsetOf(it))) }
    fun d(x: Double) = "%.6f".format(java.util.Locale.ROOT, x)
    return "{\"id\":\"${e.sightlineId}\",\"from\":\"${e.viewFrom}\",\"mode\":\"${e.mode}\",\"date\":\"${e.date}\"," +
        "\"tBest\":\"${e.tBest}\",\"windowStart\":\"${e.windowStart}\",\"windowEnd\":\"${e.windowEnd}\"," +
        "\"lat\":${d(p.lat)},\"lon\":${d(p.lon)},\"bearing\":${d(e.bearing)},\"sunAz\":${d(e.sunAzimuthDeg)}," +
        "\"sunAlt\":${d(e.sunAltitudeDeg)},\"offset\":${d(e.offsetDeg)},\"height\":${d(e.heightAboveFloorDeg)}," +
        "\"grade\":\"${e.grade}\",\"style\":\"${e.style}\",\"gate\":${d(engine.config.openSideGateDeg)}," +
        "\"maxHeight\":${d(engine.config.maxHeightAboveFloorDeg)},\"hard\":[$hard]}"
}
