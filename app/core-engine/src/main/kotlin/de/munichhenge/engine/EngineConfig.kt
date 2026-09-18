package de.munichhenge.engine

import java.time.Duration
import java.time.ZoneId

/**
 * All engine tunables in one place (CLAUDE.md rule 3). The app exposes some of them in
 * Settings so they can be tuned without rebuilding.
 */
data class EngineConfig(
    /** Calendar dates of events are in this zone; times stay UTC instants. */
    val zone: ZoneId = ZoneId.of("Europe/Berlin"),

    /** Alignment error bands, degrees. Wider = more, worse events. */
    val perfectDeg: Double = 0.5,
    val goodDeg: Double = 1.5,
    /** Beyond this there is no event; also the denominator of the quality score. */
    val nearDeg: Double = 3.0,

    /** Added to the quality of canyon sightlines (capped at 1). 0 = off. A canyon frames
     * the sun better, but boosting hides the pure alignment ranking. */
    val canyonBonus: Double = 0.0,

    /** Sun disk radius, degrees. */
    val sunRadiusDeg: Double = 0.2665,

    /** Root-finding brackets around sunset/sunrise; the altitude is monotonic within them. */
    val sunsetBracketBefore: Duration = Duration.ofHours(3),
    val sunsetBracketAfter: Duration = Duration.ofHours(1),
    val sunriseBracketBefore: Duration = Duration.ofHours(1),
    val sunriseBracketAfter: Duration = Duration.ofHours(3),

    /** Sightlines whose bearing is further than nearDeg + this from the closed-form azimuth
     * at the target altitude are skipped without root finding. Covers the declination
     * drift within the bracket (< 0.1°) and the refraction approximation. */
    val prefilterMarginDeg: Double = 1.0,

    /** Time resolution of henge moments. */
    val solverToleranceSeconds: Double = 1.0,

    /** How far nextEventsFor scans before giving up (a bearing can be out of range all year). */
    val scanLimitDays: Int = 400,
)
