package com.boratreino.core.model

enum class StrengthDirection { RISING, FLAT, FALLING }

data class StrengthRow(
    val machine: Machine,
    val before: Double,
    val after: Double,
    val changePercent: Double,
    val direction: StrengthDirection,
    /** e1RM per session in the period, the last SPARK_POINTS, oldest first. */
    val sparkline: List<Double>,
    /** Points of the machine's e1RM history inside the period. */
    val sessionsInPeriod: Int,
    /** The last in-period point's top set. */
    val lastTop: WorkoutSet?,
)

data class StrengthSummary(
    val risers: List<StrengthRow>,
    val flatOrFalling: List<StrengthRow>,
    val toCheck: List<SuspiciousJump>,
) {
    companion object {
        val EMPTY = StrengthSummary(emptyList(), emptyList(), emptyList())
    }
}

/** The Strength section of the overview: who is going up, who is not, and which values to check. */
object StrengthSummaryRules {
    const val SHOWN = 3
    const val RISING_FROM = 1.0
    const val SPARK_POINTS = 8

    fun summary(report: StatsReport): StrengthSummary {
        val flagged = report.suspiciousJumps.map { it.machine.id }.toSet()
        val history = report.oneRmHistory.associateBy { it.machine.id }
        val rows = report.strength.filter { it.machine.id !in flagged }.map { trend ->
            val inPeriod = history[trend.machine.id]?.points.orEmpty()
                .filter { !it.date.isBefore(report.from) && !it.date.isAfter(report.to) }
            StrengthRow(
                machine = trend.machine,
                before = trend.before,
                after = trend.after,
                changePercent = trend.changePercent,
                direction = when {
                    trend.changePercent >= RISING_FROM -> StrengthDirection.RISING
                    trend.changePercent <= -RISING_FROM -> StrengthDirection.FALLING
                    else -> StrengthDirection.FLAT
                },
                sparkline = inPeriod.takeLast(SPARK_POINTS).map { it.value },
                sessionsInPeriod = inPeriod.size,
                lastTop = inPeriod.lastOrNull()?.topSet,
            )
        }
        val byName = compareBy<StrengthRow, String>(String.CASE_INSENSITIVE_ORDER) { it.machine.name }
        return StrengthSummary(
            risers = rows.filter { it.direction == StrengthDirection.RISING }
                .sortedWith(compareByDescending<StrengthRow> { it.changePercent }.then(byName))
                .take(SHOWN),
            flatOrFalling = rows.filter { it.direction != StrengthDirection.RISING }
                .sortedWith(compareBy<StrengthRow> { it.changePercent }.then(byName))
                .take(SHOWN),
            toCheck = report.suspiciousJumps.take(SHOWN),
        )
    }
}
