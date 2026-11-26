package com.boratreino.core.model

import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/** [meanIndex] = mean percent difference from each exercise's own average; [standardError] null when [n] < 2. */
data class PatternBucket(val key: Int, val n: Int, val meanIndex: Double, val standardError: Double?)

data class PatternReport(
    val weekday: List<PatternBucket>,
    val rest: List<PatternBucket>,
    val sessionsUsed: Int,
    val enough: Boolean,
)

object PatternRules {
    const val MIN_SESSIONS = 15
    const val MIN_PER_BUCKET = 3
    private const val MIN_POINTS_PER_MACHINE = 3

    /**
     * Does performance (estimated 1RM per session) depend on weekday or on rest days? Per machine, each session's
     * index = (value / the machine's mean value - 1) * 100. Machines with fewer than 3 [MachineProgress.oneRmPoints]
     * (cardio has none) or a mean <= 0 are skipped. Weekday key = DayOfWeek.value (1..7), every session counts.
     * Rest key = days since the same machine's previous session: 0 = 0..3, 1 = 4..6, 2 = 7+; a machine's first
     * session has no rest value and is left out of [PatternReport.rest]. Buckets are ascending by key, empty ones
     * omitted, small ones (n < [MIN_PER_BUCKET]) kept. [PatternReport.sessionsUsed] = sessions of the machines used.
     */
    fun analyze(progresses: List<MachineProgress>): PatternReport {
        val weekday = HashMap<Int, MutableList<Double>>()
        val rest = HashMap<Int, MutableList<Double>>()
        var used = 0
        for (progress in progresses) {
            val points = progress.oneRmPoints.sortedWith(compareBy({ it.date }, { it.sessionId }))
            if (points.size < MIN_POINTS_PER_MACHINE) continue
            val mean = points.sumOf { it.value } / points.size
            if (mean <= 0.0) continue
            used += points.size
            points.forEachIndexed { i, point ->
                val index = (point.value / mean - 1.0) * 100.0
                weekday.getOrPut(point.date.dayOfWeek.value) { ArrayList() }.add(index)
                if (i > 0) {
                    val gap = ChronoUnit.DAYS.between(points[i - 1].date, point.date)
                    val key = when {
                        gap <= 3 -> 0
                        gap <= 6 -> 1
                        else -> 2
                    }
                    rest.getOrPut(key) { ArrayList() }.add(index)
                }
            }
        }
        return PatternReport(buckets(weekday), buckets(rest), used, used >= MIN_SESSIONS)
    }

    private fun buckets(groups: Map<Int, List<Double>>): List<PatternBucket> =
        groups.entries.sortedBy { it.key }.map { (key, values) ->
            val n = values.size
            val mean = values.average()
            val se = if (n < 2) null else sqrt(values.sumOf { (it - mean) * (it - mean) } / (n - 1)) / sqrt(n.toDouble())
            PatternBucket(key, n, mean, se)
        }
}
