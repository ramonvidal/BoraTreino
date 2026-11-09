package com.boratreino.core.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The ONE selectable chip of the app: selected = filled green (primary) with onPrimary text,
 * unselected = white surface with a 1 dp outline and onSurface text. At least 40 dp tall
 * (48 dp when [large]), SemiBold label, a leading check when selected, content description
 * taken from the label.
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
                style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surface,
            labelColor = scheme.onSurface,
            iconColor = scheme.onSurface,
            disabledContainerColor = scheme.surface,
            disabledLabelColor = disabledContent,
            disabledLeadingIconColor = disabledContent,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary,
            selectedLeadingIconColor = scheme.onPrimary,
            disabledSelectedContainerColor = disabledSelected,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = scheme.outline,
            selectedBorderColor = Color.Transparent,
            disabledBorderColor = scheme.onSurface.copy(alpha = 0.12f),
            disabledSelectedBorderColor = Color.Transparent,
            borderWidth = 1.dp,
            selectedBorderWidth = 0.dp,
        ),
        modifier = modifier
            .heightIn(min = if (large) 48.dp else 40.dp)
            .semantics { contentDescription = label },
    )
}
