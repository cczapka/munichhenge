package de.munichhenge.solar

import kotlin.math.tan

/**
 * Atmospheric refraction after Sæmundsson (1986), as given in Meeus, Astronomical
 * Algorithms ch. 16: `R = 1.02 / tan(h + 10.3 / (h + 5.11))` arcminutes, where `h` is the
 * *geometric* altitude in degrees. Valid down to the horizon; at the apparent horizon it
 * gives the classic 34' (0.57°), at h = 0 about 29'.
 *
 * Below [FLOOR_DEG] (where the formula's derivative is zero) R is held constant so that
 * `apparent(h)` stays strictly increasing, which the bisection solver relies on.
 */
object Refraction {
    /** Geometric altitude at which the Sæmundsson expression peaks: h + 5.11 = sqrt(10.3). */
    private const val FLOOR_DEG = -1.90

    /** Refraction in degrees for a geometric altitude in degrees. Standard atmosphere. */
    fun forGeometricAltitude(geometricAltitudeDeg: Double): Double {
        val h = if (geometricAltitudeDeg < FLOOR_DEG) FLOOR_DEG else geometricAltitudeDeg
        val argDeg = h + 10.3 / (h + 5.11)
        val arcmin = 1.02 / tan(Math.toRadians(argDeg))
        return if (arcmin > 0.0) arcmin / 60.0 else 0.0
    }

    /** Apparent (refraction-corrected) altitude for a geometric altitude, degrees. */
    fun apparent(geometricAltitudeDeg: Double): Double =
        geometricAltitudeDeg + forGeometricAltitude(geometricAltitudeDeg)
}
