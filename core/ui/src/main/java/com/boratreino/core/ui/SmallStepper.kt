package com.boratreino.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A compact minus / value / plus for the routine editor's targets (48 dp touch targets). */
@Composable
fun SmallStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.stepper_minus, contentDescription))
        }
        Text(
            value,
            modifier = Modifier.defaultMinSize(minWidth = 28.dp).semantics { this.contentDescription = contentDescription },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onPlus) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.stepper_plus, contentDescription))
        }
    }
}
