package de.munichhenge.engine

import kotlin.math.max
import kotlin.math.min

/**
 * How worthwhile a place is, independent of the date (IMPROVEMENT-PLAN.md 3.4). Combined with
 * an event's alignment quality into its score.
 */
object Interest {
    fun of(lengthM: Double, featured: Boolean, named: Boolean, framed: Double, spots: Int, config: EngineConfig): Double {
        val length = ((lengthM - config.lengthMinM) / (config.lengthMaxM - config.lengthMinM)).coerceIn(0.0, 1.0)
        val spotsNorm = (spots.toDouble() / config.spotsMax).coerceIn(0.0, 1.0)
        val sum = config.wFeatured * (if (featured) 1.0 else 0.0) + config.wLength * length +
            config.wFramed * framed.coerceIn(0.0, 1.0) + config.wSpots * spotsNorm + config.wNamed * (if (named) 1.0 else 0.0)
        val total = config.wFeatured + config.wLength + config.wFramed + config.wSpots + config.wNamed
        return if (total > 0.0) (sum / total).coerceIn(0.0, 1.0) else 0.0
    }

    /** How canyon-like the opening is: the lower of the two walls at ±framedProbeDeg above the floor,
     * relative to the floor, 0 (open road) to 1 (walls towering over a narrow far end). */
    fun framed(profile: SkylineProfile, config: EngineConfig): Double {
        val f = profile.floorDeg
        val walls = min(profile.hardAt(-config.framedProbeDeg), profile.hardAt(config.framedProbeDeg))
        return ((walls - f) / max(f, config.framedMinFloorDeg)).coerceIn(0.0, 1.0)
    }
}
