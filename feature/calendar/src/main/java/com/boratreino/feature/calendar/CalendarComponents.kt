package com.boratreino.feature.calendar

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.SetSummary
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.setSummaryText
import com.boratreino.core.ui.setText
import java.time.format.DateTimeFormatter
import java.util.Locale
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

/** "6 machines", with the right singular form. */
internal fun Resources.machinesText(count: Int): String = getQuantityString(R.plurals.calendar_machines, count, count)

/** "18 sets", with the right singular form. */
internal fun Resources.setsText(count: Int): String = getQuantityString(R.plurals.calendar_sets, count, count)

/** "Mon 2 Nov". */
internal fun dayTitleFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** "40 kg × 10"; a bodyweight exercise with no added weight reads "10 reps". */
@Composable
internal fun draftText(machine: Machine, weightKg: Double, reps: Int): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && weightKg <= 0.0) {
        LocalContext.current.resources.getQuantityString(R.plurals.calendar_reps_only, reps, reps)
    } else {
        setText(weightKg, reps)
    }

/** "3 × 10 at 40 kg" and its variants; bodyweight sets with no added weight drop the weight. */
@Composable
internal fun summaryLabel(machine: Machine, summary: SetSummary): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && summary is SetSummary.Uniform && summary.weightKg <= 0.0) {
        stringResource(R.string.calendar_uniform_no_weight, summary.count, summary.reps)
    } else {
        setSummaryText(summary)
    }

/** The label of the weight stepper. */
@Composable
internal fun weightLabel(machine: Machine): String = stringResource(
    if (machine.kind == MachineKind.BODYWEIGHT_REPS) R.string.calendar_added_weight else R.string.calendar_weight,
)

/** The number shown in a weight stepper. */
internal fun weightValue(weightKg: Double): String = formatKg(weightKg)
