package de.munichhenge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Mode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: AppViewModel, onOpenSightline: (String) -> Unit) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val modes by vm.modes.collectAsStateWithLifecycle()
    val events by vm.dayEvents.collectAsStateWithLifecycle()
    val showNear by vm.showNear.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.shiftDate(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "previous day") }
            TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                Text(Presentation.dateLabel(date), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { vm.shiftDate(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "next day") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = Mode.SUNRISE in modes, onClick = { vm.toggleMode(Mode.SUNRISE) }, label = { Text("Sunrise") })
            FilterChip(selected = Mode.SUNSET in modes, onClick = { vm.toggleMode(Mode.SUNSET) }, label = { Text("Sunset") })
            FilterChip(selected = showNear, onClick = { vm.toggleNear() }, label = { Text("Near") })
            TextButton(onClick = { vm.setDate(LocalDate.now(Presentation.zone)) }) { Text("Today") }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        val all = events
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val list = Presentation.dayList(all, showNear)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (list.events.isEmpty()) {
                item("empty") {
                    Text(
                        if (showNear) "No aligned sightlines on this day." else "No perfect or good alignment on this day. Try \"Near\".",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(list.events, key = { it.sightlineId + it.viewFrom + it.mode }) { e ->
                EventRow(e, vm.data.sightline(e.sightlineId), onClick = { onOpenSightline(e.sightlineId) })
                HorizontalDivider()
            }
            if (list.openHorizon.isNotEmpty()) {
                item("open") {
                    Text(
                        "Open horizon · " + Presentation.openHorizonLine(list.openHorizon) { vm.data.sightline(it)?.name },
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
