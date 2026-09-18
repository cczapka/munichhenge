package de.munichhenge.app

import android.content.Context
import de.munichhenge.app.settings.Settings
import de.munichhenge.app.settings.SettingsRepository
import de.munichhenge.app.weather.WeatherRepository
import de.munichhenge.engine.HengeData
import de.munichhenge.engine.HengeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Application-wide singletons: the loaded dataset, settings and the engine (rebuilt whenever
 * an engine tunable changes). The app never does geo processing; it only reads the JSON
 * built by the pipeline from assets (CLAUDE.md).
 */
class AppGraph(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val weather = WeatherRepository(context)

    val data: HengeData by lazy {
        val assets = context.assets
        val s = assets.open("data/sightlines.json").bufferedReader().use { it.readText() }
        val p = assets.open("data/pois.json").bufferedReader().use { it.readText() }
        HengeData.fromJson(s, p)
    }

    /** Null until the data is loaded and the first settings value has arrived. */
    val engine: StateFlow<HengeEngine?> = settings.flow
        .map { s: Settings -> withContext(Dispatchers.Default) { HengeEngine(data, s.toEngineConfig()) } }
        .stateIn(scope, SharingStarted.Eagerly, null)
}
