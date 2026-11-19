package de.munichhenge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.munichhenge.app.model.EventFilter
import de.munichhenge.app.model.SortOrder
import de.munichhenge.engine.Grade
import de.munichhenge.engine.Mode

/** The full filter, opened from the chip in the top bar. Changes apply at once and persist. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(filter: EventFilter, onChange: ((EventFilter) -> EventFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Filter", style = MaterialTheme.typography.titleLarge)
            Text("Applies to Day, Upcoming and Map.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Section("Show")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = Mode.SUNRISE in filter.modes, onClick = { onChange { it.toggleMode(Mode.SUNRISE) } },
                    label = { Text("Sunrise") }, leadingIcon = { ModeIcon(Mode.SUNRISE, 18.dp) })
                FilterChip(selected = Mode.SUNSET in filter.modes, onClick = { onChange { it.toggleMode(Mode.SUNSET) } },
                    label = { Text("Sunset") }, leadingIcon = { ModeIcon(Mode.SUNSET, 18.dp) })
            }

            Section("At least")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((g, label) in listOf(Grade.PERFECT to "Perfect", Grade.GOOD to "Good", Grade.NEAR to "Near (all)")) {
                    FilterChip(selected = filter.minGrade == g, onClick = { onChange { it.copy(minGrade = g) } },
                        label = { Text(label) })
                }
            }
            Text("Near: the sun shows down the street but off-centre or high above the far end.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))

            Section("Only")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter.withSpots, onClick = { onChange { it.copy(withSpots = !it.withSpots) } },
                    label = { Text("With spots") })
                FilterChip(selected = filter.canyonOnly, onClick = { onChange { it.copy(canyonOnly = !it.canyonOnly) } },
                    label = { Text("Canyons") })
            }

            Section("Sort")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((s, label) in listOf(SortOrder.BEST to "Best", SortOrder.ALIGNMENT to "Alignment", SortOrder.TIME to "Time")) {
                    FilterChip(selected = filter.sort == s, onClick = { onChange { it.copy(sort = s) } }, label = { Text(label) })
                }
            }
            Text("Best weighs today's alignment by how worthwhile the place is (featured, long, framed, spots).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))

            TextButton(onClick = { onChange { EventFilter() } }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Reset filter")
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

/** The most used filter switches as one horizontally scrolling row (Day screen). */
@Composable
fun QuickFilters(filter: EventFilter, onChange: ((EventFilter) -> EventFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = Mode.SUNRISE in filter.modes, onClick = { onChange { it.toggleMode(Mode.SUNRISE) } },
            label = { Text("Sunrise") }, leadingIcon = { ModeIcon(Mode.SUNRISE, 18.dp) })
        FilterChip(selected = Mode.SUNSET in filter.modes, onClick = { onChange { it.toggleMode(Mode.SUNSET) } },
            label = { Text("Sunset") }, leadingIcon = { ModeIcon(Mode.SUNSET, 18.dp) })
        FilterChip(
            selected = filter.minGrade == Grade.PERFECT,
            onClick = { onChange { it.copy(minGrade = if (it.minGrade == Grade.PERFECT) Grade.GOOD else Grade.PERFECT) } },
            label = { Text("Perfect only") },
        )
        FilterChip(
            selected = filter.minGrade == Grade.NEAR,
            onClick = { onChange { it.copy(minGrade = if (it.minGrade == Grade.NEAR) Grade.GOOD else Grade.NEAR) } },
            label = { Text("Include near") },
        )
        FilterChip(selected = filter.withSpots, onClick = { onChange { it.copy(withSpots = !it.withSpots) } },
            label = { Text("With spots") })
        FilterChip(selected = filter.canyonOnly, onClick = { onChange { it.copy(canyonOnly = !it.canyonOnly) } },
            label = { Text("Canyons") })
    }
}
