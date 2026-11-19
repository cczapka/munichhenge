package de.munichhenge.app.model

import de.munichhenge.engine.Confidence
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Poi
import de.munichhenge.engine.Sightline
import de.munichhenge.engine.Style
import de.munichhenge.solar.GeoPoint
import java.time.Instant
import java.time.LocalDate

/** Small builders for the M7 presentation tests. */
internal object Fx {
    fun sightline(
        id: String = "sl_x", name: String? = "Landwehrstraße", kind: String = "street", lengthM: Double = 600.0,
        canyon: Boolean = false, featured: Boolean = false,
    ) = Sightline(
        id = id, name = name, kind = kind, a = GeoPoint(48.137, 11.55), b = GeoPoint(48.137, 11.558), lengthM = lengthM,
        maxOffsetM = 2.0, bearingAb = 90.0, bearingBa = 270.0, obstructionTowardADeg = 1.0, obstructionTowardBDeg = 1.0,
        canyon = canyon, featured = featured, notes = null, osmWayIds = emptyList(),
    )

    fun poi(name: String?, kind: String) = Poi(
        id = "poi_x", name = name, kind = kind, at = GeoPoint(48.137, 11.55), osmId = null, openHorizon = false,
        views = emptyList(), tags = emptyMap(), featured = false, notes = null,
    )

    /** An event whose window opens 2 min before and closes 3 min after `t`. */
    fun event(
        id: String = "sl_x", mode: Mode = Mode.SUNSET, grade: Grade = Grade.GOOD, score: Double = 0.8,
        quality: Double = score, t: String = "2026-11-19T15:20:00Z", bearing: Double = 270.0, spots: Int = 0,
        open: Boolean = false, stepRightM: Double? = null, confidence: Confidence = Confidence.ESTIMATED,
        trees: Boolean = false, date: LocalDate = LocalDate.parse(t.take(10)),
    ): HengeEvent {
        val tb = Instant.parse(t)
        return HengeEvent(
            sightlineId = id, viewFrom = Endpoint.B, bearing = bearing, mode = mode, date = date, tBest = tb,
            windowStart = tb.minusSeconds(120), windowEnd = tb.plusSeconds(180), style = Style.ON_HORIZON,
            sunAzimuthDeg = bearing, sunAltitudeDeg = 1.0, offsetDeg = 0.0, heightAboveFloorDeg = 0.3,
            discFraction = 1.0, centring = 0.1, stepRightM = stepRightM, treesInWay = trees, confidence = confidence,
            quality = quality, interest = 0.5, score = score, grade = grade,
            poiIds = (1..spots).map { "p$it" }, openHorizon = open,
        )
    }
}
