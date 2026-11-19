package de.munichhenge.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.munichhenge.app.R
import de.munichhenge.app.model.Presentation
import de.munichhenge.engine.Mode

/** Half sun on the horizon with an arrow up (sunrise, yellow) or down (sunset, red-orange). */
@Composable
fun ModeIcon(mode: Mode, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(
        painterResource(if (mode == Mode.SUNSET) R.drawable.ic_sunset else R.drawable.ic_sunrise),
        contentDescription = Presentation.modeWord(mode),
        tint = Color.Unspecified,
        modifier = modifier.size(size),
    )
}
