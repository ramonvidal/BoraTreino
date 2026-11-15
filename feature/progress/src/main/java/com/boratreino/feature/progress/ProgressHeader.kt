package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.R as CoreUiR

/**
 * The one header of the Progress tab: the title with the period pill at its right (hidden when
 * [showPeriod] is false), and one row of page chips. The pill opens the period sheet.
 */
/** Added to a label's length to get its chip weight in the page row. */
private const val CHIP_WEIGHT_PADDING = 4

@Composable
internal fun ProgressHeader(
    page: ProgressPage,
    onPage: (ProgressPage) -> Unit,
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    showPeriod: Boolean,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (showPeriod) PeriodPill(period, onClick = { sheetOpen = true })
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressPage.entries.forEach { option ->
                val label = stringResource(pageLabel(option))
                BoraChip(
                    label = label,
                    selected = option == page,
                    onClick = { onPage(option) },
                    // The chips share the row by label length, so "Exercises" does not wrap next to "Body".
                    modifier = Modifier.weight((label.length + CHIP_WEIGHT_PADDING).toFloat()),
                )
            }
        }
    }
    if (sheetOpen && showPeriod) {
        PeriodSheet(
            selected = period,
            onSelect = {
                onPeriod(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

private fun pageLabel(page: ProgressPage): Int = when (page) {
    ProgressPage.EXERCISES -> R.string.progress_page_machines
    ProgressPage.BODY -> R.string.stats_page_body
    ProgressPage.STRENGTH -> R.string.stats_page_strength
    ProgressPage.HABIT -> R.string.stats_page_habit
}

@Composable
private fun PeriodPill(period: StatsPeriod, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = periodLabel(period)
    val description = stringResource(R.string.stats_period_pill_description, label)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 40.dp).semantics {
            contentDescription = description
            role = Role.Button
        },
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Icon(painterResource(CoreUiR.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSheet(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PeriodSheetContent(selected, onSelect)
    }
}

/** The title and the five periods as large radio rows; the chosen one has a check and bold text. */
@Composable
internal fun PeriodSheetContent(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.stats_period_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        StatsPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = isSelected, onClick = { onSelect(period) }, role = Role.RadioButton)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    periodLabel(period),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Spacer(Modifier.width(24.dp))
                }
            }
        }
    }
}
