package com.boratreino.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.ui.theme.Figtree
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.withTimeoutOrNull

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L
private val StepperButtonSize = 64.dp

/**
 * A labelled number with big minus and plus buttons (at least 56 dp; here 64 dp). Holding a
 * button repeats it after 400 ms, about every 120 ms. Tapping the value calls [onValueClick]
 * (for typing it in a number pad).
 */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperButton(
                icon = R.drawable.ic_remove,
                description = stringResource(R.string.stepper_minus, label),
                onStep = onMinus,
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = StepperButtonSize)
                    .clickable(onClick = onValueClick),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(Modifier.defaultMinSize(minHeight = StepperButtonSize), contentAlignment = Alignment.Center) {
                    Text(
                        value,
                        style = TextStyle(
                            fontFamily = Figtree,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            StepperButton(
                icon = R.drawable.ic_add,
                description = stringResource(R.string.stepper_plus, label),
                onStep = onPlus,
            )
        }
    }
}

@Composable
private fun StepperButton(@DrawableRes icon: Int, description: String, onStep: () -> Unit) {
    val currentOnStep by rememberUpdatedState(onStep)
    Surface(
        modifier = Modifier
            .size(StepperButtonSize)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { currentOnStep(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    // Lifted before the delay: one step. Held: step now, then keep stepping.
                    when (withTimeoutOrNull(REPEAT_DELAY_MS) { awaitRelease() }) {
                        true -> currentOnStep()
                        false -> Unit // taken over by scrolling: no step
                        null -> do {
                            currentOnStep()
                        } while (withTimeoutOrNull(REPEAT_INTERVAL_MS) { awaitRelease() } == null)
                    }
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(StepperButtonSize).padding(20.dp),
        )
    }
}

/** Waits until every pointer is up (true), or the gesture is taken over, e.g. by scrolling (false). */
private suspend fun AwaitPointerEventScope.awaitRelease(): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed }) return false
        if (event.changes.none { it.pressed }) return true
    }
}
