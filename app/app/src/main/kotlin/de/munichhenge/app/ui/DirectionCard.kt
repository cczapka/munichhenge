package de.munichhenge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.munichhenge.engine.LinkTarget
import de.munichhenge.engine.Links

/**
 * One viewing direction: where to stand, which way to look, and the three "take me there"
 * actions (PLAN-links-and-ui.md 1.3). `highlighted` marks the direction of the event the
 * user came from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectionCard(title: String, detail: String, target: LinkTarget, highlighted: Boolean) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = "looking ${Links.heading(target.headingDeg)} degrees",
                    modifier = Modifier.size(28.dp).rotate(target.headingDeg.toFloat()),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp))
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilledTonalButton(onClick = { LinkLauncher.open(context, Links.streetView(target)) }) { Text("Street View") }
                TextButton(onClick = { LinkLauncher.open(context, Links.walk(target.point)) }) { Text("Walk there") }
                TextButton(onClick = {
                    LinkLauncher.open(context, Links.geo(target.point, target.label), chooserTitle = "Open in")
                }) { Text("Open in maps") }
            }
        }
    }
}

/** The honest caveat under the cards (PLAN-links-and-ui.md 1.5). */
@Composable
fun StreetViewHint() {
    Text(
        "Street View shows the far end, not the sun. Imagery can be years old and from another season, " +
            "and the camera stands on the road about 2.5 m up.",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
