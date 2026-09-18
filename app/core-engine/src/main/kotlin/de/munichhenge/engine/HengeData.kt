package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The loaded dataset with lookup indexes. Immutable. */
class HengeData(val sightlines: List<Sightline>, val pois: List<Poi>) {
    private val sightlineById: Map<String, Sightline> = sightlines.associateBy { it.id }
    private val poiById: Map<String, Poi> = pois.associateBy { it.id }

    /** (sightlineId, endpoint looked at) -> poi ids sorted. */
    private val poisByView: Map<Pair<String, Endpoint>, List<String>> =
        pois.flatMap { p -> p.views.map { v -> (v.sightlineId to v.toward) to p.id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.distinct().sorted() }

    fun sightline(id: String): Sightline? = sightlineById[id]
    fun poi(id: String): Poi? = poiById[id]

    /** POIs looking down `sightlineId` from `viewFrom` (i.e. toward the other endpoint). */
    fun poiIdsFor(sightlineId: String, viewFrom: Endpoint): List<String> =
        poisByView[sightlineId to viewFrom.other] ?: emptyList()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parse data/sightlines.json and data/pois.json as written by the pipeline (schema v1). */
        fun fromJson(sightlinesJson: String, poisJson: String): HengeData {
            val s = json.decodeFromString<SightlinesFile>(sightlinesJson)
            val p = json.decodeFromString<PoisFile>(poisJson)
            require(s.version == 1) { "unsupported sightlines.json version ${s.version}" }
            require(p.version == 1) { "unsupported pois.json version ${p.version}" }
            return HengeData(s.sightlines.map { it.toModel() }, p.pois.map { it.toModel() })
        }
    }
}

private fun List<Double>.toGeoPoint(): GeoPoint {
    require(size == 2) { "expected [lat, lon], got $this" }
    return GeoPoint(this[0], this[1])
}

@Serializable
internal data class SightlinesFile(val version: Int, val sightlines: List<SightlineJson>)

@Serializable
internal data class PoisFile(val version: Int, val pois: List<PoiJson>)

@Serializable
internal data class SightlineJson(
    val id: String,
    val name: String? = null,
    val kind: String,
    val a: List<Double>,
    val b: List<Double>,
    @SerialName("length_m") val lengthM: Double,
    @SerialName("max_offset_m") val maxOffsetM: Double = 0.0,
    @SerialName("bearing_ab") val bearingAb: Double? = null,
    @SerialName("bearing_ba") val bearingBa: Double? = null,
    @SerialName("obstruction_toward_a_deg") val obstructionTowardADeg: Double,
    @SerialName("obstruction_toward_b_deg") val obstructionTowardBDeg: Double,
    val canyon: Boolean = false,
    val featured: Boolean = false,
    val notes: String? = null,
    @SerialName("osm_way_ids") val osmWayIds: List<Long> = emptyList(),
) {
    fun toModel() = Sightline(
        id = id, name = name, kind = kind, a = a.toGeoPoint(), b = b.toGeoPoint(), lengthM = lengthM,
        maxOffsetM = maxOffsetM, bearingAb = bearingAb, bearingBa = bearingBa,
        obstructionTowardADeg = obstructionTowardADeg, obstructionTowardBDeg = obstructionTowardBDeg,
        canyon = canyon, featured = featured, notes = notes, osmWayIds = osmWayIds,
    )
}

@Serializable
internal data class ViewJson(
    @SerialName("sightline_id") val sightlineId: String,
    val toward: String,
    @SerialName("distance_m") val distanceM: Double = 0.0,
) {
    fun toModel() = View(sightlineId, if (toward == "a") Endpoint.A else Endpoint.B, distanceM)
}

@Serializable
internal data class PoiJson(
    val id: String,
    val name: String? = null,
    val kind: String,
    val at: List<Double>,
    @SerialName("osm_id") val osmId: String? = null,
    @SerialName("open_horizon") val openHorizon: Boolean = false,
    val views: List<ViewJson> = emptyList(),
    val tags: Map<String, String> = emptyMap(),
    val featured: Boolean = false,
    val notes: String? = null,
) {
    fun toModel() = Poi(
        id = id, name = name, kind = kind, at = at.toGeoPoint(), osmId = osmId, openHorizon = openHorizon,
        views = views.map { it.toModel() }, tags = tags, featured = featured, notes = notes,
    )
}
