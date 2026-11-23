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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.ui.theme.Figtree
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.withTimeoutOrNull

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L
private val StepperButtonSize = 64.dp
private val CompactStepperButtonSize = 48.dp
private const val COMPACT_VALUE_SP = 24f
private const val COMPACT_MIN_VALUE_SP = 12f

/**
 * A labelled number with big minus and plus buttons (at least 56 dp; here 64 dp). Holding a
 * button repeats it after 400 ms, about every 120 ms. Tapping the value calls [onValueClick]
 * (for typing it in a number pad).
 *
 * [compact] is for narrow slots such as the half-width cells of the cardio 2 x 2 grid: 48 dp
 * buttons, a 24 sp value that never wraps (it shrinks in 1 sp steps until it fits), tighter gaps.
 */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val buttonSize = if (compact) CompactStepperButtonSize else StepperButtonSize
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        ) {
            StepperButton(
                icon = R.drawable.ic_remove,
                size = buttonSize,
                description = stringResource(R.string.stepper_minus, label),
                onStep = onMinus,
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = buttonSize)
                    .clickable(onClick = onValueClick),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(Modifier.defaultMinSize(minHeight = buttonSize), contentAlignment = Alignment.Center) {
                    if (compact) {
                        CompactValue(value)
                    } else {
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
            }
            StepperButton(
                icon = R.drawable.ic_add,
                size = buttonSize,
                description = stringResource(R.string.stepper_plus, label),
                onStep = onPlus,
            )
        }
    }
}

@Composable
private fun StepperButton(@DrawableRes icon: Int, size: Dp, description: String, onStep: () -> Unit) {
    val currentOnStep by rememberUpdatedState(onStep)
    Surface(
        modifier = Modifier
            .size(size)
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
            modifier = Modifier.size(size).padding(if (size < StepperButtonSize) 12.dp else 20.dp),
        )
    }
}

/**
 * The value of a compact [Stepper]: one line, never wrapped or ellipsized. Starts at 24 sp and
 * drops 1 sp at a time (down to 12 sp) while the text overflows its box, so "1000" or "17.3"
 * fit a half-width column even at large font scales.
 */
@Composable
private fun CompactValue(value: String) {
    var sizeSp by remember(value) { mutableFloatStateOf(COMPACT_VALUE_SP) }
    Text(
        value,
        style = TextStyle(
            fontFamily = Figtree,
            fontWeight = FontWeight.SemiBold,
            fontSize = sizeSp.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.didOverflowWidth && sizeSp > COMPACT_MIN_VALUE_SP) sizeSp -= 1f },
    )
}

/** Waits until every pointer is up (true), or the gesture is taken over, e.g. by scrolling (false). */
private suspend fun AwaitPointerEventScope.awaitRelease(): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed }) return false
        if (event.changes.none { it.pressed }) return true
    }
}
