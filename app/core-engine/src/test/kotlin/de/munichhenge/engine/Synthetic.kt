package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint

/** Synthetic sightlines around Munich for engine tests. All 800 m long, obstruction 1.0° both ways. */
object Synthetic {
    val CENTER = GeoPoint(48.14, 11.58)

    /** A sightline whose A->B bearing is `bearingAb` (B->A is bearingAb + 180). */
    fun street(id: String, bearingAb: Double, obstruction: Double = 1.0, canyon: Boolean = false,
               lengthM: Double = 800.0): Sightline {
        val b = Geo.destination(CENTER, bearingAb, lengthM)
        return Sightline(
            id = id, name = id, kind = "street", a = CENTER, b = b, lengthM = lengthM, maxOffsetM = 0.0,
            bearingAb = bearingAb % 360.0, bearingBa = (bearingAb + 180.0) % 360.0,
            obstructionTowardADeg = obstruction, obstructionTowardBDeg = obstruction,
            canyon = canyon, featured = false, notes = null, osmWayIds = emptyList(),
        )
    }

    fun openHorizon(id: String): Sightline = Sightline(
        id = id, name = id, kind = "open_horizon", a = CENTER, b = CENTER, lengthM = 0.0, maxOffsetM = 0.0,
        bearingAb = null, bearingBa = null, obstructionTowardADeg = 0.5, obstructionTowardBDeg = 0.5,
        canyon = false, featured = true, notes = null, osmWayIds = emptyList(),
    )

    fun poi(id: String, vararg views: View): Poi =
        Poi(id = id, name = id, kind = "cafe", at = CENTER, osmId = null, openHorizon = false,
            views = views.toList(), tags = emptyMap(), featured = false, notes = null)

    fun data(vararg sightlines: Sightline, pois: List<Poi> = emptyList()) =
        HengeData(sightlines.toList(), pois)
}
