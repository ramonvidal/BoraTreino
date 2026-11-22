package com.boratreino.feature.progress

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint

/** The metrics a cardio exercise can chart, in the order the chips show them. */
internal val CARDIO_METRICS: List<ProgressMetric> =
    listOf(ProgressMetric.SPEED, ProgressMetric.INTENSITY, ProgressMetric.DISTANCE, ProgressMetric.MINUTES)

internal fun Machine.isCardio(): Boolean = kind == MachineKind.CARDIO

/**
 * The session points of cardio [metric] for this machine: the per-metric points the data layer provides
 * ([MachineProgress.cardioPoints]), falling back to [MachineProgress.points] when [metric] is the machine's own.
 */
internal fun MachineProgress.pointsFor(metric: ProgressMetric): List<SessionPoint> =
    cardioPoints[metric] ?: if (metric == this.metric) points else emptyList()

/** True when [points] carry something worth charting for [metric]: speed and distance need a value above 0. */
internal fun hasCardioData(metric: ProgressMetric, points: List<SessionPoint>): Boolean = when (metric) {
    ProgressMetric.SPEED, ProgressMetric.DISTANCE -> points.any { it.value > 0.0 }
    else -> points.isNotEmpty()
}

/** The cardio metrics of [progress] that have data, in [CARDIO_METRICS] order. */
internal fun availableCardioMetrics(progress: MachineProgress): List<ProgressMetric> =
    CARDIO_METRICS.filter { hasCardioData(it, progress.pointsFor(it)) }

/** SPEED when there is distance data, else INTENSITY when there is intensity data, else MINUTES. */
internal fun defaultCardioMetricOf(available: List<ProgressMetric>): ProgressMetric = when {
    ProgressMetric.SPEED in available -> ProgressMetric.SPEED
    ProgressMetric.INTENSITY in available -> ProgressMetric.INTENSITY
    else -> ProgressMetric.MINUTES
}
