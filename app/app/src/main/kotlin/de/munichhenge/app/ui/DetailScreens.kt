package de.munichhenge.app.ui

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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.model.Presentation
import androidx.compose.ui.platform.LocalContext
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.HengeEngine
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.LinkTarget
import de.munichhenge.engine.Links
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Poi
import de.munichhenge.engine.Sightline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val NEXT_COUNT = 5

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
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
    val now by vm.now.collectAsStateWithLifecycle()
    SectionTitle("Next events")
    when (val list = next) {
        null -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        else -> if (list.isEmpty()) Text("No alignment in the next year.", modifier = Modifier.padding(16.dp))
        else {
            Text("Hold a card or tap ⋮ for a reminder.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            for (e in list) EventCard(vm, e, sightlineOf(e), now, showDate = true, onOpen = { onOpen(e) })
        }
    }
}

@Composable
fun SightlineDetailScreen(vm: AppViewModel, id: String, from: Endpoint?, onOpenPoi: (String) -> Unit, onBack: () -> Unit) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val sl = vm.data.sightline(id)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackHeader(sl?.let(Presentation::sightlineName) ?: "Unknown sightline", onBack)
        if (sl == null) {
            Text("This sightline is not in the current data.", modifier = Modifier.padding(16.dp))
            return@Column
        }
        Facts(sl)
        SectionTitle("Where to stand")
        if (sl.openHorizon) {
            // look toward the next sunset; west until the engine has answered
            val nextSunsetAz by produceState<Double?>(initialValue = null, key1 = engine) {
                value = engine?.let { e ->
                    withContext(Dispatchers.Default) {
                        e.nextEventsFor(id, LocalDate.now(Presentation.zone), 1, setOf(Mode.SUNSET)).firstOrNull()?.bearing
                    }
                }
            }
            val az = nextSunsetAz ?: 270.0
            DirectionCard(
                title = "Open horizon",
                detail = "Street View faces the next sunset (${Links.heading(az)}°).",
                target = LinkTarget.forOpenHorizon(sl, az),
                highlighted = false,
            )
        } else {
            // the direction of the event the user came from first, and outlined
            for (end in Endpoint.entries.sortedBy { it != from }) {
                val target = LinkTarget.forDirection(sl, end)
                DirectionCard(
                    title = Presentation.directionTitle(sl, end),
                    detail = Presentation.directionDetail(sl, end, target.onWater),
                    target = target,
                    highlighted = end == from,
                )
            }
        }
        StreetViewHint()
        NextEvents(vm, engine, load = { it.nextEventsFor(id, LocalDate.now(Presentation.zone), NEXT_COUNT) },
            sightlineOf = { sl }, onOpen = { vm.setDate(it.date) })
        SectionTitle("Spots")
        val spots = vm.data.pois.filter { p -> p.views.any { it.sightlineId == id } }.sortedBy { Presentation.poiName(it) }
        if (spots.isEmpty()) Text("No spots within 40 m.", modifier = Modifier.padding(16.dp))
        for (p in spots) {
            val toward = p.views.filter { it.sightlineId == id }.map { it.toward }
            ListItem(
                headlineContent = { Text(Presentation.poiName(p)) },
                supportingContent = {
                    val looks = toward.joinToString(" / ") { "looks " + Presentation.compass16(sl.bearing(it.other) ?: 0.0) }
                    Text(listOfNotNull(p.kind, looks, p.tags["opening_hours"]).joinToString(" · "))
                },
                modifier = Modifier.clickable { onOpenPoi(p.id) },
            )
            HorizontalDivider()
        }
        Text("Cloud cover (Open-Meteo) is shown next to events within the 16-day forecast; reminders fire " +
            "the lead time from Settings before the window opens.",
            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Facts(sl: Sightline) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("${sl.kind} · ${sl.lengthM.toInt()} m" + if (sl.canyon) " · canyon" else "", style = MaterialTheme.typography.bodyMedium)
        sl.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (sl.featured) Text("featured", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun PoiDetailScreen(vm: AppViewModel, id: String, onOpenSightline: (String, Endpoint?) -> Unit, onBack: () -> Unit) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val poi: Poi? = vm.data.poi(id)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackHeader(poi?.let(Presentation::poiName) ?: "Unknown spot", onBack)
        if (poi == null) {
            Text("This spot is not in the current data.", modifier = Modifier.padding(16.dp))
            return@Column
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(poi.kind + if (poi.openHorizon) " · open horizon" else "", style = MaterialTheme.typography.bodyMedium)
            poi.tags["opening_hours"]?.let { Text("Opening hours: $it", style = MaterialTheme.typography.bodyMedium) }
            poi.tags["website"]?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            poi.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { LinkLauncher.open(context, Links.walk(poi.at)) }) { Text("Walk there") }
            TextButton(onClick = {
                LinkLauncher.open(context, Links.geo(poi.at, Presentation.poiName(poi)), chooserTitle = "Open in")
            }) { Text("Open in maps") }
        }
        SectionTitle("Sightlines")
        for (v in poi.views) {
            val sl = vm.data.sightline(v.sightlineId) ?: continue
            ListItem(
                headlineContent = { Text(Presentation.sightlineName(sl)) },
                supportingContent = {
                    val b = sl.bearing(v.toward.other)
                    Text(if (b == null) "open horizon" else "looks ${Presentation.compass16(b)} (${"%.0f".format(b)}°) · ${v.distanceM.toInt()} m from the line")
                },
                trailingContent = if (sl.openHorizon) null else {
                    {
                        TextButton(onClick = {
                            LinkLauncher.open(context, Links.streetView(LinkTarget.forPoiView(poi, v, sl)))
                        }) { Text("Street View") }
                    }
                },
                modifier = Modifier.clickable { onOpenSightline(sl.id, v.toward.other) },
            )
            HorizontalDivider()
        }
        StreetViewHint()
        NextEvents(vm, engine, load = { it.nextEventsForPoi(id, LocalDate.now(Presentation.zone), NEXT_COUNT) },
            sightlineOf = { vm.data.sightline(it.sightlineId) },
            onOpen = { vm.setDate(it.date); onOpenSightline(it.sightlineId, it.viewFrom) })
    }
}
