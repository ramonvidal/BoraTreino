package com.boratreino.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Switch colours: on = green track with a white thumb, off = Line track with a white thumb (no outline). */
@Composable
fun BoraSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.secondary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
    uncheckedBorderColor = Color.Transparent,
)
