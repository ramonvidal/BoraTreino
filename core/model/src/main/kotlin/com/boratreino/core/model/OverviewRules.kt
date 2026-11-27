package com.boratreino.core.model

import java.time.LocalDate

data class BodySnapshot(
    /** Latest per type with data, [MeasurementType] order, at most [OverviewRules.MAX_TILES]. */
    val tiles: List<BodyMeasurement>,
    /** Newest date among the tiles. */
    val latestDate: LocalDate?,
    /** Some shown type has at least 2 measurements. */
    val hasTrend: Boolean,
)

data class HabitSummary(
    val since: LocalDate,
    val cardioSessions: Int,
    val cardioMinutes: Int,
    val cardioDistanceM: Int,
    val averageSessionMinutes: Int?,
    val minutesBetweenSets: Double?,
)

/** The Body and Habit sections of the overview. */
object OverviewRules {
    const val MAX_TILES = 4

    fun body(measurements: List<BodyMeasurement>): BodySnapshot {
        val byType = measurements.groupBy { it.type }
        val tiles = MeasurementType.entries.mapNotNull { type ->
            byType[type]?.maxWithOrNull(compareBy({ it.date }, { it.id }))
        }.take(MAX_TILES)
        return BodySnapshot(
            tiles = tiles,
            latestDate = tiles.maxOfOrNull { it.date },
            hasTrend = tiles.any { (byType[it.type]?.size ?: 0) >= 2 },
        )
    }

    /** null when cardioSessions == 0 and averageSessionMinutes == null. */
    fun habit(report: StatsReport): HabitSummary? {
        val sessions = report.cardioBlocks.sumOf { it.sessions }
        val avg = report.times.averageMinutes
        if (sessions == 0 && avg == null) return null
        return HabitSummary(
            since = report.from,
            cardioSessions = sessions,
            cardioMinutes = report.cardioBlocks.sumOf { it.minutes },
            cardioDistanceM = report.cardioBlocks.sumOf { it.distanceM },
            averageSessionMinutes = avg,
            minutesBetweenSets = report.times.averageMinutesBetweenSets,
        )
    }
}
