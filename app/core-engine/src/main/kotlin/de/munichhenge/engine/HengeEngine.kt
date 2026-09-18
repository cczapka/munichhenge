package de.munichhenge.engine

import de.munichhenge.solar.AltitudeSolver
import de.munichhenge.solar.GeoPoint
import de.munichhenge.solar.Refraction
import de.munichhenge.solar.SunEvents
import de.munichhenge.solar.SunPosition
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes henge events (PLAN.md 2 and 4.1). Pure and thread-safe; results per date are
 * cached in memory.
 */
class HengeEngine(val data: HengeData, val config: EngineConfig = EngineConfig()) {
    private val cache = ConcurrentHashMap<LocalDate, List<HengeEvent>>()

    /** The day's sunrise/sunset (UTC) and declination, computed once per date. */
    private class Day(val date: LocalDate, val sunrise: Instant?, val sunset: Instant?, val declinationDeg: Double)

    private val dayCache = ConcurrentHashMap<LocalDate, Day>()

    private fun day(date: LocalDate): Day = dayCache.getOrPut(date) {
        val ref = data.sightlines.firstOrNull()?.a ?: GeoPoint(0.0, 0.0)
        val set = SunEvents.sunset(date, ref, config.zone)
        val rise = SunEvents.sunrise(date, ref, config.zone)
        val decl = SunPosition.at(set ?: rise ?: SunEvents.transit(date, ref, config.zone), ref).declinationDeg
        Day(date, rise, set, decl)
    }

