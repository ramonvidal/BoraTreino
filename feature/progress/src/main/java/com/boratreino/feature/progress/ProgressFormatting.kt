package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.ui.cardioText
import com.boratreino.core.ui.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.boratreino.core.ui.R as CoreUiR

/** "2 Nov", or "2 Nov 2025" when [date] is not in [today]'s year. */
internal fun formatShortDate(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** "2 Nov 2026". */
internal fun formatFullDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date)

/** "+10" for a rise, "-5" for a fall, "0" for none. */
internal fun signed(delta: Double): String =
    if (delta > 0) "+" + formatKg(delta) else formatKg(delta)

/** "125 kg" or "12 reps". */
@Composable
internal fun valueText(metric: ProgressMetric, value: Double): String = when (metric) {
    ProgressMetric.WEIGHT -> stringResource(CoreUiR.string.weight_kg, formatKg(value))
    ProgressMetric.REPS -> value.roundToInt().let { pluralStringResource(CoreUiR.plurals.reps_only, it, it) }
    ProgressMetric.MINUTES -> cardioText(value.roundToInt(), 0, 0)
}

/** "+10 kg", "-5 kg" or "+3 reps". */
@Composable
internal fun deltaText(metric: ProgressMetric, delta: Double): String = when (metric) {
    ProgressMetric.WEIGHT -> stringResource(CoreUiR.string.weight_kg, signed(delta))
    ProgressMetric.REPS -> pluralStringResource(R.plurals.progress_delta_reps, abs(delta).roundToInt(), signed(delta))
    ProgressMetric.MINUTES -> stringResource(R.string.progress_delta_minutes, signed(delta))
}

/** "125 kg × 8" (or "12 reps" when charting reps): the set that gave the session's value. */
@Composable
internal fun topText(metric: ProgressMetric, point: SessionPoint): String = when (metric) {
    ProgressMetric.WEIGHT -> {
        val weight = formatKg(point.value)
        val reps = point.topSet.reps
        if (reps == null) stringResource(CoreUiR.string.weight_kg, weight) else stringResource(CoreUiR.string.set_text, weight, reps)
    }
    ProgressMetric.REPS, ProgressMetric.MINUTES -> valueText(metric, point.value)
}

/** "3 sets". */
@Composable
internal fun setsText(point: SessionPoint): String =
    pluralStringResource(R.plurals.progress_sets, point.setCount, point.setCount)
