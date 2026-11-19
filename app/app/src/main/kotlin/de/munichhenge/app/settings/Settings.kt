package de.munichhenge.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.munichhenge.app.model.EventFilter
import de.munichhenge.engine.EngineConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-tunable settings (PLAN.md 4.3 screen 5). Defaults match EngineConfig. */
data class Settings(
    /** Notifications only for events at least this good (M4). */
    val minQuality: Double = 0.7,
    /** Notification lead time before the window opens, minutes (M4). */
    val leadMinutes: Int = 60,
    /** EngineConfig.assumedStreetWidthM. */
    val streetWidthM: Double = 20.0,
    /** EngineConfig.maxHeightAboveFloorDeg. */
    val maxHeightAboveFloorDeg: Double = 4.0,
    val obstructionOffsetDeg: Double = 0.0,
) {
    fun normalized(): Settings = copy(
        minQuality = minQuality.coerceIn(0.0, 1.0), leadMinutes = leadMinutes.coerceIn(0, 24 * 60),
        streetWidthM = streetWidthM.coerceIn(5.0, 60.0),
        maxHeightAboveFloorDeg = maxHeightAboveFloorDeg.coerceIn(1.0, 8.0),
        obstructionOffsetDeg = obstructionOffsetDeg.coerceIn(-5.0, 5.0),
    )

    fun toEngineConfig(): EngineConfig {
        val n = normalized()
        return EngineConfig(
            assumedStreetWidthM = n.streetWidthM, maxHeightAboveFloorDeg = n.maxHeightAboveFloorDeg,
            obstructionOffsetDeg = n.obstructionOffsetDeg,
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val minQuality = doublePreferencesKey("min_quality")
        val leadMinutes = intPreferencesKey("lead_minutes")
        val streetWidthM = doublePreferencesKey("street_width_m")
        val maxHeightAboveFloorDeg = doublePreferencesKey("max_height_above_floor_deg")
        val obstructionOffsetDeg = doublePreferencesKey("obstruction_offset_deg")
        /** The shared Day / Upcoming / Map filter, EventFilter.encode(). */
        val eventFilter = stringPreferencesKey("event_filter")
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            minQuality = p[Keys.minQuality] ?: d.minQuality,
            leadMinutes = p[Keys.leadMinutes] ?: d.leadMinutes,
            streetWidthM = p[Keys.streetWidthM] ?: d.streetWidthM,
            maxHeightAboveFloorDeg = p[Keys.maxHeightAboveFloorDeg] ?: d.maxHeightAboveFloorDeg,
            obstructionOffsetDeg = p[Keys.obstructionOffsetDeg] ?: d.obstructionOffsetDeg,
        ).normalized()
    }

    val flow: Flow<Settings> = context.dataStore.data.map(::read)

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(read(p)).normalized()
            p[Keys.minQuality] = s.minQuality
            p[Keys.leadMinutes] = s.leadMinutes
            p[Keys.streetWidthM] = s.streetWidthM
            p[Keys.maxHeightAboveFloorDeg] = s.maxHeightAboveFloorDeg
            p[Keys.obstructionOffsetDeg] = s.obstructionOffsetDeg
        }
    }

    /** Resets the settings; the filter is kept (it has its own reset in the filter sheet). */
    suspend fun reset() {
        context.dataStore.edit { p ->
            p.remove(Keys.minQuality)
            p.remove(Keys.leadMinutes)
            p.remove(Keys.streetWidthM)
            p.remove(Keys.maxHeightAboveFloorDeg)
            p.remove(Keys.obstructionOffsetDeg)
        }
    }

    val filter: Flow<EventFilter> = context.dataStore.data.map { EventFilter.decode(it[Keys.eventFilter]) }

    suspend fun updateFilter(transform: (EventFilter) -> EventFilter) {
        context.dataStore.edit { p -> p[Keys.eventFilter] = transform(EventFilter.decode(p[Keys.eventFilter])).encode() }
    }
}
