package de.munichhenge.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.LinkTarget
import de.munichhenge.engine.Links
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Sightline
import java.time.Instant

@Composable
fun GradeBadge(grade: Grade) {
    val (bg, fg) = when (grade) {
        Grade.PERFECT -> GradeColors.perfect to GradeColors.onPerfect
        Grade.GOOD -> GradeColors.good to GradeColors.onGood
        Grade.NEAR -> GradeColors.near to GradeColors.onNear
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp)) {
        Text(
            Presentation.gradeWord(grade),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SmallBadge(badge: Presentation.Badge) {
    val faint = badge.kind == Presentation.BadgeKind.CONFIDENCE
    Surface(
        color = if (faint) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(badge.text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * One event (PLAN-links-and-ui.md 2.4): name and grade; the best moment large with its window
 * and a countdown; where to stand and which way to look; small facts; the cloud hint.
 * Tap opens the sightline, long-press or the ⋮ button offers reminder, Street View and walking.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EventCard(
    vm: AppViewModel,
    event: HengeEvent,
    sightline: Sightline?,
    now: Instant,
    onOpen: () -> Unit,
    showDate: Boolean = false,
    dimmed: Boolean = false,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val cloud = rememberCloudHint(vm, event)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .alpha(if (dimmed) 0.55f else 1f),
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = 14.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeIcon(event.mode, size = 24.dp)
                Text(
                    Presentation.title(event, sightline),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                GradeBadge(event.grade)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "more actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Remind me") }, onClick = {
                            menu = false
                            val lead = vm.settings.value?.leadMinutes ?: 60
                            val msg = if (vm.addReminder(event)) {
                                "Reminder $lead min before ${Presentation.localTime(event.windowStart)}, ${Presentation.dateLabel(event.date)}"
                            } else "That moment has already passed"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        })
                        if (sightline != null) {
                            val target = if (sightline.openHorizon) LinkTarget.forOpenHorizon(sightline, event.bearing)
                            else LinkTarget.forDirection(sightline, event.viewFrom)
                            DropdownMenuItem(text = { Text("Street View") }, onClick = {
                                menu = false
                                LinkLauncher.open(context, Links.streetView(target))
                            })
                            DropdownMenuItem(text = { Text("Walk there") }, onClick = {
                                menu = false
                                LinkLauncher.open(context, Links.walk(target.point))
                            })
                        }
                        LocalShowOnMap.current?.let { show ->
                            DropdownMenuItem(text = { Text("Show on map") }, onClick = {
                                menu = false
                                show(MapFocus(event.sightlineId, event))
                            })
                        }
                        DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onOpen() })
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 14.dp)) {
                Text(Presentation.bigTime(event), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 3.dp)) {
                    if (showDate) Text(Presentation.dateLabel(event.date), style = MaterialTheme.typography.labelMedium)
                    Text("${Presentation.windowLabel(event)} · ${Presentation.modeStyleLine(event)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)) {
                    Presentation.countdown(event, now)?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold)
                    }
                    cloud?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                }
            }
            val showOnMap = LocalShowOnMap.current
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).rotate(event.bearing.toFloat()),
                    tint = if (event.mode == Mode.SUNSET) ModeColors.sunset else ModeColors.sunrise,
                )
                Text(
                    if (event.openHorizon) Presentation.lookLabel(event)
                    else "${Presentation.lookLabel(event)} · from the ${Presentation.standLabel(event)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                if (showOnMap != null) {
                    IconButton(onClick = { showOnMap(MapFocus(event.sightlineId, event)) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Map, contentDescription = "Show on map", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val badges = Presentation.badges(event, sightline)
            if (badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp, end = 14.dp),
                ) { badges.forEach { SmallBadge(it) } }
            }
        }
    }
}
