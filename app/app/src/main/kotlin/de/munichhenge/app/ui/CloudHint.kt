package de.munichhenge.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import de.munichhenge.app.weather.WeatherRules
import de.munichhenge.engine.HengeEvent

/** "☀ 12%" for the event hour, or null while loading / offline / beyond the forecast. */
@Composable
fun rememberCloudHint(vm: AppViewModel, event: HengeEvent): String? {
    val hint by produceState<String?>(initialValue = null, key1 = event.sightlineId, key2 = event.tFull) {
        value = WeatherRules.label(vm.cloudCover(event))
    }
    return hint
}
