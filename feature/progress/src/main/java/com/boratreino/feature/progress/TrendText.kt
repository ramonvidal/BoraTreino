package com.boratreino.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.TrendResult
import com.boratreino.core.model.TrendVerdict
import kotlin.math.abs

/** What a trend line is about, so the "not enough data" sentence names the right thing. */
internal enum class TrendNoun { MEASUREMENTS, SESSIONS }

/**
 * One plain sentence for [result]: "Falling 0.4 cm per week", "Holding steady, no clear change" or
 * "Not enough data: needs 3 measurements". [amountRes] is a string taking the absolute weekly change
 * (for example "%1$s cm"), shown with [digits] decimals.
 */
@Composable
internal fun trendVerdictText(result: TrendResult, amountRes: Int, digits: Int, noun: TrendNoun): String {
    val slope = result.fit?.slopePerWeek ?: 0.0
    return when (result.verdict) {
        TrendVerdict.RISING -> stringResource(R.string.stats_trend_u2_rising, stringResource(amountRes, formatDecimal(abs(slope), digits)))
        TrendVerdict.FALLING -> stringResource(R.string.stats_trend_u2_falling, stringResource(amountRes, formatDecimal(abs(slope), digits)))
        TrendVerdict.PLATEAU -> stringResource(R.string.stats_trend_u2_plateau)
        TrendVerdict.NOT_ENOUGH_DATA -> when {
            result.points >= result.neededPoints -> stringResource(R.string.stats_trend_u2_needs_span, result.neededSpanDays)
            noun == TrendNoun.MEASUREMENTS -> stringResource(R.string.stats_trend_u2_needs_measurements, result.neededPoints)
            else -> stringResource(R.string.stats_trend_u2_needs_sessions, result.neededPoints, result.points)
        }
    }
}
