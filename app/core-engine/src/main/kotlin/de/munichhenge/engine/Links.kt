package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * External links that open Street View, walking directions or a map app at a sightline end.
 * Pure strings, no network: the app hands them to Android (CLAUDE.md allows opening external
 * apps; the app itself still only talks to Open-Meteo and the tile server).
 *
 * Mirrors pipeline/pipeline/links.py; both are checked against testdata/links_vectors.json.
 */
object Links {
    /** Street View field of view. A street's far end spans a degree or two; the default 90
     * makes it tiny, narrower than ~40 hides the flanking buildings. */
    const val STREETVIEW_FOV_DEG = 60

    /** Pitch follows the obstruction angle so the camera centres on the roofline; above 10°
     * that is no longer a street view. */
    const val PITCH_MAX_DEG = 10

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private fun coord(p: GeoPoint): String = String.format(Locale.ROOT, "%.6f,%.6f", p.lat, p.lon)

    /** Whole degrees, rounded half up, 360 -> 0. */
    fun heading(bearingDeg: Double): Int = Math.floorMod(floor(bearingDeg + 0.5).toInt(), 360)

    fun pitch(obstructionDeg: Double): Int = ceil(obstructionDeg).toInt().coerceIn(0, PITCH_MAX_DEG)

    private fun encode(label: String): String = buildString {
        for (b in label.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xff).toChar()
            if (b >= 0 && c in UNRESERVED) append(c) else append(String.format(Locale.ROOT, "%%%02X", b.toInt() and 0xff))
        }
    }

    fun streetView(at: GeoPoint, bearingDeg: Double, obstructionDeg: Double): String =
        "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${coord(at)}" +
            "&heading=${heading(bearingDeg)}&pitch=${pitch(obstructionDeg)}&fov=$STREETVIEW_FOV_DEG"

    fun streetView(t: LinkTarget): String = streetView(t.point, t.headingDeg, t.obstructionDeg)

    fun walk(to: GeoPoint): String =
        "https://www.google.com/maps/dir/?api=1&destination=${coord(to)}&travelmode=walking"

    fun geo(at: GeoPoint, label: String): String {
        val c = coord(at)
        return "geo:$c?q=$c(${encode(label)})"
    }
}

/**
 * Where to stand and which way to look for the links of one viewing direction.
 *
 * @property onWater the standing point is on a canal or river, so Street View shows the
 * nearest imagery from a bank or bridge rather than the exact spot.
 */
data class LinkTarget(
    val point: GeoPoint,
    val headingDeg: Double,
    val obstructionDeg: Double,
    val label: String,
    val onWater: Boolean,
) {
    companion object {
        private val WATER_KINDS = setOf("canal", "river")

        /** A sightline seen from one end. For bridges, A is the crossing point on the deck and
         * B the far point on the river. Not for open-horizon sightlines (no bearing). */
        fun forDirection(sl: Sightline, viewFrom: Endpoint): LinkTarget {
            val bearing = requireNotNull(sl.bearing(viewFrom)) { "${sl.id} has no bearing; use forOpenHorizon" }
            val onWater = sl.kind in WATER_KINDS || (sl.kind == "bridge" && viewFrom == Endpoint.B)
            return LinkTarget(sl.standingPoint(viewFrom), bearing, sl.obstruction(viewFrom), sl.name ?: sl.id, onWater)
        }

        /** An open-horizon spot, looking toward a given azimuth (normally the sun's at an event). */
        fun forOpenHorizon(sl: Sightline, azimuthDeg: Double): LinkTarget =
            LinkTarget(sl.a, azimuthDeg, sl.obstructionTowardBDeg, sl.name ?: sl.id, onWater = false)

        /** A spot's own position, looking along one of its views. */
        fun forPoiView(poi: Poi, view: View, sl: Sightline): LinkTarget {
            val viewFrom = view.toward.other
            val bearing = sl.bearing(viewFrom) ?: 0.0
            return LinkTarget(poi.at, bearing, sl.obstruction(viewFrom), poi.name ?: poi.id, onWater = false)
        }
    }
}
