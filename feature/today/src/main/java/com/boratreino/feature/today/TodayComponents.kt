package com.boratreino.feature.today

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Machine
import com.boratreino.core.ui.MachineThumb

/** "3 machines · 9 sets", with the right singular forms. */
internal fun Resources.machinesAndSets(machines: Int, sets: Int, records: Int = 0): String {
    val base = getString(
        R.string.today_machines_and_sets,
        getQuantityString(R.plurals.today_machines, machines, machines),
        getQuantityString(R.plurals.today_sets, sets, sets),
    )
    return if (records > 0) {
        getString(R.string.today_summary_with_records, base, getQuantityString(R.plurals.today_records, records, records))
    } else {
        base
    }
}

/** A tappable white card with a title and an optional grey line, at least 64 dp tall. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MachineRow(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    /** A long press; when set, the whole card handles tap and long press itself. */
    onLongClick: (() -> Unit)? = null,
) {
    val border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
    if (onLongClick == null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), border = border, colors = colors, content = content)
    } else {
        Card(
            modifier = modifier.fillMaxWidth().clip(CardDefaults.shape)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
            border = border,
            colors = colors,
            content = content,
        )
    }
}

/** A small grey heading above a group of rows. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The row picture for a stored machine; the row's own tap still applies. */
@Composable
internal fun RowThumb(machine: Machine) {
    MachineThumb(machine, size = 44.dp)
}
