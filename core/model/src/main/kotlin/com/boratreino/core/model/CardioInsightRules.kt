package com.boratreino.core.model

/**
 * Cardio summary. [speedTrend] and [efficiencyTrend] each follow ONE machine, the one with the most sessions of
 * that series (ties: the most recent, then the lowest machine id), so different machines are never mixed in a fit.
 * [sessions] counts distinct sessions with a speed (distance and time). [efficiencyLatest] / [efficiencyBest] look
 * across all cardio machines; null without any session that has both speed and intensity.
 */
data class CardioInsight(
    val sessions: Int,
    val speedTrend: TrendResult,
    val efficiencyTrend: TrendResult,
    val efficiencyLatest: Double?,
    val efficiencyBest: Double?,
)

object CardioInsightRules {
    /** Speed (km/h) per unit of intensity; null when either is missing, not finite, or the intensity is not > 0. */
    fun efficiency(speedKmh: Double?, intensity: Double?): Double? {
        if (speedKmh == null || intensity == null) return null
        if (!speedKmh.isFinite() || !intensity.isFinite() || intensity <= 0.0) return null
        return speedKmh / intensity
    }

    /** Cardio machines only; null without any session that has distance and time. */
    fun insight(progresses: List<MachineProgress>): CardioInsight? {
        val cardio = progresses.filter { it.machine.kind == MachineKind.CARDIO }
        val speedSeries = cardio.associateWith { it.cardioPoints[ProgressMetric.SPEED].orEmpty() }
            .filterValues { it.isNotEmpty() }
        if (speedSeries.isEmpty()) return null
        val sessions = speedSeries.values.flatten().map { it.sessionId }.distinct().size

        // Efficiency per session: the session's speed over its time-weighted intensity, both from the same machine.
        val efficiencySeries = cardio.associateWith { progress ->
            val intensity = progress.cardioPoints[ProgressMetric.INTENSITY].orEmpty().associateBy { it.sessionId }
            speedSeries[progress].orEmpty().mapNotNull { speed ->
                val e = efficiency(speed.value, intensity[speed.sessionId]?.value) ?: return@mapNotNull null
                Triple(speed.date, speed.sessionId, e)
            }
        }.filterValues { it.isNotEmpty() }

        val speedTrend = TrendRules.verdict(
            speedSeries.entries.bestMachine { it.value.maxOf { p -> p.date } }
                ?.value.orEmpty().map { it.date to it.value },
        )
        val efficiencyTrend = TrendRules.verdict(
            efficiencySeries.entries.bestMachine { it.value.maxOf { t -> t.first } }
                ?.value.orEmpty().map { it.first to it.third },
        )
        val all = efficiencySeries.values.flatten()
        val latest = all.maxWithOrNull(compareBy({ it.first }, { it.second }))?.third
        return CardioInsight(sessions, speedTrend, efficiencyTrend, latest, all.maxOfOrNull { it.third })
    }

    private fun <V> Collection<Map.Entry<MachineProgress, List<V>>>.bestMachine(
        lastDate: (Map.Entry<MachineProgress, List<V>>) -> java.time.LocalDate,
    ): Map.Entry<MachineProgress, List<V>>? =
        sortedWith(
            compareByDescending<Map.Entry<MachineProgress, List<V>>> { it.value.size }
                .thenByDescending(lastDate)
                .thenBy { it.key.machine.id },
        ).firstOrNull()
}
