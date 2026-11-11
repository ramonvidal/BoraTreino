package com.boratreino.core.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The ONE selectable chip of the app: selected = filled yellow (primaryContainer) with onPrimaryContainer text
 * and no check (the fill already says "selected"), unselected = white surface with no border and onSurface text.
 * Pill-shaped, at least 40 dp tall (48 dp when [large]), labelLarge, content description taken from the label.
 */
@Composable
fun BoraChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val disabledContent = scheme.onSurface.copy(alpha = 0.38f)
    val disabledSelected = scheme.onSurface.copy(alpha = 0.12f)
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceContainerLowest,
            labelColor = scheme.onSurface,
            iconColor = scheme.onSurface,
            disabledContainerColor = scheme.surfaceContainerLowest,
            disabledLabelColor = disabledContent,
            disabledLeadingIconColor = disabledContent,
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
            selectedLeadingIconColor = scheme.onPrimaryContainer,
            disabledSelectedContainerColor = disabledSelected,
        ),
        border = null,
        modifier = modifier
            .heightIn(min = if (large) 48.dp else 40.dp)
            .semantics { contentDescription = label },
    )
}
