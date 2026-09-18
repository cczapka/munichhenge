package de.munichhenge.solar

import java.time.Instant
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun position by the NOAA Solar Calculator algorithm (a low-precision Meeus
 * implementation): geometric mean longitude/anomaly, equation of centre, apparent
 * longitude, obliquity with nutation term, declination, equation of time, hour angle.
 * Accuracy ~0.01° in position; far finer than any obstruction estimate.
 */
object SunPosition {
    data class Position(
        /** Altitude above the mathematical horizon, no refraction, degrees. */
        val geometricAltitudeDeg: Double,
        /** Refraction-corrected altitude, degrees (CLAUDE.md: altitudes are apparent unless named geometric). */
        val apparentAltitudeDeg: Double,
        /** Degrees clockwise from true north, [0, 360). */
        val azimuthDeg: Double,
        val declinationDeg: Double,
        /** Apparent minus mean solar time, minutes. */
        val equationOfTimeMin: Double,
        /** Local hour angle, degrees, negative before transit, in (-180, 180]. */
        val hourAngleDeg: Double,
    )

    private const val J2000 = 2451545.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_JD = 2440587.5

    fun julianDay(t: Instant): Double = UNIX_EPOCH_JD + t.toEpochMilli() / MILLIS_PER_DAY

    private fun norm360(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

    fun at(t: Instant, at: GeoPoint): Position {
        val jd = julianDay(t)
        val jc = (jd - J2000) / 36525.0

        val l0 = norm360(280.46646 + jc * (36000.76983 + jc * 0.0003032))
        val m = norm360(357.52911 + jc * (35999.05029 - 0.0001537 * jc))
        val e = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val mRad = Math.toRadians(m)
        val c = sin(mRad) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * jc) +
            sin(3 * mRad) * 0.000289
        val trueLong = l0 + c
        val omega = Math.toRadians(125.04 - 1934.136 * jc)
        val lambda = Math.toRadians(trueLong - 0.00569 - 0.00478 * sin(omega))

        val eps0 = 23.0 + (26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0
        val eps = Math.toRadians(eps0 + 0.00256 * cos(omega))

        val decl = asin(sin(eps) * sin(lambda))

        val y = tan(eps / 2.0).let { it * it }
        val l0Rad = Math.toRadians(l0)
        val eqTimeMin = 4.0 * Math.toDegrees(
            y * sin(2 * l0Rad) - 2 * e * sin(mRad) + 4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * mRad)
        )

        val minutesOfDayUtc = ((jd + 0.5) % 1.0) * 1440.0
        val trueSolarTimeMin = ((minutesOfDayUtc + eqTimeMin + 4.0 * at.lon) % 1440.0 + 1440.0) % 1440.0
        var haDeg = trueSolarTimeMin / 4.0 - 180.0
        if (haDeg <= -180.0) haDeg += 360.0
        val ha = Math.toRadians(haDeg)

        val lat = Math.toRadians(at.lat)
        val cosZenith = (sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(ha)).coerceIn(-1.0, 1.0)
        val geometricAlt = 90.0 - Math.toDegrees(acos(cosZenith))

        // azimuth from south, westward positive; +180 -> clockwise from north
        val azFromSouth = atan2(sin(ha), cos(ha) * sin(lat) - tan(decl) * cos(lat))
        val azimuth = norm360(Math.toDegrees(azFromSouth) + 180.0)

        return Position(
            geometricAltitudeDeg = geometricAlt,
            apparentAltitudeDeg = Refraction.apparent(geometricAlt),
            azimuthDeg = azimuth,
            declinationDeg = Math.toDegrees(decl),
            equationOfTimeMin = eqTimeMin,
            hourAngleDeg = haDeg,
        )
    }
}
