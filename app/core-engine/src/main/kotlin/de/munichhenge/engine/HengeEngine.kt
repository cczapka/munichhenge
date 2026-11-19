package de.munichhenge.engine

import de.munichhenge.solar.AltitudeSolver
import de.munichhenge.solar.GeoPoint
import de.munichhenge.solar.SunEvents
import de.munichhenge.solar.SunPosition
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Computes henge events with skyline profiles (IMPROVEMENT-PLAN.md section 3). Pure and
 * thread-safe; results per date are cached in memory.
 *
 * For each viewing direction the sun is followed through the low part of its path. A moment
 * counts when the sun's centre is above the skyline at its azimuth offset, inside the street's
 * opening, and at most maxHeightAboveFloorDeg above the skyline. The event is the best such
 * moment (centred, low, disc showing) and the window of moments scoring nearly as well.
 */
class HengeEngine(val data: HengeData, val config: EngineConfig = EngineConfig()) {
    private val cache = ConcurrentHashMap<LocalDate, List<HengeEvent>>()
    private val dayCache = ConcurrentHashMap<LocalDate, Map<Mode, Band>>()

    /** One viewing direction with everything that does not depend on the date. */
    private class Direction(
        val sl: Sightline, val viewFrom: Endpoint, val bearing: Double, val standing: GeoPoint,
        val profile: SkylineProfile, val interest: Double, val poiIds: List<String>,
    )

    /** Where the sun is while it is low on a date: the anchor (sunrise/sunset) and its azimuth range. */
    private class Band(val anchor: Instant, val minAz: Double, val maxAz: Double)

    private val directions: Map<String, List<Direction>> = data.sightlines.associate { sl ->
        sl.id to Endpoint.entries.mapNotNull { end -> direction(sl, end) }
    }

    /** City reference point for the per-day prefilter: centre of all standing points. */
    private val reference: GeoPoint = data.sightlines.let { s ->
        if (s.isEmpty()) GeoPoint(48.14, 11.58)
        else GeoPoint(s.sumOf { (it.a.lat + it.b.lat) / 2 } / s.size, s.sumOf { (it.a.lon + it.b.lon) / 2 } / s.size)
    }

    private fun direction(sl: Sightline, end: Endpoint): Direction? {
        val bearing = sl.bearing(end) ?: return null
        val profile = sl.profile(end)?.shifted(config.obstructionOffsetDeg)
            ?: SkylineProfile.synthetic(sl.obstruction(end) + config.obstructionOffsetDeg, sl.lengthM, sl.canyon,
                config.assumedStreetWidthM)
        val poiIds = data.poiIdsFor(sl.id, end)
        val interest = Interest.of(sl.lengthM, sl.featured, sl.name != null, Interest.framed(profile, config), poiIds.size, config)
        return Direction(sl, end, bearing, sl.standingPoint(end), profile, interest, poiIds)
    }

    // ------------------------------------------------------------------------------- queries

    /** All events of a local date, best score first. */
    fun eventsFor(date: LocalDate, modes: Set<Mode> = ALL_MODES): List<HengeEvent> {
        val all = cache.getOrPut(date) {
            data.sightlines.flatMap { sl -> evaluate(sl, date, ALL_MODES) }
                .sortedWith(compareByDescending<HengeEvent> { it.score }.thenBy { it.tBest }.thenBy { it.sightlineId })
        }
        return if (modes.size == Mode.entries.size) all else all.filter { it.mode in modes }
    }

    /** The next `count` events of a sightline (either direction) from `from`, in time order. */
    fun nextEventsFor(sightlineId: String, from: LocalDate, count: Int, modes: Set<Mode> = ALL_MODES): List<HengeEvent> {
        val sl = data.sightline(sightlineId) ?: return emptyList()
        return scan(from, count) { date -> evaluate(sl, date, modes) }
    }

