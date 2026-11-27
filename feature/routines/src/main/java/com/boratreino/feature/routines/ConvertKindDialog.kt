package com.boratreino.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.ConversionFactor
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.KindConversionRules
import com.boratreino.core.ui.NumberPadDialog
import com.boratreino.core.ui.Stepper
import com.boratreino.core.ui.metresText
import com.boratreino.core.ui.secondsText
import com.boratreino.core.ui.setLabel
import com.boratreino.core.ui.R as CoreUiR

/**
 * The one-off question before logged sets change kind, drawn from [dialog] alone; nothing when it is
 * null. Setup, not training, so a dialog is fine here. It says what will happen to the sets, asks for
 * the factor when numbers have to be converted, and previews the latest set before and after.
 */
@Composable
internal fun ConvertKindDialog(
    dialog: ConvertDialog?,
    onFactorStep: (Boolean) -> Unit,
    onFactorSet: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (dialog == null) return
    when (val conversion = dialog.conversion) {
        is KindConversion.NeedsFactor -> FactorDialog(dialog, conversion, onFactorStep, onFactorSet, onConfirm, onDismiss)
        else -> {
            val resources = LocalContext.current.resources
            val body = resources.getQuantityString(R.plurals.convert_body_unchanged, dialog.setCount, dialog.setCount)
            val noUndo = stringResource(R.string.convert_no_undo)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.convert_title_unchanged, dialog.after.kind.editorLabel())) },
                text = { Text(body + "\n\n" + noUndo) },
                confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.convert_change)) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun FactorDialog(
    dialog: ConvertDialog,
    conversion: KindConversion.NeedsFactor,
    onFactorStep: (Boolean) -> Unit,
    onFactorSet: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var padOpen by remember { mutableStateOf(false) }
    val seconds = conversion.factor == ConversionFactor.SECONDS_PER_REP
    val factor = dialog.factor
    val unitText = when {
        factor == null -> ""
        seconds -> secondsText(factor)
        else -> metresText(factor)
    }
    val valueText = if (factor == null) stringResource(R.string.convert_value_empty) else unitText
    val title = stringResource(
        when {
            seconds && dialog.fromReps -> R.string.convert_title_seconds_per_rep
            seconds -> R.string.convert_title_rep_seconds
            dialog.fromReps -> R.string.convert_title_metres_per_rep
            else -> R.string.convert_title_rep_metres
        },
    )
    val stepperLabel = stringResource(if (seconds) R.string.convert_stepper_seconds else R.string.convert_stepper_metres)
    val resources = LocalContext.current.resources
    val summary = when {
        factor == null -> ""
        dialog.fromReps -> resources.getQuantityString(R.plurals.convert_to_timed, dialog.setCount, dialog.setCount, unitText)
        else -> resources.getQuantityString(R.plurals.convert_from_timed, dialog.setCount, dialog.setCount, unitText)
    }
    // The latest working set, before and after, through the same code that will convert it.
    val before = setLabel(dialog.before, conversion.sample)
    val preview = if (factor == null) {
        stringResource(R.string.convert_preview_pending, before)
    } else {
        val converted = KindConversionRules.convertSet(conversion.sample, dialog.before.kind, dialog.after.kind, factor)
        stringResource(R.string.convert_preview, before, setLabel(dialog.after, converted))
    }
    val noUndo = stringResource(R.string.convert_no_undo)
    val confirmText = resources.getQuantityString(R.plurals.convert_confirm, dialog.setCount, dialog.setCount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper(
                    label = stepperLabel,
                    value = valueText,
                    onMinus = { onFactorStep(false) },
                    onPlus = { onFactorStep(true) },
                    onValueClick = { padOpen = true },
                )
                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.convert_preview_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(preview, style = MaterialTheme.typography.bodyLarge)
                Text(
                    noUndo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.canConfirm) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) } },
    )
    if (padOpen) {
        NumberPadDialog(
            title = stepperLabel,
            initial = factor?.toString().orEmpty(),
            allowDecimal = false,
            onConfirm = { text ->
                padOpen = false
                onFactorSet(text.toIntOrNull() ?: 0)
            },
            onDismiss = { padOpen = false },
        )
    }
}
