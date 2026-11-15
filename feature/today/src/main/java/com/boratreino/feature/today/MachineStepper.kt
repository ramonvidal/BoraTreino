package com.boratreino.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.ui.holdRepeat
import com.boratreino.core.ui.R as CoreUiR

/** Label, a LARGE tappable number (tap = type it), and the - and + buttons under it. */
@Composable
internal fun BigStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(onClickLabel = stringResource(R.string.machine_type_value), onClick = onValueClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigStepButton(CoreUiR.drawable.ic_remove, stringResource(CoreUiR.string.stepper_minus, label), onMinus, Modifier.weight(1f))
            BigStepButton(CoreUiR.drawable.ic_add, stringResource(CoreUiR.string.stepper_plus, label), onPlus, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigStepButton(icon: Int, description: String, onStep: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp).holdRepeat(description, onStep),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}