    /** The next `count` events visible from a POI, via its views. */
    fun nextEventsForPoi(poiId: String, from: LocalDate, count: Int, modes: Set<Mode> = ALL_MODES): List<HengeEvent> {
        val poi = data.poi(poiId) ?: return emptyList()
        val wanted = poi.views.mapNotNull { v -> data.sightline(v.sightlineId)?.let { it to v.toward.other } }
        if (wanted.isEmpty()) return emptyList()
        return scan(from, count) { date ->
            wanted.flatMap { (sl, viewFrom) -> evaluate(sl, date, modes).filter { it.viewFrom == viewFrom } }
        }
    }

    /** Best real (non open-horizon) events in [from, from + days) with quality >= minQuality, best score first. */
    fun bestUpcoming(from: LocalDate, days: Int, minQuality: Double, modes: Set<Mode> = ALL_MODES): List<HengeEvent> =
        (0 until days).map { from.plusDays(it.toLong()) }
            .flatMap { eventsFor(it, modes) }
            .filter { !it.openHorizon && it.quality >= minQuality }
            .sortedWith(compareByDescending<HengeEvent> { it.score }.thenBy { it.tBest })

    private fun scan(from: LocalDate, count: Int, perDay: (LocalDate) -> List<HengeEvent>): List<HengeEvent> {
        if (count <= 0) return emptyList()
        val out = ArrayList<HengeEvent>()
        var date = from
        var scanned = 0
        while (out.size < count && scanned < config.scanLimitDays) {
            out += perDay(date).sortedBy { it.tBest }
            date = date.plusDays(1)
            scanned++
        }
        return if (out.size > count) out.subList(0, count) else out
    }

    // ---------------------------------------------------------------------------- evaluation

    private fun bracket(mode: Mode, anchor: Instant): Pair<Instant, Instant> = if (mode == Mode.SUNSET)
        anchor.minus(config.sunsetBracketBefore) to anchor.plus(config.sunsetBracketAfter)
    else
        anchor.minus(config.sunriseBracketBefore) to anchor.plus(config.sunriseBracketAfter)

    private fun bands(date: LocalDate): Map<Mode, Band> = dayCache.getOrPut(date) {
        val out = HashMap<Mode, Band>()
        for (mode in Mode.entries) {
            val anchor = (if (mode == Mode.SUNSET) SunEvents.sunset(date, reference, config.zone)
            else SunEvents.sunrise(date, reference, config.zone)) ?: continue
            val (from, to) = bracket(mode, anchor)
            val tHi = AltitudeSolver.solve(reference, config.maxScanAltitudeDeg, from, to)?.time ?: (if (mode == Mode.SUNSET) from else to)
            val tLo = AltitudeSolver.solve(reference, config.minScanAltitudeDeg, from, to)?.time ?: (if (mode == Mode.SUNSET) to else from)
            val a1 = SunPosition.at(tHi, reference).azimuthDeg
            val a2 = SunPosition.at(tLo, reference).azimuthDeg
            out[mode] = Band(anchor, min(a1, a2), max(a1, a2))
        }
        out
    }

    /** Evaluate one sightline, both directions, on one date. */
    internal fun evaluate(sl: Sightline, date: LocalDate, modes: Set<Mode>): List<HengeEvent> {
        val bands = bands(date)
        val out = ArrayList<HengeEvent>(2)
        for (mode in modes) {
            val band = bands[mode] ?: continue
            if (sl.openHorizon) {
                openHorizonEvent(sl, mode, date, band.anchor)?.let(out::add)
                continue
            }
            val margin = SkylineProfile.HALF_RANGE_DEG + config.bandMarginDeg
            for (d in directions[sl.id].orEmpty()) {
                if (d.bearing < band.minAz - margin || d.bearing > band.maxAz + margin) continue
                scanDirection(d, mode, date, band.anchor)?.let(out::add)
            }
        }
        return out
    }

    /** One moment of the scan; score 0 when the moment does not count. */
    private class Sample(val seconds: Double, val az: Double, val alt: Double, val offset: Double, val height: Double,
                         val disc: Double, val centring: Double, val score: Double)

