package com.boratreino.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
fun Modifier.holdRepeat(contentDescription: String, onStep: () -> Unit): Modifier {
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
