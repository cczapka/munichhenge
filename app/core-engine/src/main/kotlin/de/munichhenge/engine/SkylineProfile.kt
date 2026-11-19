package de.munichhenge.engine

import java.util.Base64
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** How a skyline profile was obtained. */
enum class Confidence {
    /** Measured from building, terrain and tree data over the whole view (M8). */
    MEASURED,
    /** Measured, but the view leaves the data area or the eye height is assumed (bridges). */
    PARTIAL,
    /** Synthesised from the sightline's length, obstruction angle and canyon flag. */
    ESTIMATED,
}

/** The patch of sky around the axis at one altitude, degrees of azimuth offset (negative = left). */
data class Opening(val leftDeg: Double, val rightDeg: Double, val leftOpen: Boolean, val rightOpen: Boolean) {
    val midDeg: Double get() = (leftDeg + rightDeg) / 2
    val halfDeg: Double get() = (rightDeg - leftDeg) / 2
}

/**
 * Skyline profile of one viewing direction (IMPROVEMENT-PLAN.md 3.1): the apparent altitude of
 * the highest obstruction as a function of the azimuth offset from the bearing, sampled every
 * 0.1° from -4° to +4° (81 values, positive = clockwise = to the right).
 *
 * `hard` holds buildings and terrain, `soft` trees (null when no tree data exists).
 */
class SkylineProfile(hard: DoubleArray, soft: DoubleArray?, val confidence: Confidence) {
    private val hard: DoubleArray = hard.copyOf()
    private val soft: DoubleArray? = soft?.copyOf()

    init {
        require(hard.size == SIZE) { "profile needs $SIZE samples, got ${hard.size}" }
        require(soft == null || soft.size == SIZE) { "soft layer needs $SIZE samples" }
    }

    /** Skyline straight down the axis. */
    val floorDeg: Double get() = hard[CENTER]
    val minDeg: Double = this.hard.min()
    val maxDeg: Double = this.hard.max()

    /** Whether something rises above the floor on each side; a side without is an open road. */
    private val wallLeft: Boolean = (0 until CENTER).any { this.hard[it] > this.hard[CENTER] + WALL_MIN_DEG }
    private val wallRight: Boolean = (CENTER + 1 until SIZE).any { this.hard[it] > this.hard[CENTER] + WALL_MIN_DEG }

    fun hardAt(offsetDeg: Double): Double = interpolate(hard, offsetDeg)

    fun softAt(offsetDeg: Double): Double? = soft?.let { interpolate(it, offsetDeg) }

    /**
     * Sky reaching from the axis left and right at `altDeg`; null when the axis itself is blocked.
     * A side that never rises above the floor is an open road, reported at `openSideGateDeg`.
     * A side whose wall rises beyond the ±4° edge is reported at the edge: the opening is at
     * least that wide, and the edge keeps it continuous as the sun passes the wall's top there.
     */
    fun opening(altDeg: Double, openSideGateDeg: Double): Opening? {
        if (hard[CENTER] >= altDeg) return null
        var i = CENTER
        while (i < SIZE - 1 && hard[i + 1] < altDeg) i++
        val right = when {
            i < SIZE - 1 -> offsetOf(i) + STEP_DEG * (altDeg - hard[i]) / (hard[i + 1] - hard[i])
            wallRight -> HALF_RANGE_DEG
            else -> openSideGateDeg
        }
        var j = CENTER
        while (j > 0 && hard[j - 1] < altDeg) j--
        val left = when {
            j > 0 -> offsetOf(j) - STEP_DEG * (altDeg - hard[j]) / (hard[j - 1] - hard[j])
            wallLeft -> -HALF_RANGE_DEG
            else -> -openSideGateDeg
        }
        return Opening(left, right, leftOpen = j == 0 && !wallLeft, rightOpen = i == SIZE - 1 && !wallRight)
    }

    /** Hard layer raised (or lowered) by `offsetDeg`, never below the horizon. */
    fun shifted(offsetDeg: Double): SkylineProfile =
        if (offsetDeg == 0.0) this else SkylineProfile(DoubleArray(SIZE) { max(0.0, hard[it] + offsetDeg) }, soft, confidence)

    fun withSoft(soft: DoubleArray?): SkylineProfile = SkylineProfile(hard, soft, confidence)

    /** Base64 of one byte per sample in units of 0.05°, the schema-v2 encoding (M8). */
    fun encodeHard(): String = encode(hard)

    companion object {
        const val HALF_RANGE_DEG = 4.0
        const val STEP_DEG = 0.1
        const val SIZE = 81
        const val CENTER = 40

        /** Encoding resolution: one byte per sample, so 0 to 12.75°. */
        const val UNIT_DEG = 0.05

        /** Rise above the floor that counts as a wall when deciding whether a side is open road;
         * one encoding step, so measured noise on a flat road does not count. */
        const val WALL_MIN_DEG = 0.05

        fun offsetOf(index: Int): Double = (index - CENTER) * STEP_DEG

        /** Half the angular width of a street's far end, seen from the other end. */
        fun halfWidthDeg(lengthM: Double, streetWidthM: Double): Double =
            Math.toDegrees(atan((streetWidthM / 2) / lengthM))

        fun flat(floorDeg: Double, confidence: Confidence = Confidence.ESTIMATED): SkylineProfile =
            SkylineProfile(DoubleArray(SIZE) { max(0.0, floorDeg) }, null, confidence)

        /**
         * Profile synthesised from schema-v1 fields (3.1): flat at the floor for open sides; for
         * canyon sightlines flat across the far end (±halfWidth) and rising linearly outside it,
         * which is a flanking wall's roofline seen in perspective.
         */
        fun synthetic(floorDeg: Double, lengthM: Double, canyon: Boolean, streetWidthM: Double): SkylineProfile {
            val f = max(0.0, floorDeg)
            if (!canyon || f <= 0.0 || lengthM <= 0.0) return flat(f)
            val e0 = halfWidthDeg(lengthM, streetWidthM)
            return SkylineProfile(DoubleArray(SIZE) { i ->
                val d = abs(offsetOf(i))
                if (d <= e0) f else f * d / e0
            }, null, Confidence.ESTIMATED)
        }

        fun decode(hardBase64: String, softBase64: String?, confidence: Confidence): SkylineProfile =
            SkylineProfile(decodeLayer(hardBase64), softBase64?.let(::decodeLayer), confidence)

        private fun decodeLayer(b64: String): DoubleArray {
            val bytes = Base64.getDecoder().decode(b64)
            require(bytes.size == SIZE) { "profile needs $SIZE bytes, got ${bytes.size}" }
            return DoubleArray(SIZE) { (bytes[it].toInt() and 0xff) * UNIT_DEG }
        }

        private fun encode(a: DoubleArray): String =
            Base64.getEncoder().encodeToString(ByteArray(SIZE) { (a[it] / UNIT_DEG).roundToInt().coerceIn(0, 255).toByte() })

        private fun interpolate(a: DoubleArray, offsetDeg: Double): Double {
            val x = (offsetDeg + HALF_RANGE_DEG) / STEP_DEG
            if (x <= 0.0) return a[0]
            if (x >= SIZE - 1) return a[SIZE - 1]
            val i = floor(x).toInt()
            val f = x - i
            return a[i] + (a[i + 1] - a[i]) * f
        }
    }
}
