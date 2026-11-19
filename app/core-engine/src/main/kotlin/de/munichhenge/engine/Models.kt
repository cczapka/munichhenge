package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint
import java.time.Instant
import java.time.LocalDate

/** Which end of a sightline. A viewer standing at A and looking toward B sees `bearingAb`. */
enum class Endpoint {
    A, B;

    val other: Endpoint get() = if (this == A) B else A
}

enum class Mode { SUNRISE, SUNSET }

/** Event grades; thresholds in EngineConfig (IMPROVEMENT-PLAN.md 3.2). */
enum class Grade { PERFECT, GOOD, NEAR }

/** How an event looks at its best moment. */
enum class Style {
    /** The disc sits within one diameter of the skyline: the classic henge picture. */
    ON_HORIZON,
    /** The sun is centred between the walls, higher up. */
    IN_CANYON,
}

/** A straight run between endpoints A and B (see CLAUDE.md vocabulary). */
data class Sightline(
    val id: String,
    val name: String?,
    val kind: String,
    val a: GeoPoint,
    val b: GeoPoint,
    val lengthM: Double,
    val maxOffsetM: Double,
    /** Azimuth seen from A toward B; null for an open-horizon virtual sightline. */
    val bearingAb: Double?,
    val bearingBa: Double?,
    /** Apparent altitude of whatever blocks the view at A, seen from B. */
    val obstructionTowardADeg: Double,
    val obstructionTowardBDeg: Double,
    val canyon: Boolean,
    val featured: Boolean,
    val notes: String?,
    val osmWayIds: List<Long>,
    /** Measured skyline seen from A toward B (schema v2, M8); null = synthesise from the fields above. */
    val profileAb: SkylineProfile? = null,
    /** Measured skyline seen from B toward A. */
    val profileBa: SkylineProfile? = null,
) {
    /** Measured skyline profile for a viewing direction, if the data carries one. */
    fun profile(viewFrom: Endpoint): SkylineProfile? = if (viewFrom == Endpoint.A) profileAb else profileBa

    /** All-azimuth virtual sightline of an open_horizon spot (a == b, no bearing). */
    val openHorizon: Boolean get() = bearingAb == null

    /** Where the viewer stands. */
    fun standingPoint(viewFrom: Endpoint): GeoPoint = if (viewFrom == Endpoint.A) a else b

    /** Azimuth the viewer at `viewFrom` looks toward (null for open horizon). */
    fun bearing(viewFrom: Endpoint): Double? = if (viewFrom == Endpoint.A) bearingAb else bearingBa

    /** Obstruction angle at the far end, as seen from `viewFrom`. */
    fun obstruction(viewFrom: Endpoint): Double =
        if (viewFrom == Endpoint.A) obstructionTowardBDeg else obstructionTowardADeg
}

/** A POI's view down a sightline: `toward` is the endpoint it looks at. */
data class View(val sightlineId: String, val toward: Endpoint, val distanceM: Double)

/** A spot: bar, restaurant, café, biergarten, viewpoint or featured place. */
data class Poi(
    val id: String,
    val name: String?,
    val kind: String,
    val at: GeoPoint,
    val osmId: String?,
    val openHorizon: Boolean,
    val views: List<View>,
    val tags: Map<String, String>,
    val featured: Boolean,
    val notes: String?,
)

/**
 * One sightline x one direction x one date x sunrise|sunset (IMPROVEMENT-PLAN.md 3.2): the sun
 * is visible down the sightline, above the skyline and inside the street's opening.
 */
data class HengeEvent(
    val sightlineId: String,
    /** Where you stand. */
    val viewFrom: Endpoint,
    /** Azimuth you look toward. For open-horizon events the sun's azimuth at tBest. */
    val bearing: Double,
    val mode: Mode,
    /** Local (config zone) calendar date. */
    val date: LocalDate,
    /** The best moment: centred, low and with the disc showing. */
    val tBest: Instant,
    /** The span around tBest worth being there for, chronological (windowStart <= windowEnd). */
    val windowStart: Instant,
    val windowEnd: Instant,
    val style: Style,
    val sunAzimuthDeg: Double,
    /** Apparent altitude of the sun's centre at tBest. */
    val sunAltitudeDeg: Double,
    /** Sun's azimuth minus the bearing at tBest, (-4, 4); positive = right of the axis. */
    val offsetDeg: Double,
    /** Sun's centre above the skyline at its offset, degrees. */
    val heightAboveFloorDeg: Double,
    /** Share of the disc's diameter above the skyline, 0..1. */
    val discFraction: Double,
    /** 0 = centred in the opening, 1 = touching a wall. */
    val centring: Double,
    /** Metres to step right (negative: left) of the centre line so the sun sits over the middle of
     * the far end; null when that is more than half the street width. */
    val stepRightM: Double?,
    /** A tree rises above the sun at tBest (soft layer, M8). */
    val treesInWay: Boolean,
    val confidence: Confidence,
    /** How good the alignment is today, 0..1. */
    val quality: Double,
    /** How worthwhile the place is, 0..1, independent of the date. */
    val interest: Double,
    /** Ranking value: quality x (interestFloor + (1 - interestFloor) x interest). */
    val score: Double,
    val grade: Grade,
    /** POIs that look down this sightline in this direction, sorted by id. */
    val poiIds: List<String>,
    /** True for events of an open-horizon virtual sightline (always "aligned"). */
    val openHorizon: Boolean = false,
)
