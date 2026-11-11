package com.boratreino.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The ONE card style: 20 dp rounded, white (or [tint]), no border, no shadow, content padded 16 dp
 * horizontally and 14 dp vertically. With [onClick] the whole card is clickable with a ripple.
 */
@Composable
fun BoraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = tint ?: MaterialTheme.colorScheme.surfaceContainerLowest)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, content = body)
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, content = body)
    }
}
