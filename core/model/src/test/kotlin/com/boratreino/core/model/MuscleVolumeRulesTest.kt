package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Weekly direct sets per muscle against the 10..20 guideline. Expected numbers are worked out by hand. */
class MuscleVolumeRulesTest {
    private val week: LocalDate = LocalDate.of(2026, 11, 2)

    private fun row(muscle: Muscle, direct: Double, indirect: Double = 0.0, weekStart: LocalDate = week) =
        MuscleDetailWeek(weekStart, muscle, direct, indirect)

    private fun byMuscle(volumes: List<MuscleVolume>, muscle: Muscle): MuscleVolume =
        volumes.single { it.muscle == muscle }

    @Test
    fun `the guideline bounds are 10 and 20 sets a week`() {
        assertEquals(10.0, MuscleVolumeRules.LOW, 0.0)
        assertEquals(20.0, MuscleVolumeRules.HIGH, 0.0)
    }

    @Test
    fun `every muscle is listed in enum order even with no data`() {
        val volumes = MuscleVolumeRules.volumes(emptyList(), 4)

        assertEquals(14, volumes.size)
        assertEquals(Muscle.entries.toList(), volumes.map { it.muscle })
        assertTrue(volumes.all { it.setsPerWeek == 0.0 && it.band == VolumeBand.NONE })
    }

    @Test
    fun `the average counts weeks without rows as zero`() {
        // Chest: 12 sets in one week and 18 in another, 30 in total.
        val rows = listOf(
            row(Muscle.CHEST, 12.0, weekStart = week),
            row(Muscle.CHEST, 18.0, weekStart = week.plusDays(7)),
        )

        val over4 = byMuscle(MuscleVolumeRules.volumes(rows, 4), Muscle.CHEST)
        assertEquals(7.5, over4.setsPerWeek, 1e-12)
        assertEquals(VolumeBand.BELOW, over4.band)

        val over2 = byMuscle(MuscleVolumeRules.volumes(rows, 2), Muscle.CHEST)
        assertEquals(15.0, over2.setsPerWeek, 1e-12)
        assertEquals(VolumeBand.IN_RANGE, over2.band)

        val over1 = byMuscle(MuscleVolumeRules.volumes(rows, 1), Muscle.CHEST)
        assertEquals(30.0, over1.setsPerWeek, 1e-12)
        assertEquals(VolumeBand.ABOVE, over1.band)
    }

    @Test
    fun `the band boundaries are inclusive at 10 and 20`() {
        val rows = listOf(
            row(Muscle.SHOULDERS, 10.0),
            row(Muscle.TRICEPS, 20.0),
            row(Muscle.BICEPS, 20.5),
            row(Muscle.FOREARMS, 9.5),
            row(Muscle.ABS, 0.0),
        )
        val volumes = MuscleVolumeRules.volumes(rows, 1)

        assertEquals(VolumeBand.IN_RANGE, byMuscle(volumes, Muscle.SHOULDERS).band)
        assertEquals(VolumeBand.IN_RANGE, byMuscle(volumes, Muscle.TRICEPS).band)
        assertEquals(VolumeBand.ABOVE, byMuscle(volumes, Muscle.BICEPS).band)
        assertEquals(VolumeBand.BELOW, byMuscle(volumes, Muscle.FOREARMS).band)
        assertEquals(VolumeBand.NONE, byMuscle(volumes, Muscle.ABS).band)
    }

    @Test
    fun `indirect sets do not count towards the volume`() {
        val rows = listOf(row(Muscle.BICEPS, 0.0, indirect = 15.0))
        val biceps = byMuscle(MuscleVolumeRules.volumes(rows, 1), Muscle.BICEPS)

        assertEquals(0.0, biceps.setsPerWeek, 0.0)
        assertEquals(VolumeBand.NONE, biceps.band)
    }

    @Test
    fun `fractional direct sets are banded by their value`() {
        // 0.5 sets from a shared exercise: the band is BELOW, not NONE.
        val volumes = MuscleVolumeRules.volumes(listOf(row(Muscle.HIPS, 0.5)), 1)

        assertEquals(VolumeBand.BELOW, byMuscle(volumes, Muscle.HIPS).band)
    }

    @Test
    fun `a week count of zero or less gives zero for every muscle`() {
        val rows = listOf(row(Muscle.CHEST, 12.0))

        val zero = MuscleVolumeRules.volumes(rows, 0)
        assertEquals(0.0, byMuscle(zero, Muscle.CHEST).setsPerWeek, 0.0)
        assertEquals(VolumeBand.NONE, byMuscle(zero, Muscle.CHEST).band)

        val negative = MuscleVolumeRules.volumes(rows, -1)
        assertEquals(0.0, byMuscle(negative, Muscle.CHEST).setsPerWeek, 0.0)
    }
}
