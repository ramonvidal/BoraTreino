package com.boratreino.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.Record
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Suggestion
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** "Mon".."Sun" in the phone's language. */
@Composable
fun DayOfWeek.shortLabel(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "Try 125 kg". */
@Composable
fun suggestionText(s: Suggestion): String = stringResource(R.string.suggestion_try, formatKg(s.weightKg))

/** "New record: 125 kg" or "New record: 12 reps at 120 kg". */
@Composable
fun recordText(r: Record): String = when (r) {
    is Record.HeaviestWeight -> stringResource(R.string.record_heaviest, formatKg(r.weightKg))
    is Record.MostReps -> stringResource(R.string.record_most_reps, r.reps, formatKg(r.weightKg))
}

/** "3 × 8–12" (or "3 × 10" when the range is a single number). */
@Composable
fun targetText(item: RoutineItem): String =
    if (item.repsMin == item.repsMax) {
        stringResource(R.string.target_text_fixed, item.targetSets, item.repsMin)
    } else {
        stringResource(R.string.target_text, item.targetSets, item.repsMin, item.repsMax)
    }
