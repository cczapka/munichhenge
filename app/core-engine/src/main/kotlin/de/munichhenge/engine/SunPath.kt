package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint
import de.munichhenge.solar.SunPosition
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

/**
 * Sun positions at one place over a time span: exact at knots every `knotSeconds`, linear in
 * between. The low sun's track is smooth enough that a 3-minute knot spacing stays within
 * 0.005° (tested), and it saves ~95 % of the solar computations of a 60 s / 5 s scan.
 */
internal class SunPath(at: GeoPoint, val start: Instant, end: Instant, knotSeconds: Long) {
    val spanSeconds: Double = (end.toEpochMilli() - start.toEpochMilli()) / 1000.0
    private val n: Int = max(2, ceil(spanSeconds / knotSeconds).toInt() + 1)
    private val step: Double = spanSeconds / (n - 1)
    private val az = DoubleArray(n)
    private val alt = DoubleArray(n)

    init {
        for (k in 0 until n) {
            val p = SunPosition.at(instantAt(k * step), at)
            alt[k] = p.apparentAltitudeDeg
            // unwrap so interpolation never crosses 360 -> 0
            az[k] = if (k == 0) p.azimuthDeg else az[k - 1] + Geo.signedDiffDeg(p.azimuthDeg, az[k - 1])
        }
    }

    fun instantAt(seconds: Double): Instant = start.plusMillis((seconds * 1000.0).toLong())

    fun azimuthAt(seconds: Double): Double = Geo.normalizeDeg(interp(az, seconds))

    fun altitudeAt(seconds: Double): Double = interp(alt, seconds)

    private fun interp(a: DoubleArray, seconds: Double): Double {
        val x = (seconds / step).coerceIn(0.0, (n - 1).toDouble())
        val i = x.toInt().coerceAtMost(n - 2)
        val f = x - i
        return a[i] + (a[i + 1] - a[i]) * f
    }
}
