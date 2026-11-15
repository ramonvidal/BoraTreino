package com.boratreino.feature.progress

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementRules
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.holdRepeat
import kotlinx.coroutines.delay
import java.time.LocalDate
import com.boratreino.core.ui.R as CoreUiR

private const val HISTORY_ENTRIES = 5
private const val UNDO_MILLIS = 5_000L
private const val DEFAULT_START_CM = 80.0

/** The type a fresh card shows: the first one with data, else the waist. */
internal fun defaultMeasurementType(all: List<BodyMeasurement>): MeasurementType =
    MeasurementRules.typesWithData(all).firstOrNull() ?: MeasurementType.WAIST

/** The value the log sheet starts at for [type]: the latest value, else 80 cm. */
internal fun measurementStart(all: List<BodyMeasurement>, type: MeasurementType): Double =
    MeasurementRules.latest(all, type)?.valueCm ?: DEFAULT_START_CM

/** The last [HISTORY_ENTRIES] entries of [type], newest first. */
internal fun measurementHistory(all: List<BodyMeasurement>, type: MeasurementType): List<BodyMeasurement> =
    all.filter { it.type == type }
        .sortedWith(compareByDescending<BodyMeasurement> { it.date }.thenByDescending { it.id })
        .take(HISTORY_ENTRIES)

@Composable
internal fun MeasurementType.label(): String = stringResource(
    when (this) {
        MeasurementType.WAIST -> R.string.stats_measure_waist
        MeasurementType.HIPS -> R.string.stats_measure_hips
        MeasurementType.CHEST -> R.string.stats_measure_chest
        MeasurementType.ARM -> R.string.stats_measure_arm
        MeasurementType.THIGH -> R.string.stats_measure_thigh
        MeasurementType.NECK -> R.string.stats_measure_neck
    },
)

@Composable
private fun cmText(valueCm: Double): String = stringResource(R.string.stats_measure_value, formatDecimal(valueCm, 1))

/**
 * Body measurements: type chips, the latest value, its change over the period [from], a line chart,
 * the last entries (each with a delete icon and an Undo row afterwards) and the "Log measurement"
 * button. [selectedType] null means the default type. Drawn from its arguments alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeasurementsCard(
    measurements: List<BodyMeasurement>,
    from: LocalDate?,
    today: LocalDate,
    selectedType: MeasurementType?,
    onType: (MeasurementType) -> Unit,
    onLog: () -> Unit,
    onDelete: (BodyMeasurement) -> Unit,
    onUndo: (BodyMeasurement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = selectedType ?: defaultMeasurementType(measurements)
    var deleted by remember { mutableStateOf<BodyMeasurement?>(null) }
    LaunchedEffect(deleted) {
        if (deleted != null) {
            delay(UNDO_MILLIS)
            deleted = null
        }
    }
    StatsCard(stringResource(R.string.stats_measure_title), modifier) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementType.entries.forEach { entry ->
                BoraChip(label = entry.label(), selected = entry == type, onClick = { onType(entry) })
            }
        }
        val latest = MeasurementRules.latest(measurements, type)
        if (latest == null) {
            Text(
                stringResource(R.string.stats_measure_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            val series = MeasurementRules.series(measurements, type, from)
            Text(cmText(latest.valueCm), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            MeasurementRules.change(series)?.let { change ->
                Text(
                    stringResource(R.string.stats_measure_change, signedPercentNumber(change)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (series.size >= 2) {
                MeasurementChart(series.map { it.valueCm }, type.label())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(formatShortDate(series.first().date, today), style = MaterialTheme.typography.labelMedium, color = muted)
                    Text(formatShortDate(series.last().date, today), style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            Text(
                stringResource(R.string.stats_measure_history),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            measurementHistory(measurements, type).forEach { entry ->
                MeasurementRow(entry, today, onDelete = {
                    deleted = entry
                    onDelete(entry)
                })
            }
        }
        deleted?.let { gone ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.stats_measure_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                BoraButton(
                    text = stringResource(CoreUiR.string.undo),
                    onClick = {
                        onUndo(gone)
                        deleted = null
                    },
                    style = BoraButtonStyle.Text,
                )
            }
        }
        BoraButton(
            text = stringResource(R.string.stats_measure_log),
            onClick = onLog,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = CoreUiR.drawable.ic_add,
        )
    }
}

@Composable
private fun MeasurementRow(entry: BodyMeasurement, today: LocalDate, onDelete: () -> Unit) {
    val dateText = formatShortDate(entry.date, today)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(cmText(entry.valueCm), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDelete) {
            Icon(
                painterResource(CoreUiR.drawable.ic_delete),
                contentDescription = stringResource(R.string.stats_measure_delete, dateText),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A simple line over [values] (oldest first): Ink line, the last point a green dot. */
@Composable
private fun MeasurementChart(values: List<Double>, name: String, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.stats_measure_chart,
        name,
        formatDecimal(values.first(), 1),
        formatDecimal(values.last(), 1),
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        val dotRadius = 5.dp.toPx()
        val grid = 1.dp.toPx()
        drawLine(gridColor, Offset(0f, size.height - grid / 2f), Offset(size.width, size.height - grid / 2f), strokeWidth = grid)
        val offsets = trendOffsets(values, size.width, size.height, inset = dotRadius + 2.dp.toPx())
        if (offsets.size >= 2) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                for (i in 1 until offsets.size) lineTo(offsets[i].x, offsets[i].y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        offsets.lastOrNull()?.let { drawCircle(dotColor, dotRadius, it) }
    }
}

/** The bottom sheet around [LogMeasurementContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogMeasurementSheet(
    measurements: List<BodyMeasurement>,
    initialType: MeasurementType,
    onSave: (MeasurementType, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LogMeasurementContent(measurements, initialType, onSave)
    }
}

/** Type chips, a big value with minus and plus (no keyboard) and Save; starts at the type's latest value or 80 cm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogMeasurementContent(
    measurements: List<BodyMeasurement>,
    initialType: MeasurementType,
    onSave: (MeasurementType, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by remember { mutableStateOf(initialType) }
    var value by remember(type) { mutableDoubleStateOf(measurementStart(measurements, type)) }
    Column(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.stats_measure_log), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementType.entries.forEach { entry ->
                BoraChip(label = entry.label(), selected = entry == type, onClick = { type = entry })
            }
        }
        val valueDescription = stringResource(R.string.stats_measure_value_description)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StepButton(CoreUiR.drawable.ic_remove, stringResource(CoreUiR.string.stepper_minus, valueDescription)) {
                value = MeasurementRules.clamp(value - MeasurementRules.STEP_CM)
            }
            Text(
                cmText(value),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = valueDescription },
            )
            StepButton(CoreUiR.drawable.ic_add, stringResource(CoreUiR.string.stepper_plus, valueDescription)) {
                value = MeasurementRules.clamp(value + MeasurementRules.STEP_CM)
            }
        }
        Spacer(Modifier.height(4.dp))
        BoraButton(
            text = stringResource(CoreUiR.string.save),
            onClick = { onSave(type, value) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .holdRepeat(description) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}
