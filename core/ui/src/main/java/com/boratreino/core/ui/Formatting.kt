package com.boratreino.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.SetSummary
import java.text.NumberFormat
import java.util.Locale

/** A weight without trailing zeros: 40.0 -> "40", 42.5 -> "42.5" (decimal separator follows [locale]). */
fun formatKg(weightKg: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(weightKg)

/** "40 kg × 10". */
@Composable
fun setText(weightKg: Double, reps: Int): String =
    stringResource(R.string.set_text, formatKg(weightKg), reps)

/** "3 × 10 at 40 kg", "40 kg: 10, 10, 8" or "40 kg × 10, 42.5 kg × 8". */
@Composable
fun setSummaryText(summary: SetSummary): String {
    val separator = stringResource(R.string.set_summary_separator)
    return when (summary) {
        is SetSummary.Uniform ->
            stringResource(R.string.set_summary_uniform, summary.count, summary.reps, formatKg(summary.weightKg))
        is SetSummary.SameWeight ->
            stringResource(R.string.set_summary_same_weight, formatKg(summary.weightKg), summary.reps.joinToString(separator))
        is SetSummary.Mixed ->
            summary.sets.map { setText(it.weightKg, it.reps) }.joinToString(separator)
        is SetSummary.Cardio -> cardioText(summary.minutes, summary.calories, summary.distanceM)
    }
}

/** "20 min · 150 kcal · 3.2 km"; a part that is 0 (not logged) is left out, and nothing logged reads "-". */
@Composable
fun cardioText(minutes: Int, calories: Int, distanceM: Int): String {
    val parts = buildList {
        if (minutes > 0) add(stringResource(R.string.cardio_minutes, minutes))
        if (calories > 0) add(stringResource(R.string.cardio_calories, calories))
        if (distanceM > 0) add(stringResource(R.string.cardio_km, formatKg(distanceM / 1000.0)))
    }
    return if (parts.isEmpty()) "-" else parts.joinToString(" · ")
}

/** The group's name for the UI: Legs, Chest, Back, Shoulders, Arms, Core, Cardio. */
@Composable
fun MuscleGroup.label(): String = stringResource(
    when (this) {
        MuscleGroup.LEGS -> R.string.muscle_group_legs
        MuscleGroup.CHEST -> R.string.muscle_group_chest
        MuscleGroup.BACK -> R.string.muscle_group_back
        MuscleGroup.SHOULDERS -> R.string.muscle_group_shoulders
        MuscleGroup.ARMS -> R.string.muscle_group_arms
        MuscleGroup.CORE -> R.string.muscle_group_core
        MuscleGroup.CARDIO -> R.string.muscle_group_cardio
    },
)
