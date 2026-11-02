package com.boratreino.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A small rounded badge with a routine's short name; green-tinted when [selected], grey otherwise. */
@Composable
fun RoutineBadge(shortName: String, selected: Boolean = false, modifier: Modifier = Modifier) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = container, contentColor = content) {
        Box(Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(shortName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
