package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val MAX_LENGTH = 7

/**
 * A small number pad for typing a value in (first-time setup; the steppers stay the main path).
 * [initial] and the string given to [onConfirm] use "." as the decimal separator. The first key
 * pressed replaces [initial]; later ones add to it.
 */
@Composable
fun NumberPadDialog(
    title: String,
    initial: String,
    allowDecimal: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var untouched by remember { mutableStateOf(true) }

    fun press(key: Char) {
        val base = if (untouched) "" else text
        untouched = false
        text = when {
            base.length >= MAX_LENGTH -> base
            key == '.' -> if (!allowDecimal || '.' in base) base else (base.ifEmpty { "0" } + ".")
            base == "0" -> key.toString()
            else -> base + key
        }
    }

    fun backspace() {
        val base = if (untouched) initial else text
        untouched = false
        text = base.dropLast(1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text.ifEmpty { "0" },
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displaySmall,
                )
                val decimal = stringResource(R.string.number_pad_decimal_separator)
                val rows = listOf("123", "456", "789")
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key -> PadKey(key.toString(), Modifier.weight(1f)) { press(key) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowDecimal) {
                        PadKey(decimal, Modifier.weight(1f)) { press('.') }
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    PadKey("0", Modifier.weight(1f)) { press('0') }
                    PadKey(
                        "<",
                        Modifier.weight(1f),
                        description = stringResource(R.string.number_pad_delete_digit),
                    ) { backspace() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trimEnd('.').ifEmpty { "0" }) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PadKey(
    label: String,
    modifier: Modifier,
    description: String? = null,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
