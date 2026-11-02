package com.boratreino.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.SetSummary

/** "40 kg × 10"; a bodyweight exercise with no added weight reads "10 reps". */
@Composable
fun draftText(machine: Machine, weightKg: Double, reps: Int): String =
    if (machine.kind == MachineKind.BODYWEIGHT_REPS && weightKg <= 0.0) {
        LocalContext.current.resources.getQuantityString(R.plurals.reps_only, reps, reps)
    } else {
        setText(weightKg, reps)
    }

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