    private fun sample(d: Direction, path: SunPath, seconds: Double): Sample {
        val az = path.azimuthAt(seconds)
        val alt = path.altitudeAt(seconds)
        val offset = Geo.signedDiffDeg(az, d.bearing)
        val none = Sample(seconds, az, alt, offset, 0.0, 0.0, 1.0, 0.0)
        if (abs(offset) > SkylineProfile.HALF_RANGE_DEG) return none
        val sky = d.profile.hardAt(offset)
        if (alt <= sky) return none
        val opening = d.profile.opening(alt, config.openSideGateDeg) ?: return none
        if (offset < opening.leftDeg || offset > opening.rightDeg) return none
        val height = alt - sky
        if (height > config.maxHeightAboveFloorDeg) return none
        val r = config.sunRadiusDeg
        val centring = if (opening.halfDeg > 0) (abs(offset - opening.midDeg) / opening.halfDeg).coerceIn(0.0, 1.0) else 1.0
        val disc = ((alt + r - sky) / (2 * r)).coerceIn(0.0, 1.0)
        val lowness = if (height <= 2 * r) 1.0
        else (1.0 - (height - 2 * r) / (config.maxHeightAboveFloorDeg - 2 * r)).coerceIn(0.0, 1.0)
        return Sample(seconds, az, alt, offset, height, disc, centring, (1.0 - centring) * lowness * disc)
    }

    private fun scanDirection(d: Direction, mode: Mode, date: LocalDate, anchor: Instant): HengeEvent? {
        val (from, to) = bracket(mode, anchor)
        val lower = max(config.minScanAltitudeDeg, d.profile.minDeg)
        val upper = min(config.maxScanAltitudeDeg, d.profile.maxDeg + config.maxHeightAboveFloorDeg)
        if (upper <= lower) return null
        val tol = config.solverToleranceSeconds
        val tUpper = AltitudeSolver.solve(d.standing, upper, from, to, tol)?.time ?: (if (mode == Mode.SUNSET) from else to)
        val tLower = AltitudeSolver.solve(d.standing, lower, from, to, tol)?.time ?: (if (mode == Mode.SUNSET) to else from)
        val (start, end) = if (mode == Mode.SUNSET) tUpper to tLower else tLower to tUpper
        if (!end.isAfter(start)) return null
        val path = SunPath(d.standing, start, end, config.pathKnotSeconds)

        // coarse pass, then refine around the best coarse moment
        val coarse = config.coarseStepSeconds.toDouble()
        var best: Sample? = null
        var s = 0.0
        while (s <= path.spanSeconds) {
            val x = sample(d, path, s)
            if (x.score > (best?.score ?: 0.0)) best = x
            s += coarse
        }
        val coarseBest = best ?: return null
        var b: Sample = coarseBest
        var fine = coarseBest.seconds - coarse
        while (fine <= coarseBest.seconds + coarse) {
            if (fine in 0.0..path.spanSeconds) {
                val x = sample(d, path, fine)
                if (x.score > b.score) b = x
            }
            fine += config.fineStepSeconds
        }

        // window: contiguous span around tBest scoring at least windowScoreFraction of it
        val threshold = config.windowScoreFraction * b.score
        val inside = { t: Double -> sample(d, path, t).score >= threshold }
        val windowStart = edge(b.seconds, -coarse, 0.0, path.spanSeconds, inside)
        val windowEnd = edge(b.seconds, coarse, 0.0, path.spanSeconds, inside)

        val treesInWay = d.profile.softAt(b.offset)?.let { it >= b.alt } ?: false
        val leafOn = date.monthValue in config.leafOnMonths
        val quality = b.score * (if (treesInWay && leafOn) config.treesQualityFactor else 1.0)
        val style = if (b.height <= 2 * config.sunRadiusDeg) Style.ON_HORIZON else Style.IN_CANYON
        return HengeEvent(
            sightlineId = d.sl.id, viewFrom = d.viewFrom, bearing = d.bearing, mode = mode, date = date,
            tBest = path.instantAt(b.seconds), windowStart = path.instantAt(windowStart), windowEnd = path.instantAt(windowEnd),
            style = style, sunAzimuthDeg = b.az, sunAltitudeDeg = b.alt, offsetDeg = b.offset,
            heightAboveFloorDeg = b.height, discFraction = b.disc, centring = b.centring,
            stepRightM = stepRightM(d.sl.lengthM, b.offset, config.assumedStreetWidthM),
            treesInWay = treesInWay, confidence = d.profile.confidence,
            quality = quality, interest = d.interest, score = score(quality, d.interest, config),
            grade = grade(b, style), poiIds = d.poiIds,
        )
    }

