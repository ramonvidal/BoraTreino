package com.boratreino.feature.progress

import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.WeightEntry

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
)

/** What the Stats page can ask for. */
internal data class StatsActions(
    val onPeriod: (StatsPeriod) -> Unit = {},
    val onOpenMachine: (Long) -> Unit = {},
)
