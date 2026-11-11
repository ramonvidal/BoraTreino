package com.boratreino.feature.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.boratreino.core.ui.theme.Sunny

private const val TONE_COUNT = 4

/**
 * One of four muted tones for a routine (Ok green, deep yellow, muted grey, ink), all from the Sunny tokens; never red.
 * The grid itself does not use them: trained days are always success-green and planned ones dashed outlines, whatever the routine.
 */
@Composable
internal fun routineTone(routineId: Long?): Color {
    if (routineId == null) return MaterialTheme.colorScheme.secondary
    return when (Math.floorMod(routineId, TONE_COUNT.toLong()).toInt()) {
        0 -> MaterialTheme.colorScheme.secondary
        1 -> Sunny.BrandDeep
        2 -> Sunny.Muted
        else -> Sunny.Ink
    }
}
