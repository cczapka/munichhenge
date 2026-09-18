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
        else -> error("unknown command $cmd")
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun format(e: HengeEvent, data: HengeData, engine: HengeEngine): String {
    val sl = data.sightline(e.sightlineId)
    val local = e.tFull.atZone(engine.config.zone).format(timeFmt)
    val stand = if (e.viewFrom == Endpoint.A) "A" else "B"
    return "%s  %-7s %-7s q=%.2f err=%.2f° %-14s %s  [stand at %s, look %.0f°]  %d spots%s".format(
        local, e.mode, e.grade, e.quality, e.alignmentErrorDeg, e.sightlineId, sl?.name ?: "?", stand, e.bearing,
        e.poiIds.size, if (sl?.canyon == true) "  canyon" else "",
    )
}
