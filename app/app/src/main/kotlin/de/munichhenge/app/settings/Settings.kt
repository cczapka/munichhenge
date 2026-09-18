package de.munichhenge.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.munichhenge.engine.EngineConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-tunable settings (PLAN.md 4.3 screen 5). Defaults match EngineConfig / PLAN.md. */
data class Settings(
    /** Notifications only for events at least this good (M4). */
    val minQuality: Double = 0.7,
    /** Notification lead time before t_full, minutes (M4). */
    val leadMinutes: Int = 60,
    val perfectDeg: Double = 0.5,
    val goodDeg: Double = 1.5,
    val nearDeg: Double = 3.0,
    val obstructionOffsetDeg: Double = 0.0,
    val canyonBonus: Double = 0.0,
) {
    /** Tolerances are kept ordered so the bands stay meaningful whatever the sliders do. */
    fun normalized(): Settings {
        val near = nearDeg.coerceIn(0.5, 10.0)
        val good = goodDeg.coerceIn(0.1, near)
        val perfect = perfectDeg.coerceIn(0.05, good)
        return copy(
            minQuality = minQuality.coerceIn(0.0, 1.0), leadMinutes = leadMinutes.coerceIn(0, 24 * 60),
            perfectDeg = perfect, goodDeg = good, nearDeg = near,
            obstructionOffsetDeg = obstructionOffsetDeg.coerceIn(-5.0, 5.0), canyonBonus = canyonBonus.coerceIn(0.0, 0.5),
        )
    }

    fun toEngineConfig(): EngineConfig {
        val n = normalized()
        return EngineConfig(
            perfectDeg = n.perfectDeg, goodDeg = n.goodDeg, nearDeg = n.nearDeg,
            obstructionOffsetDeg = n.obstructionOffsetDeg, canyonBonus = n.canyonBonus,
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val minQuality = doublePreferencesKey("min_quality")
        val leadMinutes = intPreferencesKey("lead_minutes")
        val perfectDeg = doublePreferencesKey("perfect_deg")
        val goodDeg = doublePreferencesKey("good_deg")
        val nearDeg = doublePreferencesKey("near_deg")
        val obstructionOffsetDeg = doublePreferencesKey("obstruction_offset_deg")
        val canyonBonus = doublePreferencesKey("canyon_bonus")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            minQuality = p[Keys.minQuality] ?: d.minQuality,
            leadMinutes = p[Keys.leadMinutes] ?: d.leadMinutes,
            perfectDeg = p[Keys.perfectDeg] ?: d.perfectDeg,
            goodDeg = p[Keys.goodDeg] ?: d.goodDeg,
            nearDeg = p[Keys.nearDeg] ?: d.nearDeg,
            obstructionOffsetDeg = p[Keys.obstructionOffsetDeg] ?: d.obstructionOffsetDeg,
            canyonBonus = p[Keys.canyonBonus] ?: d.canyonBonus,
        ).normalized()
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val current = Settings(
                minQuality = p[Keys.minQuality] ?: 0.7, leadMinutes = p[Keys.leadMinutes] ?: 60,
                perfectDeg = p[Keys.perfectDeg] ?: 0.5, goodDeg = p[Keys.goodDeg] ?: 1.5, nearDeg = p[Keys.nearDeg] ?: 3.0,
                obstructionOffsetDeg = p[Keys.obstructionOffsetDeg] ?: 0.0, canyonBonus = p[Keys.canyonBonus] ?: 0.0,
            )
            val s = transform(current).normalized()
            p[Keys.minQuality] = s.minQuality
            p[Keys.leadMinutes] = s.leadMinutes
            p[Keys.perfectDeg] = s.perfectDeg
            p[Keys.goodDeg] = s.goodDeg
            p[Keys.nearDeg] = s.nearDeg
            p[Keys.obstructionOffsetDeg] = s.obstructionOffsetDeg
            p[Keys.canyonBonus] = s.canyonBonus
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}
