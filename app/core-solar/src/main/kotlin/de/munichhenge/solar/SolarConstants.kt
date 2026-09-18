package de.munichhenge.solar

/** Shared numeric constants (CLAUDE.md conventions). */
object SolarConstants {
    /** Apparent radius of the solar disk in degrees. */
    const val SUN_RADIUS_DEG: Double = 0.2665

    /** Apparent altitude of the sun's centre at sunrise/sunset: upper limb on the horizon. */
    const val RISE_SET_ALTITUDE_DEG: Double = -SUN_RADIUS_DEG
}

/** WGS84 position, degrees. `lat` first, then `lon` (CLAUDE.md). */
data class GeoPoint(val lat: Double, val lon: Double)
