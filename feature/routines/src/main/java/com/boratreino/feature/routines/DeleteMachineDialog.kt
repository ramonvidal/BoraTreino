package com.boratreino.feature.routines

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.boratreino.core.ui.R as CoreUiR

/**
 * The two questions of the delete flow, drawn from [dialog] alone; nothing when it is null.
 * Setup, not training, so a dialog is fine here.
 */
@Composable
internal fun DeleteMachineDialog(
    dialog: DeleteDialog?,
    onConfirm: () -> Unit,
    onArchiveInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        is DeleteDialog.Confirm -> {
            val name = dialog.machine.name
            val routines = dialog.routineNames.joinToString(", ")
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.delete_title, name)) },
                text = {
                    if (routines.isNotEmpty()) {
                        Text(stringResource(R.string.delete_also_removed, routines))
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
                },
            )
        }
        is DeleteDialog.ArchiveInstead -> {
            val name = dialog.machine.name
            val history = LocalContext.current.resources
                .getQuantityString(R.plurals.delete_has_sets, dialog.setCount, name, dialog.setCount)
            val advice = stringResource(R.string.delete_archive_advice)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.delete_title, name)) },
                text = { Text(history + "\n\n" + advice) },
                confirmButton = {
                    TextButton(onClick = onArchiveInstead) { Text(stringResource(R.string.delete_archive)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
                },
            )
        }
    }
}