    /** Walk from `t0` (inside) by `step` until outside or the bound, then bisect to the tolerance. */
    private fun edge(t0: Double, step: Double, lo: Double, hi: Double, inside: (Double) -> Boolean): Double {
        var inT = t0
        while (true) {
            val next = (inT + step).coerceIn(lo, hi)
            if (next == inT) return inT
            if (!inside(next)) {
                var a = inT
                var b = next
                while (abs(b - a) > config.solverToleranceSeconds) {
                    val m = (a + b) / 2
                    if (inside(m)) a = m else b = m
                }
                return a
            }
            inT = next
        }
    }

    private fun grade(b: Sample, style: Style): Grade = when {
        style == Style.ON_HORIZON && b.disc >= config.perfectMinDisc && b.centring <= config.perfectMaxCentring -> Grade.PERFECT
        b.disc >= config.goodMinDisc && b.centring <= config.goodMaxCentring && b.height <= config.goodMaxHeightDeg -> Grade.GOOD
        else -> Grade.NEAR
    }

    /** Open-horizon spots: the full disc resting on the (flat) horizon, any azimuth. */
    private fun openHorizonEvent(sl: Sightline, mode: Mode, date: LocalDate, anchor: Instant): HengeEvent? {
        val (from, to) = bracket(mode, anchor)
        val floor = max(0.0, sl.obstructionTowardBDeg + config.obstructionOffsetDeg)
        val r = config.sunRadiusDeg
        val tol = config.solverToleranceSeconds
        // the flat-profile score with centring 0 is lowness x disc; solve where it equals the window threshold
        val gHigh = 2 * r + (1 - config.windowScoreFraction) * (config.maxHeightAboveFloorDeg - 2 * r)
        val gLow = 2 * r * config.windowScoreFraction - r
        val tBest = AltitudeSolver.solve(sl.a, floor + r, from, to, tol)?.time ?: return null
        val tHigh = AltitudeSolver.solve(sl.a, floor + gHigh, from, to, tol)?.time ?: return null
        val tLow = AltitudeSolver.solve(sl.a, floor + gLow, from, to, tol)?.time ?: return null
        val (start, end) = if (tHigh < tLow) tHigh to tLow else tLow to tHigh
        val pos = SunPosition.at(tBest, sl.a)
        return HengeEvent(
            sightlineId = sl.id, viewFrom = Endpoint.A, bearing = pos.azimuthDeg, mode = mode, date = date,
            tBest = tBest, windowStart = start, windowEnd = end, style = Style.ON_HORIZON,
            sunAzimuthDeg = pos.azimuthDeg, sunAltitudeDeg = pos.apparentAltitudeDeg, offsetDeg = 0.0,
            heightAboveFloorDeg = pos.apparentAltitudeDeg - floor, discFraction = 1.0, centring = 0.0, stepRightM = null,
            treesInWay = false, confidence = Confidence.ESTIMATED, quality = 1.0, interest = 0.0, score = 1.0,
            grade = Grade.PERFECT, poiIds = data.poiIdsFor(sl.id, Endpoint.A), openHorizon = true,
        )
    }

    companion object {
        val ALL_MODES: Set<Mode> = Mode.entries.toSet()

        /** Ranking value of an event (IMPROVEMENT-PLAN.md 3.4). */
        fun score(quality: Double, interest: Double, config: EngineConfig): Double =
            quality * (config.interestFloor + (1.0 - config.interestFloor) * interest)

        /**
         * Metres to step right (negative: left) of a sightline's centre line so a sun seen
         * `offsetDeg` off the axis sits over the middle of the far end `lengthM` away; null when
         * that is more than half the street width.
         */
        fun stepRightM(lengthM: Double, offsetDeg: Double, streetWidthM: Double): Double? {
            val step = -lengthM * tan(Math.toRadians(offsetDeg))
            return if (abs(step) <= streetWidthM / 2) step else null
        }
    }
}
