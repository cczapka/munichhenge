package de.munichhenge.app.model

import de.munichhenge.engine.Confidence
import de.munichhenge.engine.EngineConfig
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Links
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Poi
import de.munichhenge.engine.Sightline
import de.munichhenge.engine.Style
import de.munichhenge.solar.SunEvents
import de.munichhenge.solar.SunPosition
import java.time.Duration
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

    /** "Stand at the east end, north side, look W" (PLAN.md 4.3). */
    fun instruction(e: HengeEvent): String =
        if (e.openHorizon) "Open horizon, look ${compass16(e.bearing)}"
        else "Stand at the ${standLabel(e)}, look ${compass16(e.bearing)}"

    /**
     * "east end", or "east end, north side" when the sun sits over the middle of the far end
     * only from one side of the street. Looking along bearing b, your right hand points to b + 90.
     */
    fun standLabel(e: HengeEvent): String {
        if (e.openHorizon) return "open horizon"
        val end = "${standingEndWord(e.bearing)} end"
        val step = e.stepRightM ?: return end
        if (kotlin.math.abs(step) < UiConfig.SIDE_STEP_MIN_M) return end
        val side = compass8Word(e.bearing + if (step > 0) 90.0 else -90.0)
        return "$end, $side side"
    }

    /** "look WNW 287°" */
    fun lookLabel(e: HengeEvent): String = "look ${compass16(e.bearing)} ${Links.heading(e.bearing)}°"

    fun modeWord(mode: Mode): String = if (mode == Mode.SUNSET) "sunset" else "sunrise"

    fun gradeWord(grade: Grade): String = when (grade) {
        Grade.PERFECT -> "perfect"
        Grade.GOOD -> "good"
        Grade.NEAR -> "near"
    }

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    fun localTime(t: Instant): String = t.atZone(zone).format(timeFmt)

    /** "16:19–16:24": when to be there. */
    fun windowLabel(e: HengeEvent): String = localTime(e.windowStart) + "–" + localTime(e.windowEnd)

    fun styleWord(style: Style): String = when (style) {
        Style.ON_HORIZON -> "on the horizon"
        Style.IN_CANYON -> "between the buildings"
    }

    /** The card's large time: the best moment. */
    fun bigTime(e: HengeEvent): String = localTime(e.tBest)

    /** "sunset · on the horizon" */
    fun modeStyleLine(e: HengeEvent): String = "${modeWord(e.mode)} · ${styleWord(e.style)}"

    /** Window closed already. */
    fun isOver(e: HengeEvent, now: Instant): Boolean = e.windowEnd.isBefore(now)

    /**
     * "in 2 h 14 min" before the window opens (minutes rounded up, so 59 s reads "in 1 min"),
     * "now" while it is open, null afterwards or more than a day ahead.
     */
    fun countdown(e: HengeEvent, now: Instant): String? {
        if (isOver(e, now)) return null
        if (!now.isBefore(e.windowStart)) return "now"
        val left = Duration.between(now, e.windowStart)
        if (left > Duration.ofHours(UiConfig.COUNTDOWN_MAX_HOURS)) return null
        val minutes = (left.seconds + 59) / 60
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "in $m min"
            m == 0L -> "in $h h"
            else -> "in $h h $m min"
        }
    }

    enum class BadgeKind { CANYON, FEATURED, LENGTH, SPOTS, CONFIDENCE, TREES }

    data class Badge(val text: String, val kind: BadgeKind)

    /** Small facts under a card, in a fixed order; the grade has its own coloured badge. */
    fun badges(e: HengeEvent, sightline: Sightline?, config: EngineConfig = EngineConfig()): List<Badge> {
        val out = mutableListOf<Badge>()
        if (sightline?.canyon == true) out += Badge("canyon", BadgeKind.CANYON)
        if (sightline?.featured == true) out += Badge("featured", BadgeKind.FEATURED)
        if (sightline != null && !sightline.openHorizon) out += Badge(lengthLabel(sightline.lengthM), BadgeKind.LENGTH)
        if (e.poiIds.isNotEmpty()) out += Badge(spotsLabel(e.poiIds.size), BadgeKind.SPOTS)
        when (e.confidence) {
            Confidence.ESTIMATED -> out += Badge("estimated", BadgeKind.CONFIDENCE)
            Confidence.PARTIAL -> out += Badge("partly estimated", BadgeKind.CONFIDENCE)
            Confidence.MEASURED -> {}
        }
        if (e.treesInWay) {
            out += Badge(if (e.date.monthValue in config.leafOnMonths) "trees may hide it" else "through bare branches",
                BadgeKind.TREES)
        }
        return out
    }

    /** "423 m", "1.2 km" */
    fun lengthLabel(m: Double): String =
        if (m < 1000.0) "${Math.round(m)} m" else String.format(Locale.ROOT, "%.1f km", m / 1000.0)

    /** "Sunrise 07:26 at 119° · Sunset 16:31 at 241°" at the city centre: the bearings that matter today. */
    fun sunLine(date: LocalDate): String {
        val c = UiConfig.CITY_CENTRE
        fun part(word: String, t: Instant?) =
            if (t == null) "$word —" else "$word ${localTime(t)} at ${Math.round(SunPosition.at(t, c).azimuthDeg)}°"
        return part("Sunrise", SunEvents.sunrise(date, c, zone)) + " · " + part("Sunset", SunEvents.sunset(date, c, zone))
    }

    /** "16:21 sunset · 16:19–16:24 · on the horizon" */
    fun timeLine(e: HengeEvent): String = "${localTime(e.tBest)} ${modeWord(e.mode)} · ${windowLabel(e)} · ${styleWord(e.style)}"
    fun dateLabel(d: LocalDate): String = d.format(dateFmt)

    fun spotsLabel(n: Int): String = when (n) {
        0 -> "no spots"
        1 -> "1 spot"
        else -> "$n spots"
    }

    /** Card title. Never a raw id. */
    fun title(e: HengeEvent, sightline: Sightline?): String = sightline?.let(::sightlineName) ?: "Unknown place"

    /** The name, or "Unnamed path, 423 m". */
    fun sightlineName(sl: Sightline): String = sl.name ?: when {
        sl.openHorizon -> "Open horizon"
        else -> "Unnamed ${kindWord(sl.kind)}, ${lengthLabel(sl.lengthM)}"
    }

    /** The name, or "Unnamed café". */
    fun poiName(p: Poi): String = p.name ?: "Unnamed ${kindWord(p.kind)}"

    /** Unnamed highways are mostly footpaths and service ways, so "street" reads "path". */
    fun kindWord(kind: String): String = when (kind) {
        "street" -> "path"
        "river" -> "river bank"
        "cafe" -> "café"
        "biergarten" -> "beer garden"
        "open_horizon" -> "open horizon"
        else -> kind.replace('_', ' ')
    }

    /** Events grouped by local date in date order; within a day best first, then by time. */
    fun groupByDay(events: List<HengeEvent>): List<Pair<LocalDate, List<HengeEvent>>> =
        events.groupBy { it.date }.toSortedMap().map { (d, es) ->
            d to es.sortedWith(compareByDescending<HengeEvent> { it.score }.thenBy { it.tBest })
        }

    /** "Olympiaberg: sunrise 07:02 (88°), sunset 19:11 (272°)". */
    fun openHorizonLine(events: List<HengeEvent>, nameOf: (String) -> String?): String =
        events.groupBy { it.sightlineId }.entries.joinToString("\n") { (id, es) ->
            (nameOf(id) ?: "Open horizon") + ": " + es.sortedBy { it.tBest }.joinToString(", ") {
                "${modeWord(it.mode)} ${localTime(it.tBest)} (${Math.round(it.bearing)}°)"
            }
        }

    /** Map stroke width for a sightline on the selected date: thin when it has no event. */
    fun strokeWidthPx(quality: Double?, densityScale: Float): Float =
        if (quality == null) 2f * densityScale else (3f + 9f * quality.toFloat()) * densityScale

    /** Which end of the sightline the viewer stands at, in words for the detail screen. */
    fun endpointWord(sightline: Sightline, viewFrom: Endpoint): String {
        val bearing = sightline.bearing(viewFrom) ?: return "here"
        return standingEndWord(bearing)
    }

    /** Card title for one viewing direction: "Stand at the east end, look WNW (287°)". */
    fun directionTitle(sightline: Sightline, viewFrom: Endpoint): String {
        val b = sightline.bearing(viewFrom) ?: return "Open horizon"
        return "Stand at the ${standingEndWord(b)} end, look ${compass16(b)} (${Links.heading(b)}°)"
    }

    /** Card detail line: distance to the far end, its obstruction angle, a note for water ends. */
    fun directionDetail(sightline: Sightline, viewFrom: Endpoint, onWater: Boolean): String {
        val far = "Far end ${Math.round(sightline.lengthM)} m away, skyline there " +
            String.format(java.util.Locale.ROOT, "%.1f", sightline.obstruction(viewFrom)) + "° up"
        return if (onWater) "$far. This end is on the water: Street View shows the nearest bank or bridge." else far
    }

    private fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
}
