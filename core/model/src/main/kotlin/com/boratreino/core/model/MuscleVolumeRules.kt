package com.boratreino.core.model

/** Where a muscle's weekly direct sets sit against the common 10..20 hypertrophy guideline. */
enum class VolumeBand { NONE, BELOW, IN_RANGE, ABOVE }

data class MuscleVolume(val muscle: Muscle, val setsPerWeek: Double, val band: VolumeBand)

object MuscleVolumeRules {
    const val LOW = 10.0
    const val HIGH = 20.0
    private const val EPS = 1e-9

    /**
     * Average direct sets per week of every muscle over [weekCount] weeks (weeks without rows count as 0), all
     * muscles in enum order. [weekCount] <= 0 gives 0 for everyone. Bands: NONE = 0 sets, BELOW < 10,
     * IN_RANGE 10..20 (both ends inclusive), ABOVE > 20; a tolerance of 1e-9 absorbs fractional-set rounding.
     */
    fun volumes(detailWeeks: List<MuscleDetailWeek>, weekCount: Int): List<MuscleVolume> {
        val totals = detailWeeks.groupBy { it.muscle }.mapValues { (_, rows) -> rows.sumOf { it.directSets } }
        return Muscle.entries.map { muscle ->
            val perWeek = if (weekCount > 0) (totals[muscle] ?: 0.0) / weekCount else 0.0
            MuscleVolume(muscle, perWeek, band(perWeek))
        }
    }

    fun band(setsPerWeek: Double): VolumeBand = when {
        setsPerWeek <= EPS -> VolumeBand.NONE
        setsPerWeek < LOW - EPS -> VolumeBand.BELOW
        setsPerWeek <= HIGH + EPS -> VolumeBand.IN_RANGE
        else -> VolumeBand.ABOVE
    }
}