    /** All events of a local date, sorted by quality then time. */
    fun eventsFor(date: LocalDate, modes: Set<Mode> = ALL_MODES): List<HengeEvent> {
        val all = cache.getOrPut(date) {
            data.sightlines.flatMap { sl -> evaluate(sl, date, ALL_MODES) }
                .sortedWith(compareByDescending<HengeEvent> { it.quality }.thenBy { it.tFull }.thenBy { it.sightlineId })
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

    /** Best real (non open-horizon) events in [from, from + days), quality >= minQuality, best first. */
    fun bestUpcoming(from: LocalDate, days: Int, minQuality: Double, modes: Set<Mode> = ALL_MODES): List<HengeEvent> =
        (0 until days).map { from.plusDays(it.toLong()) }
            .flatMap { eventsFor(it, modes) }
            .filter { !it.openHorizon && it.quality >= minQuality }
            .sortedWith(compareByDescending<HengeEvent> { it.quality }.thenBy { it.tFull })

    private fun scan(from: LocalDate, count: Int, perDay: (LocalDate) -> List<HengeEvent>): List<HengeEvent> {
        if (count <= 0) return emptyList()
        val out = ArrayList<HengeEvent>()
        var date = from
        var scanned = 0
        while (out.size < count && scanned < config.scanLimitDays) {
            out += perDay(date).sortedBy { it.tFull }
            date = date.plusDays(1)
            scanned++
        }
        return if (out.size > count) out.subList(0, count) else out
    }

    /** Evaluate one sightline, both directions, on one date. */
    internal fun evaluate(sl: Sightline, date: LocalDate, modes: Set<Mode>): List<HengeEvent> {
        val day = day(date)
        val out = ArrayList<HengeEvent>(2)
        for (mode in modes) {
            val anchor = (if (mode == Mode.SUNSET) day.sunset else day.sunrise) ?: continue
            if (sl.openHorizon) {
                openHorizonEvent(sl, mode, date, anchor)?.let(out::add)
                continue
            }
            for (viewFrom in Endpoint.entries) {
                val bearing = sl.bearing(viewFrom) ?: continue
                val obstruction = (sl.obstruction(viewFrom) + config.obstructionOffsetDeg).coerceAtLeast(0.0)
                val standing = sl.standingPoint(viewFrom)
                // cheap closed-form prefilter: azimuth at the target altitude for today's declination
                val approxAz = azimuthAtAltitude(standing.lat, day.declinationDeg, obstruction + config.sunRadiusDeg, mode)
                    ?: continue
                if (Geo.angleDiffDeg(approxAz, bearing) > config.nearDeg + config.prefilterMarginDeg) continue
                event(sl, viewFrom, bearing, obstruction, standing, mode, date, anchor)?.let(out::add)
            }
        }
        return out
    }

    private fun bracket(mode: Mode, anchor: Instant): Pair<Instant, Instant> = if (mode == Mode.SUNSET)
        anchor.minus(config.sunsetBracketBefore) to anchor.plus(config.sunsetBracketAfter)
    else
        anchor.minus(config.sunriseBracketBefore) to anchor.plus(config.sunriseBracketAfter)

    private fun event(
        sl: Sightline, viewFrom: Endpoint, bearing: Double, obstruction: Double, standing: GeoPoint,
        mode: Mode, date: LocalDate, anchor: Instant,
    ): HengeEvent? {
        val (from, to) = bracket(mode, anchor)
        val full = AltitudeSolver.solve(standing, obstruction + config.sunRadiusDeg, from, to, config.solverToleranceSeconds) ?: return null
        val half = AltitudeSolver.solve(standing, obstruction, from, to, config.solverToleranceSeconds) ?: return null
        val az = SunPosition.at(full.time, standing).azimuthDeg
        val err = Geo.angleDiffDeg(az, bearing)
        val grade = grade(err) ?: return null
        return HengeEvent(
            sightlineId = sl.id, viewFrom = viewFrom, bearing = bearing, mode = mode, date = date,
            tFull = full.time, tHalf = half.time, azimuthAtFull = az, alignmentErrorDeg = err,
            quality = quality(err, sl.canyon), grade = grade, poiIds = data.poiIdsFor(sl.id, viewFrom),
        )
    }

    private fun openHorizonEvent(sl: Sightline, mode: Mode, date: LocalDate, anchor: Instant): HengeEvent? {
        val (from, to) = bracket(mode, anchor)
        val obstruction = (sl.obstructionTowardBDeg + config.obstructionOffsetDeg).coerceAtLeast(0.0)
        val full = AltitudeSolver.solve(sl.a, obstruction + config.sunRadiusDeg, from, to, config.solverToleranceSeconds) ?: return null
        val half = AltitudeSolver.solve(sl.a, obstruction, from, to, config.solverToleranceSeconds) ?: return null
        val az = SunPosition.at(full.time, sl.a).azimuthDeg
        return HengeEvent(
            sightlineId = sl.id, viewFrom = Endpoint.A, bearing = az, mode = mode, date = date,
            tFull = full.time, tHalf = half.time, azimuthAtFull = az, alignmentErrorDeg = 0.0,
            quality = 1.0, grade = Grade.PERFECT, poiIds = data.poiIdsFor(sl.id, Endpoint.A), openHorizon = true,
        )
    }

    private fun grade(errDeg: Double): Grade? = when {
        errDeg <= config.perfectDeg -> Grade.PERFECT
        errDeg <= config.goodDeg -> Grade.GOOD
        errDeg <= config.nearDeg -> Grade.NEAR
        else -> null
    }

    private fun quality(errDeg: Double, canyon: Boolean): Double {
        val q = (1.0 - errDeg / config.nearDeg).coerceAtLeast(0.0)
        return if (canyon) (q + config.canyonBonus).coerceAtMost(1.0) else q
    }

    companion object {
        val ALL_MODES: Set<Mode> = Mode.entries.toSet()

        /**
         * Azimuth (degrees from north) at which the sun has apparent altitude `apparentAltDeg`
         * for the given latitude and declination; morning side for SUNRISE, evening for SUNSET.
         * Null when that altitude is never reached. Closed form; used only as a prefilter.
         */
        internal fun azimuthAtAltitude(latDeg: Double, declDeg: Double, apparentAltDeg: Double, mode: Mode): Double? {
            val geometric = apparentAltDeg - Refraction.forGeometricAltitude(apparentAltDeg)
            val lat = Math.toRadians(latDeg)
            val decl = Math.toRadians(declDeg)
            val h = Math.toRadians(geometric)
            val cosAz = (sin(decl) - sin(lat) * sin(h)) / (cos(lat) * cos(h))
            if (cosAz < -1.0 || cosAz > 1.0) return null
            val az = Math.toDegrees(acos(cosAz))
            return if (mode == Mode.SUNRISE) az else 360.0 - az
        }
    }
}
