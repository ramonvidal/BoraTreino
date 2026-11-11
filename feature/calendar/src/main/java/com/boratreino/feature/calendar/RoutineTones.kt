package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val TONE_COUNT = 4
private val BlueGrey = Color(0xFF52708A)
private val Amber = Color(0xFF9A6B00)
private val Plum = Color(0xFF7A4B6E)

/** One of four muted tones for a routine (Ok green, blue-grey, amber, plum); never red. */
@Composable
internal fun routineTone(routineId: Long?): Color {
    if (routineId == null) return MaterialTheme.colorScheme.secondary
    return when (Math.floorMod(routineId, TONE_COUNT.toLong()).toInt()) {
        0 -> MaterialTheme.colorScheme.secondary
        1 -> BlueGrey
        2 -> Amber
        else -> Plum
    }
}

/** The trained-day badge: a light wash of the routine's tone with dark text; solid tone when selected. */
@Composable
internal fun TrainedBadge(shortName: String, tone: Color, selected: Boolean, modifier: Modifier = Modifier) {
    val container = if (selected) tone else lerp(tone, Color.White, 0.8f)
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = container, contentColor = content) {
        Box(Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(shortName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
