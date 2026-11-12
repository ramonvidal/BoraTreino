package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.ui.cardioText
import com.boratreino.core.ui.formatKg
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.text.NumberFormat
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

/** An estimate with up to one decimal, not rounded to plates: "101.3". */
internal fun formatEstimate(value: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(value)

/** "+1.5" for a rise, "-5" for a fall, "0" for none, with up to one decimal. */
internal fun signedEstimate(delta: Double): String =
    if (delta > 0) "+" + formatEstimate(delta) else formatEstimate(delta)

/** "+10" for a rise, "-5" for a fall, "0" for none. */
internal fun signed(delta: Double): String =
    if (delta > 0) "+" + formatKg(delta) else formatKg(delta)

/** "125 kg" or "12 reps". */
@Composable
internal fun valueText(metric: ProgressMetric, value: Double): String = when (metric) {
    ProgressMetric.WEIGHT -> stringResource(CoreUiR.string.weight_kg, formatKg(value))
    ProgressMetric.REPS -> value.roundToInt().let { pluralStringResource(CoreUiR.plurals.reps_only, it, it) }
    ProgressMetric.MINUTES -> cardioText(value.roundToInt(), 0, 0)
    ProgressMetric.ONE_RM -> stringResource(CoreUiR.string.weight_kg, formatEstimate(value))
}

/** "+10 kg", "-5 kg" or "+3 reps". */
@Composable
internal fun deltaText(metric: ProgressMetric, delta: Double): String = when (metric) {
    ProgressMetric.WEIGHT -> stringResource(CoreUiR.string.weight_kg, signed(delta))
    ProgressMetric.REPS -> pluralStringResource(R.plurals.progress_delta_reps, abs(delta).roundToInt(), signed(delta))
    ProgressMetric.MINUTES -> stringResource(R.string.progress_delta_minutes, signed(delta))
    ProgressMetric.ONE_RM -> stringResource(CoreUiR.string.weight_kg, signedEstimate(delta))
}

/** "101.3 kg est. (80 kg × 8)" for an estimated 1RM point: the estimate and the set it came from. */
@Composable
internal fun oneRmText(point: SessionPoint): String =
    stringResource(R.string.progress_one_rm_top, formatEstimate(point.value), setOnlyText(point))

/** "80 kg × 8": the set behind [point]. */
@Composable
internal fun setOnlyText(point: SessionPoint): String {
    val weight = point.topSet.weightKg?.let { formatKg(it) } ?: formatKg(point.value)
    val reps = point.topSet.reps
    return if (reps == null) stringResource(CoreUiR.string.weight_kg, weight) else stringResource(CoreUiR.string.set_text, weight, reps)
}

/** "125 kg × 8" (or "12 reps" when charting reps): the set that gave the session's value. */
@Composable
internal fun topText(metric: ProgressMetric, point: SessionPoint): String = when (metric) {
    ProgressMetric.WEIGHT -> {
        val weight = formatKg(point.value)
        val reps = point.topSet.reps
        if (reps == null) stringResource(CoreUiR.string.weight_kg, weight) else stringResource(CoreUiR.string.set_text, weight, reps)
    }
    ProgressMetric.ONE_RM -> oneRmText(point)
    ProgressMetric.REPS, ProgressMetric.MINUTES -> valueText(metric, point.value)
}

/** "3 sets". */
@Composable
internal fun setsText(point: SessionPoint): String =
    pluralStringResource(R.plurals.progress_sets, point.setCount, point.setCount)

/** Whole days from [date] to [today]; 0 for today (and for a date in the future). */
internal fun daysSince(date: LocalDate, today: LocalDate): Int =
    ChronoUnit.DAYS.between(date, today).toInt().coerceAtLeast(0)

/** Percent change from the first to the last of [trend] (oldest first); null with fewer than two values or a first value of 0 or less. */
internal fun trendChangePercent(trend: List<Double>): Double? {
    if (trend.size < 2) return null
    val first = trend.first()
    if (first <= 0.0) return null
    return (trend.last() - first) / first * 100.0
}

/** "+8 %" for a rise, "-3 %" for a fall, "0" when it rounds to nothing. */
internal fun signedPercent(percent: Double): String {
    val rounded = percent.roundToInt()
    return when {
        rounded > 0 -> "+$rounded %"
        rounded < 0 -> "$rounded %"
        else -> "0"
    }
}
