package de.munichhenge.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sun = Color(0xFFF2B134)
private val Night = Color(0xFF1B2A41)

private val Light = lightColorScheme(primary = Night, secondary = Sun, tertiary = Color(0xFFB85C00))
private val Dark = darkColorScheme(primary = Sun, secondary = Color(0xFFFFD68A), tertiary = Color(0xFFFFB870))

@Composable
fun MunichHengeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

object GradeColors {
    val perfect = Color(0xFF2E7D32)
    val good = Color(0xFFF9A825)
    val near = Color(0xFF9E9E9E)
}
