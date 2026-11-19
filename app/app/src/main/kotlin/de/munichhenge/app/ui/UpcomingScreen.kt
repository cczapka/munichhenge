package de.munichhenge.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.model.Presentation
import de.munichhenge.app.model.UiConfig
import de.munichhenge.engine.Endpoint
import java.time.LocalDate

/**
 * The next two weeks at the shared filter (PLAN-links-and-ui.md 2.6): the best few on top,
 * then each day under a sticky header with its cloud outlook, capped with "Show all".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpcomingScreen(vm: AppViewModel, onOpenSightline: (String, Endpoint?) -> Unit) {
    val model by vm.upcoming.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val expanded by vm.expandedDays.collectAsStateWithLifecycle()
    val u = model
    when {
        u == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        u.days.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Nothing at this filter in the next ${UiConfig.UPCOMING_DAYS} days. Try \"Near (all)\" in the filter.")
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            item("hero_title") {
                Text(
                    "Best in the next ${UiConfig.UPCOMING_DAYS} days",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            items(u.hero, key = { "hero_${it.date}_${it.sightlineId}_${it.viewFrom}_${it.mode}" }) { e ->
                EventCard(vm, e, vm.sightline(e.sightlineId), now, showDate = true,
                    onOpen = { onOpenSightline(e.sightlineId, e.viewFrom) })
            }
            for (day in u.days) {
                stickyHeader(key = "h_${day.date}") { DayHeader(vm, day.date, day.total) }
                items(day.events, key = { "${day.date}_${it.sightlineId}_${it.viewFrom}_${it.mode}" }) { e ->
                    EventCard(vm, e, vm.sightline(e.sightlineId), now, onOpen = { onOpenSightline(e.sightlineId, e.viewFrom) })
                }
                if (day.hidden > 0 || day.date in expanded) {
                    item(key = "more_${day.date}") {
                        TextButton(onClick = { vm.toggleExpanded(day.date) }, modifier = Modifier.padding(start = 8.dp)) {
                            Text(if (day.hidden > 0) "Show all ${day.total}" else "Show fewer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(vm: AppViewModel, date: LocalDate, count: Int) {
    val outlook by produceState<String?>(initialValue = null, key1 = date) { value = vm.dayOutlook(date) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(Presentation.dateLabel(date), style = MaterialTheme.typography.titleSmall)
                outlook?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(if (count == 1) "1 event" else "$count events", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
