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

/** Alignment error bands (CLAUDE.md): perfect ≤ 0.5°, good ≤ 1.5°, near ≤ 3°. */
enum class Grade { PERFECT, GOOD, NEAR }

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
) {
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

/** One sightline × one direction × one date × sunrise|sunset (PLAN.md 4.2). */
data class HengeEvent(
    val sightlineId: String,
    /** Where you stand. */
    val viewFrom: Endpoint,
    /** Azimuth you look toward. For open-horizon events this is the sun's azimuth at tFull. */
    val bearing: Double,
    val mode: Mode,
    /** Local (config zone) calendar date. */
    val date: LocalDate,
    /** Sun centre at obstruction + one radius: the whole disk rests on the line. */
    val tFull: Instant,
    /** Sun centre exactly at the obstruction angle: half the disk showing. */
    val tHalf: Instant,
    val azimuthAtFull: Double,
    val alignmentErrorDeg: Double,
    /** 0..1, max(0, 1 - error/near) plus optional canyon bonus. */
    val quality: Double,
    val grade: Grade,
    /** POIs that look down this sightline in this direction, sorted by id. */
    val poiIds: List<String>,
    /** True for events of an open-horizon virtual sightline (always "aligned"). */
    val openHorizon: Boolean = false,
)
