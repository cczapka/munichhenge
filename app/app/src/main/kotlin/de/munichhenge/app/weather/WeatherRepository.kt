package de.munichhenge.app.weather

import android.content.Context
import android.util.Log
import de.munichhenge.solar.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Fetches and caches Open-Meteo forecasts per grid cell, once per (UTC) day, in memory and in
 * the app cache dir so the notification worker reuses what the UI fetched. Every failure is
 * swallowed: offline simply means "no hint" (PLAN.md 4.4).
 */
class WeatherRepository(private val context: Context) {
    private class Entry(val fetched: LocalDate, val forecast: Forecast)

    private val memory = HashMap<String, Entry>()
    private val failedAt = HashMap<String, Long>()
    private val mutex = Mutex()
    private val dir: File get() = File(context.cacheDir, "weather").apply { mkdirs() }

    /** Retry interval after a failed fetch, so an offline phone does not hammer the network. */
    private val retryAfterMs = 10 * 60 * 1000L

    suspend fun cloudCover(at: GeoPoint, t: Instant): CloudCover? = forecast(at)?.at(t)

    suspend fun forecast(at: GeoPoint): Forecast? = withContext(Dispatchers.IO) {
        val key = OpenMeteo.cellKey(at)
        val today = LocalDate.now(ZoneOffset.UTC)
        mutex.withLock {
            memory[key]?.takeIf { it.fetched == today }?.let { return@withContext it.forecast }
            loadDisk(key, today)?.let { memory[key] = it; return@withContext it.forecast }
            val lastFail = failedAt[key]
            if (lastFail != null && System.currentTimeMillis() - lastFail < retryAfterMs) return@withContext null
            try {
                val body = fetch(OpenMeteo.url(at))
                val f = OpenMeteo.parse(body)
                memory[key] = Entry(today, f)
                saveDisk(key, today, body)
                f
            } catch (e: Exception) {
                Log.i("munichhenge", "weather fetch failed for $key: ${e.message}")
                failedAt[key] = System.currentTimeMillis()
                null
            }
        }
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "munichhenge/${context.packageName}")
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun diskFile(key: String, day: LocalDate) = File(dir, "$key-$day.json")

    private fun loadDisk(key: String, today: LocalDate): Entry? {
        val f = diskFile(key, today)
        if (!f.exists()) return null
        return try {
            Entry(today, OpenMeteo.parse(f.readText()))
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    private fun saveDisk(key: String, today: LocalDate, body: String) {
        try {
            dir.listFiles()?.filter { it.name.startsWith("$key-") }?.forEach { it.delete() }   // drop stale days
            diskFile(key, today).writeText(body)
        } catch (_: Exception) {
        }
    }
}
