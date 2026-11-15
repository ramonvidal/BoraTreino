package com.boratreino.feature.progress

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.StatsPage
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.WeightEntry
import java.time.LocalDate

/** Everything the Stats page shows. [loaded] false (no [report] yet) draws a blank body. */
internal data class StatsUiState(
    val loaded: Boolean = false,
    val period: StatsPeriod = StatsPeriod.THREE_MONTHS,
    val report: StatsReport? = null,
    val weeklyGoal: Int = 4,
    /** Latest weight entry's kg, else the profile's weight; null when neither exists. */
    val bodyWeightKg: Double? = null,
    /** Weight entries, newest first as stored. */
    val weights: List<WeightEntry> = emptyList(),
    /** Body measurements, newest date first as stored. */
    val measurements: List<BodyMeasurement> = emptyList(),
)

/** What the Stats page can ask for. */
internal data class StatsActions(
    val onPeriod: (StatsPeriod) -> Unit = {},
    val onOpenMachine: (Long) -> Unit = {},
    val onAddMeasurement: (LocalDate, MeasurementType, Double) -> Unit = { _, _, _ -> },
    val onDeleteMeasurement: (Long) -> Unit = {},
    val onUndoDeleteMeasurement: (BodyMeasurement) -> Unit = {},
    /** A highlight card was tapped: open the page that holds its card. */
    val onOpenPage: (StatsPage) -> Unit = {},
)
