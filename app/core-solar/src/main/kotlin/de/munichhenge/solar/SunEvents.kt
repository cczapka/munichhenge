package de.munichhenge.solar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sunrise, sunset and transit (solar noon) for a *local* calendar date. The zone defines
 * which day is meant; results are UTC instants (CLAUDE.md).
 */
object SunEvents {
    private val HALF_DAY: Duration = Duration.ofHours(12)

    /** Solar noon: the instant the hour angle is zero, found by two Newton-like corrections. */
    fun transit(date: LocalDate, at: GeoPoint, zone: ZoneId): Instant {
        var t = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        repeat(3) {
            val ha = SunPosition.at(t, at).hourAngleDeg
            t = t.minusMillis((ha / 15.0 * 3_600_000.0).toLong())   // sun moves 15°/h
        }
        return t
    }

    /** Upper limb touches the horizon; null in polar night/day. */
    fun sunrise(date: LocalDate, at: GeoPoint, zone: ZoneId): Instant? {
        val noon = transit(date, at, zone)
        return AltitudeSolver.solve(at, SolarConstants.RISE_SET_ALTITUDE_DEG, noon.minus(HALF_DAY), noon)?.time
    }

    /** Upper limb touches the horizon; null in polar night/day. */
    fun sunset(date: LocalDate, at: GeoPoint, zone: ZoneId): Instant? {
        val noon = transit(date, at, zone)
        return AltitudeSolver.solve(at, SolarConstants.RISE_SET_ALTITUDE_DEG, noon, noon.plus(HALF_DAY))?.time
    }
}
