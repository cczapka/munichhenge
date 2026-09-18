package de.munichhenge.app.weather

import de.munichhenge.solar.GeoPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.math.abs

/** Cloud cover at one hour, percent. */
data class CloudCover(val totalPct: Int, val lowPct: Int?)

/** Hourly cloud-cover forecast for one grid cell. Pure data, testable without network. */
class Forecast(private val times: LongArray, private val total: IntArray, private val low: IntArray?) {
    val size: Int get() = times.size
    val first: Instant? get() = if (times.isEmpty()) null else Instant.ofEpochSecond(times.first())
    val last: Instant? get() = if (times.isEmpty()) null else Instant.ofEpochSecond(times.last())

    /** Value of the hour nearest to `t`, or null when `t` is outside the forecast (± 30 min). */
    fun at(t: Instant): CloudCover? {
        if (times.isEmpty()) return null
        val s = t.epochSecond
        var best = 0
        for (i in times.indices) if (abs(times[i] - s) < abs(times[best] - s)) best = i
        if (abs(times[best] - s) > 30 * 60) return null
        val tot = total[best]
        if (tot < 0) return null
        return CloudCover(tot, low?.get(best)?.takeIf { it >= 0 })
    }
}

/** Open-Meteo forecast API: no key, hourly cloud cover, up to 16 days (PLAN.md 4.4). */
object OpenMeteo {
    const val FORECAST_DAYS = 16

    /** Grid cells per degree: 0.1° cells (~7 x 11 km). Open-Meteo's models are 1-11 km, so one
     * forecast serves all sightlines of a district and requests stay at a handful per day. */
    const val CELLS_PER_DEG = 10

    /** Cell centre; divide (not multiply) so 48.1 comes out as the same double as the literal. */
    fun cell(p: GeoPoint): Pair<Double, Double> =
        Math.round(p.lat * CELLS_PER_DEG) / CELLS_PER_DEG.toDouble() to Math.round(p.lon * CELLS_PER_DEG) / CELLS_PER_DEG.toDouble()

    fun cellKey(p: GeoPoint): String {
        val (lat, lon) = cell(p)
        return "%.1f_%.1f".format(java.util.Locale.ROOT, lat, lon)
    }

    fun url(p: GeoPoint): String {
        val (lat, lon) = cell(p)
        return "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(java.util.Locale.ROOT, lat, lon) +
            "&hourly=cloud_cover,cloud_cover_low&forecast_days=$FORECAST_DAYS&timezone=UTC&timeformat=unixtime"
    }

    @Serializable
    private data class Response(val hourly: Hourly)

    @Serializable
    private data class Hourly(
        val time: List<Long>,
        @SerialName("cloud_cover") val cloudCover: List<Int?>,
        @SerialName("cloud_cover_low") val cloudCoverLow: List<Int?>? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): Forecast {
        val h = json.decodeFromString<Response>(body).hourly
        require(h.time.size == h.cloudCover.size) { "time/cloud_cover length mismatch" }
        val low = h.cloudCoverLow?.takeIf { it.size == h.time.size }
        return Forecast(
            times = h.time.toLongArray(),
            total = IntArray(h.time.size) { h.cloudCover[it] ?: -1 },
            low = low?.let { l -> IntArray(h.time.size) { l[it] ?: -1 } },
        )
    }
}

/** Thresholds for the ☀ / ⛅ / ☁ hint and the notification rule. */
object WeatherRules {
    /** Below this total cloud cover the sky counts as clear. */
    const val CLEAR_MAX_PCT = 25
    /** Below this it is partly cloudy; at or above, cloudy. PLAN.md 4.5 notifies below 60 %. */
    const val CLOUDY_MIN_PCT = 60

    fun symbol(totalPct: Int): String = when {
        totalPct < CLEAR_MAX_PCT -> "☀"
        totalPct < CLOUDY_MIN_PCT -> "⛅"
        else -> "☁"
    }

    fun label(c: CloudCover?): String? = c?.let { "${symbol(it.totalPct)} ${it.totalPct}%" }

    /** Notify unless the forecast says it is cloudy; unknown counts as fine (PLAN.md 4.5). */
    fun worthNotifying(c: CloudCover?): Boolean = c == null || c.totalPct < CLOUDY_MIN_PCT
}
