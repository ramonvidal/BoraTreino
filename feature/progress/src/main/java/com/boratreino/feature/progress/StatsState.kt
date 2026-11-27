package com.boratreino.feature.progress

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.BodySnapshot
import com.boratreino.core.model.Finding
import com.boratreino.core.model.FrequencySummary
import com.boratreino.core.model.HabitSummary
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.StrengthSummary
import com.boratreino.core.model.WeightEntry
import java.time.LocalDate

/** Everything the Stats page shows. [loaded] false (no [report] yet) draws a blank body. */
internal data class StatsUiState(
    val loaded: Boolean = false,
    val period: StatsPeriod = StatsPeriod.FOUR_WEEKS,
    val report: StatsReport? = null,
    val weeklyGoal: Int = 4,
    /** Latest weight entry's kg, else the profile's weight; null when neither exists. */
    val bodyWeightKg: Double? = null,
    /** Weight entries, newest first as stored. */
    val weights: List<WeightEntry> = emptyList(),
    /** Body measurements, newest date first as stored. */
    val measurements: List<BodyMeasurement> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val frequency: FrequencySummary? = null,
    val strengthSummary: StrengthSummary = StrengthSummary.EMPTY,
    val body: BodySnapshot = BodySnapshot(emptyList(), null, false),
    val habit: HabitSummary? = null,
)

/** Where Analysis opens. */
internal enum class AnalysisCard {
    TREND, WORKLOAD, PATTERNS, HARD_SETS, BALANCE, STRENGTH, RECORDS, MUSCLE_VOLUME,
    WEEK_MUSCLES, BODY_MAP, MUSCLES, REP_RANGES, WEEKLY_VOLUME, STRENGTH_VS_WEIGHT, RELATIVE_STRENGTH,
    SUMMARY, CARDIO, TIMES, RECENCY, MEASUREMENTS, PROPORTIONS,
}

/** The Analysis level shows one section at a time; the chips at its top pick it. */
internal enum class AnalysisSection { HIGHLIGHTS, STRENGTH, MUSCLES, HABIT, BODY }

/** Pure: the section a card belongs to. */
internal fun sectionOf(card: AnalysisCard): AnalysisSection = when (card) {
    AnalysisCard.TREND, AnalysisCard.WORKLOAD, AnalysisCard.PATTERNS, AnalysisCard.HARD_SETS, AnalysisCard.BALANCE ->
        AnalysisSection.HIGHLIGHTS
    AnalysisCard.STRENGTH, AnalysisCard.RECORDS, AnalysisCard.REP_RANGES, AnalysisCard.STRENGTH_VS_WEIGHT,
    AnalysisCard.RELATIVE_STRENGTH -> AnalysisSection.STRENGTH
    AnalysisCard.MUSCLE_VOLUME, AnalysisCard.WEEK_MUSCLES, AnalysisCard.BODY_MAP, AnalysisCard.MUSCLES,
    AnalysisCard.WEEKLY_VOLUME -> AnalysisSection.MUSCLES
    AnalysisCard.SUMMARY, AnalysisCard.CARDIO, AnalysisCard.TIMES, AnalysisCard.RECENCY -> AnalysisSection.HABIT
    AnalysisCard.MEASUREMENTS, AnalysisCard.PROPORTIONS -> AnalysisSection.BODY
}

/** What the Stats page can ask for. */
internal data class StatsActions(
    val onPeriod: (StatsPeriod) -> Unit = {},
    val onOpenMachine: (Long) -> Unit = {},
    val onAddMeasurement: (LocalDate, MeasurementType, Double) -> Unit = { _, _, _ -> },
    val onDeleteMeasurement: (Long) -> Unit = {},
    val onUndoDeleteMeasurement: (BodyMeasurement) -> Unit = {},
    /** Opens the Analysis level, scrolled to [card] (null = top). */
    val onOpenAnalysis: (AnalysisCard?) -> Unit = {},
    /** Opens the Calendar tab through :app. */
    val onOpenCalendar: () -> Unit = {},
)
