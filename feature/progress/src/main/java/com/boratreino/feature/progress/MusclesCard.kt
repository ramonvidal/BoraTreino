package com.boratreino.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyInsightRules
import com.boratreino.core.model.Muscle
import com.boratreino.core.model.StatsReport
import com.boratreino.core.ui.label

/** The muscles ordered by load, highest first; ties keep the enum (display) order. */
internal fun musclesByLoad(load: Map<Muscle, Double>): List<Muscle> =
    Muscle.entries.sortedByDescending { load.getValue(it) }

/** Below this a row would read "0.0": such muscles are hidden until "Show all". */
private const val SHOWN_MIN_LOAD = 0.05

/** One bar per specific muscle: average effective sets a week over the period, highest first, untrained last. */
@Composable
internal fun MusclesCard(report: StatsReport, modifier: Modifier = Modifier) {
    val load = BodyInsightRules.muscleDetailLoad(report)
    val all = musclesByLoad(load)
    var showAll by rememberSaveable { mutableStateOf(false) }
    val ordered = if (showAll) all else all.filter { load.getValue(it) >= SHOWN_MIN_LOAD }
    val hidden = all.size - ordered.size
    val names = Muscle.entries.associateWith { it.label() }
    val max = all.maxOfOrNull { load.getValue(it) } ?: 0.0
    val accent = MaterialTheme.colorScheme.primary
    val track = StatsPalette.track()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val description = ordered.joinToString(". ") { "${names.getValue(it)}: ${formatOneDecimal(load.getValue(it))}" }
    StatsCard(stringResource(R.string.stats_muscles_title), modifier) {
        NoteLine(stringResource(R.string.stats_week_muscles_note))
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ordered.forEach { muscle ->
                val value = load.getValue(muscle)
                val textColor = if (value > 0.0) MaterialTheme.colorScheme.onSurface else muted
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            names.getValue(muscle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatOneDecimal(value), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(track, Offset.Zero, size, radius)
                        if (max > 0.0 && value > 0.0) {
                            val w = (size.width * (value / max)).toFloat().coerceAtLeast(size.height)
                            drawRoundRect(accent, Offset.Zero, Size(w, size.height), radius)
                        }
                    }
                }
            }
        }
        if (hidden > 0) {
            TextButton(onClick = { showAll = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.stats_muscles_show_all, all.size))
            }
        }
    }
}
