package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.formatKg
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import com.boratreino.core.ui.R as CoreUiR

private const val TONNE_KG = 1000.0

/** Up to [digits] decimals, no grouping: 2.8 -> "2.8", 3.0 -> "3". */
internal fun formatDecimal(value: Double, digits: Int = 1, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = digits
        isGroupingUsed = false
    }.format(value)

/** Exactly one decimal: 6.0 -> "6.0". */
internal fun formatOneDecimal(value: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(value)

/** "+6.4" for a rise, "-2.0" for a fall, "0.0" for none. */
internal fun signedPercentNumber(value: Double, locale: Locale = Locale.getDefault()): String {
    val text = formatOneDecimal(value, locale)
    return if (value > 0 && !text.startsWith("+")) "+$text" else text
}

/** True when the period has no logged training at all (no working sets and no sessions). */
internal fun StatsReport.isEmptyPeriod(): Boolean =
    weeks.all { it.workingSets == 0 && it.sessions == 0 } && muscleWeeks.isEmpty()

/** "850 kg" below a tonne, "12.4 t" from 1000 kg on. */
@Composable
internal fun volumeText(volumeKg: Double): String =
    if (volumeKg >= TONNE_KG) {
        stringResource(R.string.stats_volume_tonnes, formatDecimal(volumeKg / TONNE_KG, 1))
    } else {
        stringResource(CoreUiR.string.weight_kg, formatKg(volumeKg.roundToInt().toDouble()))
    }
