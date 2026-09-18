package de.munichhenge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.munichhenge.app.model.Presentation

/** Best events in the next 14 days across the city, grouped by day. */
@Composable
fun UpcomingScreen(vm: AppViewModel, onOpenSightline: (String) -> Unit) {
    val grouped by vm.upcoming.collectAsStateWithLifecycle()
    val g = grouped
    when {
        g == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        g.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing aligned in the next ${AppViewModel.UPCOMING_DAYS} days.")
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            for ((date, events) in g) {
                item(key = "h$date") {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            Presentation.dateLabel(date),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                items(events, key = { "$date" + it.sightlineId + it.viewFrom + it.mode }) { e ->
                    EventRow(e, vm.data.sightline(e.sightlineId), cloudHint = rememberCloudHint(vm, e),
                        onClick = { onOpenSightline(e.sightlineId) })
                    HorizontalDivider()
                }
            }
        }
    }
}
