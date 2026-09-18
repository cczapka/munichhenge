package de.munichhenge.solar

import java.time.Instant

/**
 * Finds the instant at which the sun's apparent altitude equals a target, by bisection over
 * a bracket in which the altitude is monotonic (e.g. [sunset - 3 h, sunset + 1 h]).
 */
object AltitudeSolver {
    data class Result(val time: Instant, val iterations: Int)

    /**
     * @return the crossing, or null when the target is not crossed between [from] and [to]
     * (no sign change). With a 4 h bracket and 1 s tolerance this takes ~14 iterations.
     */
    fun solve(
        at: GeoPoint,
        targetApparentAltitudeDeg: Double,
        from: Instant,
        to: Instant,
        toleranceSeconds: Double = 1.0,
    ): Result? {
        require(to > from) { "empty bracket" }
        fun f(ms: Long): Double =
            SunPosition.at(Instant.ofEpochMilli(ms), at).apparentAltitudeDeg - targetApparentAltitudeDeg

        var lo = from.toEpochMilli()
        var hi = to.toEpochMilli()
        var fLo = f(lo)
        val fHi = f(hi)
        if (fLo == 0.0) return Result(from, 0)
        if (fHi == 0.0) return Result(to, 0)
        if ((fLo < 0) == (fHi < 0)) return null

        val tolMs = (toleranceSeconds * 1000.0).toLong().coerceAtLeast(1L)
        var iterations = 0
        while (hi - lo > tolMs && iterations < 64) {
            val mid = lo + (hi - lo) / 2
            val fMid = f(mid)
            iterations++
            if (fMid == 0.0) return Result(Instant.ofEpochMilli(mid), iterations)
            if ((fMid < 0) == (fLo < 0)) {
                lo = mid
                fLo = fMid
            } else {
                hi = mid
            }
        }
        return Result(Instant.ofEpochMilli(lo + (hi - lo) / 2), iterations)
    }
}
