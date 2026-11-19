package de.munichhenge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.settings.Settings

/** Notification thresholds and engine tunables (PLAN.md 4.3 screen 5), persisted in DataStore. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        SettingSlider("Minimum quality", s.minQuality, 0f..1f, format = { "%.2f".format(it) }) { v ->
            vm.updateSettings { it.copy(minQuality = v.toDouble()) }
        }
        SettingSlider("Lead time", s.leadMinutes.toDouble(), 0f..180f, steps = 11, format = { "${it.toInt()} min" }) { v ->
            vm.updateSettings { it.copy(leadMinutes = v.toInt()) }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Engine", style = MaterialTheme.typography.titleMedium)
        Text("Street width: how wide a street's far end is assumed to be where no measured skyline exists. " +
            "Wider streets let the sun in further off the axis.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSlider("Street width", s.streetWidthM, 10f..40f, steps = 5, format = { "${it.toInt()} m" }) { v ->
            vm.updateSettings { it.copy(streetWidthM = v.toDouble()) }
        }
        Text("Highest the sun may stand above the skyline and still count. Lower keeps only sunsets " +
            "right on the horizon.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSlider("Max height above skyline", s.maxHeightAboveFloorDeg, 1f..6f, format = { "%.1f°".format(it) }) { v ->
            vm.updateSettings { it.copy(maxHeightAboveFloorDeg = v.toDouble()) }
        }
        Text("Obstruction offset: added to every sightline's estimated obstruction angle.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSlider("Obstruction offset", s.obstructionOffsetDeg, -2f..3f, format = { "%+.1f°".format(it) }) { v ->
            vm.updateSettings { it.copy(obstructionOffsetDeg = v.toDouble()) }
        }
        TextButton(onClick = { vm.resetSettings() }, modifier = Modifier.padding(top = 8.dp)) { Text("Reset to defaults") }
    }
}

@Composable
private fun SettingSlider(
    label: String, value: Double, range: ClosedFloatingPointRange<Float>, steps: Int = 0,
    format: (Float) -> String, onChangeFinished: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(format(local), style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onChangeFinished(local) },
        valueRange = range,
        steps = steps,
    )
}
