package de.munichhenge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.HengeEngine
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Poi
import de.munichhenge.engine.Sightline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val NEXT_COUNT = 5

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun NextEvents(vm: AppViewModel, engine: HengeEngine?, load: suspend (HengeEngine) -> List<HengeEvent>,
                       sightlineOf: (HengeEvent) -> Sightline?, onOpen: (HengeEvent) -> Unit) {
    val next by produceState<List<HengeEvent>?>(initialValue = null, key1 = engine) {
        value = engine?.let { e -> withContext(Dispatchers.Default) { load(e) } }
    }
    var message by remember { mutableStateOf<String?>(null) }
    SectionTitle("Next events")
    when (val list = next) {
        null -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        else -> if (list.isEmpty()) Text("No alignment in the next year.", modifier = Modifier.padding(16.dp))
        else for (e in list) {
            EventRow(e, sightlineOf(e), showDate = true, cloudHint = rememberCloudHint(vm, e), onClick = { onOpen(e) })
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val lead = vm.settings.value?.leadMinutes ?: 60
                    message = if (vm.addReminder(e)) {
                        "Reminder set for $lead min before ${Presentation.localTime(e.tFull)} on ${Presentation.dateLabel(e.date)}"
                    } else {
                        "That moment has already passed."
                    }
                }) { Text("Add reminder") }
            }
            HorizontalDivider()
        }
    }
    message?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.tertiary) }
}

@Composable
fun SightlineDetailScreen(vm: AppViewModel, id: String, onOpenPoi: (String) -> Unit, onBack: () -> Unit) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val sl = vm.data.sightline(id)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(sl?.name ?: id, onBack)
        if (sl == null) {
            Text("Unknown sightline $id", modifier = Modifier.padding(16.dp))
            return@Column
        }
        Facts(sl)
        NextEvents(vm, engine, load = { it.nextEventsFor(id, LocalDate.now(Presentation.zone), NEXT_COUNT) },
            sightlineOf = { sl }, onOpen = { vm.setDate(it.date) })
        SectionTitle("Spots")
        val spots = vm.data.pois.filter { p -> p.views.any { it.sightlineId == id } }.sortedBy { it.name ?: it.id }
        if (spots.isEmpty()) Text("No spots within 40 m.", modifier = Modifier.padding(16.dp))
        for (p in spots) {
            val toward = p.views.filter { it.sightlineId == id }.map { it.toward }
            ListItem(
                headlineContent = { Text(p.name ?: p.id) },
                supportingContent = {
                    val looks = toward.joinToString(" / ") { "looks " + Presentation.compass16(sl.bearing(it.other) ?: 0.0) }
                    Text(listOfNotNull(p.kind, looks, p.tags["opening_hours"]).joinToString(" · "))
                },
                modifier = Modifier.clickable { onOpenPoi(p.id) },
            )
            HorizontalDivider()
        }
        Text("Cloud cover (Open-Meteo) is shown next to events within the 16-day forecast; reminders fire " +
            "the lead time from Settings before the full-disk moment.",
            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Facts(sl: Sightline) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("${sl.kind} · ${sl.lengthM.toInt()} m" + if (sl.canyon) " · canyon" else "", style = MaterialTheme.typography.bodyMedium)
        if (!sl.openHorizon) {
            Text("From the ${Presentation.endpointWord(sl, Endpoint.A)} end look ${Presentation.compass16(sl.bearingAb!!)} " +
                "(${"%.1f".format(sl.bearingAb)}°), obstruction ${"%.1f".format(sl.obstructionTowardBDeg)}°",
                style = MaterialTheme.typography.bodyMedium)
            Text("From the ${Presentation.endpointWord(sl, Endpoint.B)} end look ${Presentation.compass16(sl.bearingBa!!)} " +
                "(${"%.1f".format(sl.bearingBa)}°), obstruction ${"%.1f".format(sl.obstructionTowardADeg)}°",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Open horizon, obstruction ${"%.1f".format(sl.obstructionTowardBDeg)}°", style = MaterialTheme.typography.bodyMedium)
        }
        sl.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (sl.featured) Text("featured", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun PoiDetailScreen(vm: AppViewModel, id: String, onOpenSightline: (String) -> Unit, onBack: () -> Unit) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val poi: Poi? = vm.data.poi(id)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(poi?.name ?: id, onBack)
        if (poi == null) {
            Text("Unknown spot $id", modifier = Modifier.padding(16.dp))
            return@Column
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(poi.kind + if (poi.openHorizon) " · open horizon" else "", style = MaterialTheme.typography.bodyMedium)
            poi.tags["opening_hours"]?.let { Text("Opening hours: $it", style = MaterialTheme.typography.bodyMedium) }
            poi.tags["website"]?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            poi.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        SectionTitle("Sightlines")
        for (v in poi.views) {
            val sl = vm.data.sightline(v.sightlineId) ?: continue
            ListItem(
                headlineContent = { Text(sl.name ?: sl.id) },
                supportingContent = {
                    val b = sl.bearing(v.toward.other)
                    Text(if (b == null) "open horizon" else "looks ${Presentation.compass16(b)} (${"%.0f".format(b)}°) · ${v.distanceM.toInt()} m from the line")
                },
                modifier = Modifier.clickable { onOpenSightline(sl.id) },
            )
            HorizontalDivider()
        }
        NextEvents(vm, engine, load = { it.nextEventsForPoi(id, LocalDate.now(Presentation.zone), NEXT_COUNT) },
            sightlineOf = { vm.data.sightline(it.sightlineId) }, onOpen = { vm.setDate(it.date); onOpenSightline(it.sightlineId) })
    }
}
