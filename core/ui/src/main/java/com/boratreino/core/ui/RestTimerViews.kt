package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.model.RestTimerRules

/**
 * The compact rest countdown shown at the top of every screen but the machine screen:
 * time left, a thin bar, +30 s and Skip. State comes in as parameters only.
 */
@Composable
fun RestBanner(
    remainingSeconds: Int,
    totalSeconds: Int,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.rest_banner_title, RestTimerRules.format(remainingSeconds)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onExtend, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                    Text(stringResource(R.string.rest_extend, RestTimerRules.EXTEND_SECONDS))
                }
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                    Text(stringResource(R.string.rest_skip))
                }
            }
            LinearProgressIndicator(
                progress = { RestTimerRules.progress(remainingSeconds, totalSeconds) },
                modifier = Modifier.fillMaxWidth().heightIn(max = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }
    }
}

/** The big rest countdown on the machine screen: a large time, a bar of time left, +30 s and Skip. */
@Composable
fun RestCard(
    remainingSeconds: Int,
    totalSeconds: Int,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.rest_card_title), style = MaterialTheme.typography.titleMedium)
            Text(
                RestTimerRules.format(remainingSeconds),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { RestTimerRules.progress(remainingSeconds, totalSeconds) },
                modifier = Modifier.fillMaxWidth().heightIn(max = 8.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onExtend,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                ) { Text(stringResource(R.string.rest_extend, RestTimerRules.EXTEND_SECONDS)) }
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                ) { Text(stringResource(R.string.rest_skip)) }
            }
        }
    }
}
