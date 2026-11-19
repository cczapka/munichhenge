package de.munichhenge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.model.DayModel
import de.munichhenge.app.model.Presentation
import de.munichhenge.app.model.UiConfig
import de.munichhenge.engine.Endpoint
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One day (PLAN-links-and-ui.md 2.5): date header with the sun line, sunrise and sunset sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(vm: AppViewModel, onOpenSightline: (String, Endpoint?) -> Unit) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val events by vm.dayEvents.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val today = now.atZone(Presentation.zone).toLocalDate()
    val sunLine = remember(date) { Presentation.sunLine(date) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.shiftDate(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "previous day") }
            TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                Text(Presentation.dateLabel(date), style = MaterialTheme.typography.titleMedium)
            }
            if (date != today) {
                AssistChip(onClick = { vm.setDate(today) }, label = { Text("Today") })
            }
            IconButton(onClick = { vm.shiftDate(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "next day") }
        }
        Text(
            sunLine,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        QuickFilters(filter, vm::updateFilter, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

        val all = events
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val s = remember(all, filter, now) { DayModel.sections(all, filter, vm::sightline, now) }
        val open: (HengeEvent) -> Unit = { onOpenSightline(it.sightlineId, it.viewFrom) }

        val allPassed = s.sunrise.isEmpty() && s.sunset.isEmpty() && s.earlier.isNotEmpty()
        var showAllEarlier by rememberSaveable(date) { mutableStateOf(false) }
        val earlier = if (showAllEarlier) s.earlier else s.earlier.take(UiConfig.EARLIER_SHOWN)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (s.isEmpty) {
                item("empty") { EmptyDay(vm, date, "Nothing at this filter on ${Presentation.dateLabel(date)}.") }
            } else if (allPassed) {
                item("passed") { EmptyDay(vm, date, "All of today's moments have passed.") }
            }
            section("Sunrise", Mode.SUNRISE, s.sunrise, vm, now, open)
            section("Sunset", Mode.SUNSET, s.sunset, vm, now, open)
            section("Earlier today", null, earlier, vm, now, open, dimmed = true, total = s.earlier.size)
            if (s.earlier.size > UiConfig.EARLIER_SHOWN) {
                item("earlier_more") {
                    TextButton(onClick = { showAllEarlier = !showAllEarlier }, modifier = Modifier.padding(start = 8.dp)) {
                        Text(if (showAllEarlier) "Show fewer" else "Show all ${s.earlier.size}")
                    }
                }
            }
            if (s.hiddenCount > 0 && !s.isEmpty) {
                item("hidden") {
                    Text(
                        "${s.hiddenCount} more on this day hidden by the filter.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.openHorizon.isNotEmpty()) {
                item("open") {
                    Text(
                        "Open horizon · " + Presentation.openHorizonLine(s.openHorizon) { id ->
                            vm.sightline(id)?.let(Presentation::sightlineName)
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        vm.setDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun LazyListScope.section(
    title: String, mode: Mode?, events: List<HengeEvent>, vm: AppViewModel, now: Instant, open: (HengeEvent) -> Unit,
    dimmed: Boolean = false, total: Int = events.size,
) {
    if (events.isEmpty()) return
    item("h_$title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
        ) {
            if (mode != null) ModeIcon(mode, size = 20.dp, modifier = Modifier.padding(end = 6.dp))
            Text("$title · $total", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    items(events, key = { "${title}_${it.sightlineId}_${it.viewFrom}_${it.mode}" }) { e ->
        EventCard(vm, e, vm.sightline(e.sightlineId), now, onOpen = { open(e) }, dimmed = dimmed)
    }
}

/** Nothing (left) at the filter on this day: say so and offer the next date that has something. */
@Composable
private fun EmptyDay(vm: AppViewModel, date: LocalDate, headline: String) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    // null while searching; Optional.empty() when nothing turns up within the scan horizon
    val next by produceState<java.util.Optional<LocalDate>?>(null, date, filter, engine) {
        value = null
        value = java.util.Optional.ofNullable(vm.nextDateWithEvent(date, filter))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(headline, style = MaterialTheme.typography.bodyLarge)
        val n = next
        when {
            n == null -> Text(
                "Looking for the next date…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            n.isPresent -> FilledTonalButton(onClick = { vm.setDate(n.get()) }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Next: ${Presentation.dateLabel(n.get())}")
            }
            else -> Text(
                "Nothing in the next ${UiConfig.NEXT_EVENT_SCAN_DAYS} days either. Try \"Include near\" or fewer filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
