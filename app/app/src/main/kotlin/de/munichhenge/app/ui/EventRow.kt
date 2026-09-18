package de.munichhenge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Grade
import de.munichhenge.engine.HengeEvent
import de.munichhenge.engine.Mode
import de.munichhenge.engine.Sightline

@Composable
fun GradeBadge(grade: Grade) {
    val color = when (grade) {
        Grade.PERFECT -> GradeColors.perfect
        Grade.GOOD -> GradeColors.good
        Grade.NEAR -> GradeColors.near
    }
    Surface(color = color, contentColor = Color.White, shape = RoundedCornerShape(6.dp)) {
        Text(
            Presentation.gradeWord(grade),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** One event: name, t_full local time, grade badge, standing instruction, spot count. */
@Composable
fun EventRow(event: HengeEvent, sightline: Sightline?, showDate: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (event.mode == Mode.SUNSET) Icons.Filled.WbTwilight else Icons.Filled.WbSunny,
            contentDescription = Presentation.modeWord(event.mode),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(Presentation.title(event, sightline), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val time = (if (showDate) Presentation.dateLabel(event.date) + " · " else "") +
                Presentation.localTime(event.tFull) + " " + Presentation.modeWord(event.mode)
            Text(time, style = MaterialTheme.typography.bodyMedium)
            Text(
                Presentation.instruction(event) + " · " + Presentation.spotsLabel(event.poiIds.size) +
                    if (sightline?.canyon == true) " · canyon" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GradeBadge(event.grade)
    }
}
