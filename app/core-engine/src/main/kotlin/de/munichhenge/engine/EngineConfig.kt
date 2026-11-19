package de.munichhenge.engine

import java.time.Duration
import java.time.ZoneId

/**
 * All engine tunables in one place (CLAUDE.md rule 3), with what each trades off. The app
 * exposes some of them in Settings so they can be tuned without rebuilding.
 * Definitions: IMPROVEMENT-PLAN.md section 3.
 */
data class EngineConfig(
    /** Calendar dates of events are in this zone; times stay UTC instants. */
    val zone: ZoneId = ZoneId.of("Europe/Berlin"),

    // ---- visibility -------------------------------------------------------------------
    /** Width of a street's far end when no measured skyline exists. Wider = more events on
     * canyon streets, including ones where the sun would really sit behind a wall. */
    val assumedStreetWidthM: Double = 20.0,
    /** How far left or right of the axis the sun may be on a side with no wall within ±4°
     * (open roads, canals). Wider = more events that look less like a henge. */
    val openSideGateDeg: Double = 1.5,
    /** Highest the sun may stand above the skyline at its offset and still count. Higher =
     * more "between the buildings" events that are no longer sunset pictures. */
    val maxHeightAboveFloorDeg: Double = 4.0,
    /** The window is the span around the best moment scoring at least this share of it.
     * Higher = shorter, more precise windows. */
    val windowScoreFraction: Double = 0.7,
    /** Added to every obstruction angle and profile (may be negative). The estimates are
     * coarse; this shifts all events at once. */
    val obstructionOffsetDeg: Double = 0.0,
    /** Sun disc radius. */
    val sunRadiusDeg: Double = 0.2665,

    // ---- scanning -----------------------------------------------------------------------
    /** Altitude band in which the sun is followed. Below the lower bound it is set; above the
     * upper no skyline plus maxHeightAboveFloorDeg reaches. */
    val minScanAltitudeDeg: Double = -0.3,
    val maxScanAltitudeDeg: Double = 12.0,
    /** First pass step. Smaller = slower, catches openings narrower than the sun moves in a step
     * (~0.15° of azimuth per minute). */
    val coarseStepSeconds: Long = 60,
    /** Step of the refinement around the best coarse sample; sets the time resolution of tBest. */
    val fineStepSeconds: Long = 5,
    /** Knot spacing of the interpolated sun path (SunPath). Smaller = more exact, slower. */
    val pathKnotSeconds: Long = 180,
    /** Extra azimuth margin of the per-day prefilter around the sun's band ± 4°. */
    val bandMarginDeg: Double = 0.5,
    /** Brackets around sunset/sunrise in which altitudes are solved; the sun is monotonic there. */
    val sunsetBracketBefore: Duration = Duration.ofHours(3),
    val sunsetBracketAfter: Duration = Duration.ofHours(1),
    val sunriseBracketBefore: Duration = Duration.ofHours(1),
    val sunriseBracketAfter: Duration = Duration.ofHours(3),
    /** Time resolution of the window edges. */
    val solverToleranceSeconds: Double = 1.0,

    // ---- grades -------------------------------------------------------------------------
    /** PERFECT: on the horizon, at least this share of the disc showing, centring at most this. */
    val perfectMinDisc: Double = 0.9,
    val perfectMaxCentring: Double = 0.35,
    /** GOOD: at least half the disc, centring at most this, at most this high above the skyline. */
    val goodMinDisc: Double = 0.5,
    val goodMaxCentring: Double = 0.7,
    val goodMaxHeightDeg: Double = 1.5,

    // ---- trees ----------------------------------------------------------------------------
    /** Months in which trees count as in leaf. */
    val leafOnMonths: Set<Int> = (4..10).toSet(),
    /** Quality factor when a tree is in front of the sun in leaf. Tree data is coarse, so such
     * events are kept but demoted rather than dropped. */
    val treesQualityFactor: Double = 0.5,

    // ---- ranking (score = quality x (interestFloor + (1 - interestFloor) x interest)) -----
    val wFeatured: Double = 0.30,
    val wLength: Double = 0.25,
    val wFramed: Double = 0.20,
    val wSpots: Double = 0.15,
    val wNamed: Double = 0.10,
    /** Length normalised to 0..1 between these. */
    val lengthMinM: Double = 400.0,
    val lengthMaxM: Double = 1500.0,
    /** Spot count normalised to 0..1 up to this many. */
    val spotsMax: Int = 5,
    /** Framing: wall height at ± this offset relative to the floor. */
    val framedProbeDeg: Double = 1.5,
    /** Floor used in the framing ratio at least this, so a near-zero floor does not explode it. */
    val framedMinFloorDeg: Double = 0.3,
    /** Share of the score a superb alignment keeps on a plain street. Lower = plain streets sink. */
    val interestFloor: Double = 0.4,

    /** How far nextEventsFor scans before giving up (a bearing can be out of range all year). */
    val scanLimitDays: Int = 400,
)
