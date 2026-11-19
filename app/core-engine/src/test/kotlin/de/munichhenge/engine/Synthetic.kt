package de.munichhenge.engine

import de.munichhenge.solar.AltitudeSolver
import de.munichhenge.solar.GeoPoint
import de.munichhenge.solar.SunEvents
import de.munichhenge.solar.SunPosition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.tan

/** Synthetic sightlines around Munich for engine tests. */
object Synthetic {
    val CENTER = GeoPoint(48.14, 11.58)
    private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    /** A sightline whose A->B bearing is `bearingAb` (B->A is bearingAb + 180). Open sides unless canyon. */
    fun street(id: String, bearingAb: Double, obstruction: Double = 1.0, canyon: Boolean = false,
               lengthM: Double = 800.0, name: String? = id, featured: Boolean = false): Sightline {
        val b = Geo.destination(CENTER, bearingAb, lengthM)
        return Sightline(
            id = id, name = name, kind = "street", a = CENTER, b = b, lengthM = lengthM, maxOffsetM = 0.0,
            bearingAb = Geo.normalizeDeg(bearingAb), bearingBa = Geo.normalizeDeg(bearingAb + 180.0),
            obstructionTowardADeg = obstruction, obstructionTowardBDeg = obstruction,
            canyon = canyon, featured = featured, notes = null, osmWayIds = emptyList(),
        )
    }

    /** A canyon street whose far end, 20 m wide (the default assumed width), spans ±e0Deg seen from A. */
    fun canyon(id: String, bearingAb: Double, floorDeg: Double = 1.0, e0Deg: Double = 1.0): Sightline =
        street(id, bearingAb, obstruction = floorDeg, canyon = true, lengthM = 10.0 / tan(Math.toRadians(e0Deg)))

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

    /** When the sun's centre has apparent altitude `altDeg` on `date`, and its azimuth then, at `at`. */
    fun sunAt(date: LocalDate, altDeg: Double, mode: Mode, at: GeoPoint = CENTER): Pair<Instant, Double> {
        val anchor = if (mode == Mode.SUNSET) SunEvents.sunset(date, at, ZONE)!! else SunEvents.sunrise(date, at, ZONE)!!
        val (from, to) = if (mode == Mode.SUNSET) anchor.minus(Duration.ofHours(3)) to anchor.plus(Duration.ofHours(1))
        else anchor.minus(Duration.ofHours(1)) to anchor.plus(Duration.ofHours(3))
        val t = AltitudeSolver.solve(at, altDeg, from, to)!!.time
        return t to SunPosition.at(t, at).azimuthDeg
    }
}
