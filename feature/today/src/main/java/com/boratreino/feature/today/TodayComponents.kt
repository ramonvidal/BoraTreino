package com.boratreino.feature.today

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.setSummaryText
import com.boratreino.core.ui.setText
import kotlinx.coroutines.withTimeoutOrNull

/** How long an Undo snackbar stays. */
private const val UNDO_MILLIS = 5_000L

/** Shows [message] with an Undo action for 5 s, replacing any snackbar showing; true when Undo was tapped. */
internal suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean {
    currentSnackbarData?.dismiss()
    val result = withTimeoutOrNull(UNDO_MILLIS) {
        showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    }
    return result == SnackbarResult.ActionPerformed
}

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
@Composable
internal fun MachineRow(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

/** "40 kg × 10"; a bodyweight exercise with no added weight reads "10 reps". */
@Composable
internal fun draftText(machine: Machine, weightKg: Double, reps: Int): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && weightKg <= 0.0) {
        LocalContext.current.resources.getQuantityString(R.plurals.today_reps_only, reps, reps)
    } else {
        setText(weightKg, reps)
    }

/** "3 × 10 at 40 kg" and its variants; bodyweight sets with no added weight drop the weight. */
@Composable
internal fun summaryLabel(machine: Machine, summary: SetSummary): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && summary is SetSummary.Uniform && summary.weightKg <= 0.0) {
        stringResource(R.string.today_uniform_no_weight, summary.count, summary.reps)
    } else {
        setSummaryText(summary)
    }

/** The number shown in a weight stepper. */
internal fun weightValue(weightKg: Double): String = formatKg(weightKg)
