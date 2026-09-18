package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Simple spherical-earth geodesy: fine at city scale (CLAUDE.md). Angles in degrees. */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    fun normalizeDeg(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Smallest absolute difference between two bearings, in [0, 180]. */
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = normalizeDeg(a - b)
        return if (d > 180.0) 360.0 - d else d
    }

    /** Point at `distanceM` from `from` along `bearingDeg`. */
    fun destination(from: GeoPoint, bearingDeg: Double, distanceM: Double): GeoPoint {
        val lat1 = Math.toRadians(from.lat)
        val lon1 = Math.toRadians(from.lon)
        val brg = Math.toRadians(bearingDeg)
        val d = distanceM / EARTH_RADIUS_M
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lon2 = lon1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /** Initial bearing from a to b, degrees clockwise from north. */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeDeg(Math.toDegrees(atan2(y, x)))
    }

    /** Great-circle distance in metres. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(kotlin.math.sqrt(h))
    }
}
