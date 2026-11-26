package com.boratreino.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where the acute:chronic workload ratio sits. See [WorkloadRules]. */
enum class AcwrStatus { NO_DATA, BUILDING_BASELINE, LOW, OPTIMAL, HIGH, VERY_HIGH }

/**
 * Training load of the last week against the last month. [acuteSets] = working sets in the last 7 days (today
 * included), [chronicWeeklyAverage] = sets in the last 28 days / 4, [acwr] = acute / chronic (null when chronic is 0),
 * [historyDays] = days from the first day with load to today, inclusive (0 without load).
 */
data class WorkloadReport(
    val acuteSets: Int,
    val chronicWeeklyAverage: Double,
    val acwr: Double?,
    val status: AcwrStatus,
    val historyDays: Int,
)

object WorkloadRules {
    const val ACUTE_DAYS = 7
    const val CHRONIC_DAYS = 28
    const val LOW_BELOW = 0.8
    const val OPTIMAL_UP_TO = 1.3
    const val HIGH_UP_TO = 1.5

    /**
     * [setsPerDay] = working sets (no warm-ups, strength machines only) per date; the caller filters. Days after
     * [today] and days with 0 or fewer sets are ignored.
     *
     * Status: NO_DATA when there is no load at all, or when [acwr] is null (nothing in the last 28 days);
     * BUILDING_BASELINE while [WorkloadReport.historyDays] < 28 (the ratio is still filled);
     * otherwise LOW < 0.8, OPTIMAL 0.8..1.3, HIGH > 1.3..1.5, VERY_HIGH > 1.5 (upper bounds inclusive).
     */
    fun report(setsPerDay: Map<LocalDate, Int>, today: LocalDate): WorkloadReport {
        val loads = setsPerDay.filter { (date, sets) -> sets > 0 && !date.isAfter(today) }
        if (loads.isEmpty()) {
            return WorkloadReport(0, 0.0, null, AcwrStatus.NO_DATA, 0)
        }
        val first = loads.keys.min()
        val historyDays = (ChronoUnit.DAYS.between(first, today) + 1).toInt()

        val acuteSets = (0 until ACUTE_DAYS).sumOf { loads[today.minusDays(it.toLong())] ?: 0 }
        val chronicSets = (0 until CHRONIC_DAYS).sumOf { loads[today.minusDays(it.toLong())] ?: 0 }
        val chronic = chronicSets / 4.0
        val acwr = if (chronic > 0.0) acuteSets / chronic else null

        val status = when {
            acwr == null -> AcwrStatus.NO_DATA
            historyDays < CHRONIC_DAYS -> AcwrStatus.BUILDING_BASELINE
            acwr < LOW_BELOW -> AcwrStatus.LOW
            acwr <= OPTIMAL_UP_TO -> AcwrStatus.OPTIMAL
            acwr <= HIGH_UP_TO -> AcwrStatus.HIGH
            else -> AcwrStatus.VERY_HIGH
        }
        return WorkloadReport(acuteSets, chronic, acwr, status, historyDays)
    }
}
