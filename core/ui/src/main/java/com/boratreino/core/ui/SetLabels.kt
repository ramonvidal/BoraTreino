package com.boratreino.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.LoggingRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetMeasure
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.WorkoutSet

/** "40 kg × 10"; a bodyweight exercise with no added weight reads "10 reps". */
@Composable
fun draftText(machine: Machine, weightKg: Double, reps: Int): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && weightKg <= 0.0) {
        LocalContext.current.resources.getQuantityString(R.plurals.reps_only, reps, reps)
    } else {
        setText(weightKg, reps)
    }

/**
 * One set as text for [machine]'s kind: "40 kg × 10", "10 reps", "47.5 kg · 30 s" (0 kg -> "30 s"),
 * "40 kg · 20 m" (0 kg -> "20 m"), "45 s" for time exercises.
 */
@Composable
fun draftText(machine: Machine, draft: SetDraft): String = when (machine.kind.measure) {
    SetMeasure.SECONDS -> measuredDraftText(SetMeasure.SECONDS, draft)
    SetMeasure.METRES -> measuredDraftText(SetMeasure.METRES, draft)
    else -> draftText(machine, draft.weightKg, draft.reps)
}

/** [set] as text for [machine]'s kind. */
@Composable
fun setLabel(machine: Machine, set: WorkoutSet): String =
    draftText(machine, LoggingRules.draftOf(set, machine.kind))

/** "45 s" (always seconds). */
@Composable
fun secondsText(seconds: Int): String = stringResource(R.string.seconds_text, seconds)

/** "20 m". */
@Composable
fun metresText(metres: Int): String = stringResource(R.string.metres_text, metres)

/** The amount stepper's label: "Reps", "Time (s)" or "Distance (m)". */
@Composable
fun amountLabel(machine: Machine): String = stringResource(
    when (machine.kind.measure) {
        SetMeasure.SECONDS -> R.string.amount_label_seconds
        SetMeasure.METRES -> R.string.amount_label_metres
        else -> R.string.amount_label_reps
    },
)

/** "47.5 kg · 30 s" / "30 s" (no load); metres likewise. Used by [draftText] and the Measured summary. */
@Composable
internal fun measuredDraftText(measure: SetMeasure, draft: SetDraft): String {
    val seconds = measure == SetMeasure.SECONDS
    val amount = if (seconds) draft.seconds else draft.distanceM
    return when {
        draft.weightKg > NO_LOAD_KG && seconds -> stringResource(R.string.set_text_seconds, formatKg(draft.weightKg), amount)
        draft.weightKg > NO_LOAD_KG -> stringResource(R.string.set_text_metres, formatKg(draft.weightKg), amount)
        seconds -> secondsText(amount)
        else -> metresText(amount)
    }
}

private const val NO_LOAD_KG = 0.001

/** "3 × 10 at 40 kg" and its variants; bodyweight sets with no added weight drop the weight. */
@Composable
fun summaryLabel(machine: Machine, summary: SetSummary): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && summary is SetSummary.Uniform && summary.weightKg <= 0.0) {
        stringResource(R.string.set_summary_uniform_no_weight, summary.count, summary.reps)
    } else {
        setSummaryText(summary)
    }

/** The label of the weight stepper: "Weight (kg)", or "Added weight (kg)" for bodyweight machines. */
@Composable
fun weightLabel(machine: Machine): String = stringResource(
    if (machine.kind == MachineKind.BODYWEIGHT_REPS) R.string.added_weight_label else R.string.weight_label,
)
