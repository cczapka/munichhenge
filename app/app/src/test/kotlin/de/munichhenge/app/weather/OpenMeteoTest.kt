package de.munichhenge.app.weather

import de.munichhenge.solar.GeoPoint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenMeteoTest {
    // trimmed real-shaped response: 4 hours from 2026-09-20T16:00Z, timeformat=unixtime
    private val body = """
    {"latitude":48.1,"longitude":11.6,"generationtime_ms":0.1,"utc_offset_seconds":0,"timezone":"UTC",
     "hourly_units":{"time":"unixtime","cloud_cover":"%","cloud_cover_low":"%"},
     "hourly":{"time":[1789920000,1789923600,1789927200,1789930800],
               "cloud_cover":[10,40,null,85],
               "cloud_cover_low":[0,5,null,80]}}
    """.trimIndent()

    @Test
    fun `parses hourly cloud cover and looks up the nearest hour`() {
        val f = OpenMeteo.parse(body)
        assertEquals(4, f.size)
        assertEquals(Instant.ofEpochSecond(1789920000), f.first)
        assertEquals(CloudCover(10, 0), f.at(Instant.ofEpochSecond(1789920000)))
        assertEquals(CloudCover(40, 5), f.at(Instant.ofEpochSecond(1789923600 + 20 * 60)))     // 16:20 -> 16:00 slot (index 1)
        assertEquals(CloudCover(85, 80), f.at(Instant.ofEpochSecond(1789930800 - 25 * 60)))    // nearest is the last hour
        assertNull(f.at(Instant.ofEpochSecond(1789927200)), "null value in the feed -> no hint")
        assertNull(f.at(Instant.ofEpochSecond(1789930800 + 3600)), "outside the forecast")
    }

    @Test
    fun `cells are 0_1 degree and the url asks for 16 days of cloud cover in unix time`() {
        val p = GeoPoint(48.1436, 11.5991)
        assertEquals(48.1 to 11.6, OpenMeteo.cell(p))
        assertEquals("48.1_11.6", OpenMeteo.cellKey(p))
        assertEquals(OpenMeteo.cellKey(p), OpenMeteo.cellKey(GeoPoint(48.14, 11.58)))
        val url = OpenMeteo.url(p)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=48.10&longitude=11.60"))
        assertTrue("hourly=cloud_cover,cloud_cover_low" in url && "forecast_days=16" in url && "timeformat=unixtime" in url)
    }

    @Test
    fun `symbols and the notification rule`() {
        assertEquals("☀", WeatherRules.symbol(0)); assertEquals("☀", WeatherRules.symbol(24))
        assertEquals("⛅", WeatherRules.symbol(25)); assertEquals("⛅", WeatherRules.symbol(59))
        assertEquals("☁", WeatherRules.symbol(60)); assertEquals("☁", WeatherRules.symbol(100))
        assertEquals("⛅ 40%", WeatherRules.label(CloudCover(40, null)))
        assertNull(WeatherRules.label(null))
        assertTrue(WeatherRules.worthNotifying(null))
        assertTrue(WeatherRules.worthNotifying(CloudCover(59, 10)))
        assertTrue(!WeatherRules.worthNotifying(CloudCover(60, 10)))
    }
}
