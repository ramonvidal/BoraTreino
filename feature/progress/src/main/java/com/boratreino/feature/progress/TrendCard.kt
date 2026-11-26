package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Forecast
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.TrendResult
import com.boratreino.core.model.TrendVerdict
import com.boratreino.core.ui.BoraCard

/** Days in a week, to turn the rule's needed span into weeks for the text. */
private const val DAYS_PER_WEEK = 7

/** "Rising", "Falling", "Plateau" or "Not enough data". */
@Composable
internal fun trendVerdictLabel(verdict: TrendVerdict): String = stringResource(
    when (verdict) {
        TrendVerdict.RISING -> R.string.trend_rising
        TrendVerdict.FALLING -> R.string.trend_falling
        TrendVerdict.PLATEAU -> R.string.trend_plateau
        TrendVerdict.NOT_ENOUGH_DATA -> R.string.trend_not_enough
    },
)

/** The unit a trend over [metric] is measured in: "kg", "km/h", "reps", "min"; empty when it has none. */
@Composable
internal fun trendUnit(metric: ProgressMetric): String = when (metric) {
    ProgressMetric.WEIGHT, ProgressMetric.ONE_RM -> stringResource(R.string.trend_unit_kg)
    ProgressMetric.SPEED -> stringResource(R.string.trend_unit_kmh)
    ProgressMetric.REPS -> stringResource(R.string.trend_unit_reps)
    ProgressMetric.MINUTES -> stringResource(R.string.trend_unit_minutes)
    ProgressMetric.DISTANCE -> stringResource(R.string.trend_unit_km)
    ProgressMetric.INTENSITY -> ""
}

/** "+1.2 kg per week"; the unit is left out when [unit] is empty. */
@Composable
internal fun slopeText(slopePerWeek: Double, unit: String, valueFormatter: (Double) -> String): String =
    stringResource(R.string.trend_slope_per_week, signedWith(slopePerWeek, valueFormatter), unit).trim()

private fun signedWith(value: Double, valueFormatter: (Double) -> String): String =
    if (value > 0) "+" + valueFormatter(value) else valueFormatter(value)

/**
 * The honest trend of one exercise: verdict, weekly change with its 95% range, fit quality and a
 * 4-week forecast; "needs more data" instead of a guess below the minimum. [valueFormatter] gives
 * the number only, [unit] is appended by the strings.
 */
@Composable
internal fun TrendCard(
    result: TrendResult,
    forecast: Forecast?,
    valueFormatter: (Double) -> String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    var explain by rememberSaveable { mutableStateOf(false) }
    val fit = result.fit
    BoraCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(trendVerdictLabel(result.verdict), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (result.verdict == TrendVerdict.NOT_ENOUGH_DATA || fit == null) {
                Text(
                    stringResource(
                        R.string.trend_needs,
                        result.neededPoints,
                        result.neededSpanDays / DAYS_PER_WEEK,
                        result.points,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    stringResource(
                        R.string.trend_slope_range,
                        slopeText(fit.slopePerWeek, unit, valueFormatter),
                        signedWith(fit.slopeLow, valueFormatter),
                        signedWith(fit.slopeHigh, valueFormatter),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    pluralStringResource(R.plurals.trend_fit, fit.n, formatDecimal(fit.r2, 2), fit.n),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (forecast != null) {
                    Text(
                        stringResource(
                            R.string.trend_forecast,
                            formatDecimal(forecast.weeksAhead, 0),
                            valueFormatter(forecast.value),
                            unit,
                            valueFormatter(forecast.low),
                            valueFormatter(forecast.high),
                        ).replace("  ", " "),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            TextButton(onClick = { explain = !explain }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (explain) R.string.trend_hide_explain else R.string.trend_show_explain))
            }
            if (explain) {
                Text(
                    stringResource(R.string.trend_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
