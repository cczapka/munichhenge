package de.munichhenge.app.model

import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Sightline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure presentation helpers (no Android imports) so they can be unit-tested on the JVM. */
object Presentation {
    val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private val POINTS_16 = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    private val WORDS_8 = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")

    /** 16-point compass name for a bearing, e.g. 270 -> "W", 292.5 -> "WNW". */
    fun compass16(bearingDeg: Double): String {
        val idx = Math.floorMod(Math.round(norm(bearingDeg) / 22.5).toInt(), 16)
        return POINTS_16[idx]
    }

    /** 8-point compass word, e.g. 90 -> "east". */
    fun compass8Word(bearingDeg: Double): String {
        val idx = Math.floorMod(Math.round(norm(bearingDeg) / 45.0).toInt(), 8)
        return WORDS_8[idx]
    }

    /** The end you stand at is opposite to where you look: bearing 270 (look W) -> "east". */
    fun standingEndWord(bearingDeg: Double): String = compass8Word(bearingDeg + 180.0)

    /** "Stand at the east end, look W" (PLAN.md 4.3). */
    fun instruction(e: HengeEvent): String =
        if (e.openHorizon) "Open horizon, look ${compass16(e.bearing)}"
        else "Stand at the ${standingEndWord(e.bearing)} end, look ${compass16(e.bearing)}"

    fun modeWord(mode: Mode): String = if (mode == Mode.SUNSET) "sunset" else "sunrise"

    fun gradeWord(grade: Grade): String = when (grade) {
        Grade.PERFECT -> "perfect"
        Grade.GOOD -> "good"
        Grade.NEAR -> "near"
    }

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    fun localTime(t: Instant): String = t.atZone(zone).format(timeFmt)
    fun dateLabel(d: LocalDate): String = d.format(dateFmt)

    fun spotsLabel(n: Int): String = when (n) {
        0 -> "no spots"
        1 -> "1 spot"
        else -> "$n spots"
    }

    fun title(e: HengeEvent, sightline: Sightline?): String = sightline?.name ?: e.sightlineId

    /** Events grouped by local date in date order; within a day best first, then by time. */
    fun groupByDay(events: List<HengeEvent>): List<Pair<LocalDate, List<HengeEvent>>> =
        events.groupBy { it.date }.toSortedMap().map { (d, es) ->
            d to es.sortedWith(compareByDescending<HengeEvent> { it.quality }.thenBy { it.tFull })
        }

    /** Map stroke width for a sightline on the selected date: thin when it has no event. */
    fun strokeWidthPx(quality: Double?, densityScale: Float): Float =
        if (quality == null) 2f * densityScale else (3f + 9f * quality.toFloat()) * densityScale

    /** Which end of the sightline the viewer stands at, in words for the detail screen. */
    fun endpointWord(sightline: Sightline, viewFrom: Endpoint): String {
        val bearing = sightline.bearing(viewFrom) ?: return "here"
        return standingEndWord(bearing)
    }

    private fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
}
