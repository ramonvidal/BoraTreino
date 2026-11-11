package com.boratreino.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.ui.R as CoreUiR
import kotlinx.coroutines.withTimeoutOrNull

private const val FIRST_REPEAT_DELAY_MS = 400L
private const val SLOW_INTERVAL_MS = 150L
private const val FAST_INTERVAL_MS = 70L
private const val REPEATS_BEFORE_FAST = 12
private const val MIN_TICK_GAP_MS = 100L

/**
 * Press = one step at once; held for 400 ms = a step every 150 ms, and after 12 repeats every
 * 70 ms; release stops it. Each step gives a light haptic tick, but never more often than every
 * 100 ms. TalkBack's click action gives a single step.
 */
@Composable
internal fun Modifier.holdRepeat(contentDescription: String, onStep: () -> Unit): Modifier {
    val currentOnStep by rememberUpdatedState(onStep)
    val haptic = LocalHapticFeedback.current
    val lastTickAt = remember { LongArray(1) }
    fun step() {
        val now = System.currentTimeMillis()
        if (now - lastTickAt[0] >= MIN_TICK_GAP_MS) {
            lastTickAt[0] = now
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        currentOnStep()
    }
    return this
        .semantics {
            role = Role.Button
            this.contentDescription = contentDescription
            onClick { step(); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                step()
                var released = withTimeoutOrNull(FIRST_REPEAT_DELAY_MS) { awaitUp() }
                var repeats = 0
                while (released == null) {
                    step()
                    repeats++
                    val gap = if (repeats >= REPEATS_BEFORE_FAST) FAST_INTERVAL_MS else SLOW_INTERVAL_MS
                    released = withTimeoutOrNull(gap) { awaitUp() }
                }
            }
        }
}

/** Waits until every pointer is up or the gesture is taken over; returns when it ends. */
private suspend fun AwaitPointerEventScope.awaitUp(): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed } || event.changes.none { it.pressed }) return true
    }
}

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
