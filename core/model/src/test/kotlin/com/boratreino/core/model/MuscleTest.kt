package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The specific muscles: their groups, their order, and the library names they come from. */
class MuscleTest {
    @Test
    fun `there are fourteen muscles in display order`() {
        assertEquals(
            listOf(
                "CHEST", "SHOULDERS", "BICEPS", "TRICEPS", "FOREARMS", "ABS", "QUADRICEPS", "HAMSTRINGS",
                "GLUTES", "CALVES", "HIPS", "UPPER_BACK", "TRAPS", "LOWER_BACK",
            ),
            Muscle.entries.map { it.name },
        )
    }

    @Test
    fun `chest and shoulders are their own groups`() {
        assertEquals(listOf(Muscle.CHEST), Muscle.ofGroup(MuscleGroup.CHEST))
        assertEquals(listOf(Muscle.SHOULDERS), Muscle.ofGroup(MuscleGroup.SHOULDERS))
    }

    @Test
    fun `arms are biceps triceps and forearms in that order`() {
        assertEquals(listOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS), Muscle.ofGroup(MuscleGroup.ARMS))
    }

    @Test
    fun `core is the abs`() {
        assertEquals(listOf(Muscle.ABS), Muscle.ofGroup(MuscleGroup.CORE))
    }

    @Test
    fun `legs are quadriceps hamstrings glutes calves and hips`() {
        assertEquals(
            listOf(Muscle.QUADRICEPS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.HIPS),
            Muscle.ofGroup(MuscleGroup.LEGS),
        )
    }

    @Test
    fun `back is upper back traps and lower back`() {
        assertEquals(
            listOf(Muscle.UPPER_BACK, Muscle.TRAPS, Muscle.LOWER_BACK),
            Muscle.ofGroup(MuscleGroup.BACK),
        )
    }

    @Test
    fun `cardio has no muscles`() {
        assertTrue(Muscle.ofGroup(MuscleGroup.CARDIO).isEmpty())
    }

    @Test
    fun `every muscle belongs to one group and the groups of ofGroup cover all of them`() {
        val fromGroups = MuscleGroup.entries.flatMap { Muscle.ofGroup(it) }

        assertEquals(Muscle.entries.toSet(), fromGroups.toSet())
        assertEquals(Muscle.entries.size, fromGroups.size)
        Muscle.entries.forEach { muscle ->
            assertTrue(muscle.group != MuscleGroup.CARDIO)
            assertTrue(Muscle.ofGroup(muscle.group).contains(muscle))
        }
    }

    @Test
    fun `each muscle has the group it is listed under`() {
        assertEquals(MuscleGroup.CHEST, Muscle.CHEST.group)
        assertEquals(MuscleGroup.SHOULDERS, Muscle.SHOULDERS.group)
        assertEquals(MuscleGroup.ARMS, Muscle.BICEPS.group)
        assertEquals(MuscleGroup.ARMS, Muscle.TRICEPS.group)
        assertEquals(MuscleGroup.ARMS, Muscle.FOREARMS.group)
        assertEquals(MuscleGroup.CORE, Muscle.ABS.group)
        assertEquals(MuscleGroup.LEGS, Muscle.QUADRICEPS.group)
        assertEquals(MuscleGroup.LEGS, Muscle.HAMSTRINGS.group)
        assertEquals(MuscleGroup.LEGS, Muscle.GLUTES.group)
        assertEquals(MuscleGroup.LEGS, Muscle.CALVES.group)
        assertEquals(MuscleGroup.LEGS, Muscle.HIPS.group)
        assertEquals(MuscleGroup.BACK, Muscle.UPPER_BACK.group)
        assertEquals(MuscleGroup.BACK, Muscle.TRAPS.group)
        assertEquals(MuscleGroup.BACK, Muscle.LOWER_BACK.group)
    }

    @Test
    fun `each library muscle name maps to its muscle`() {
        assertEquals(Muscle.QUADRICEPS, libraryMuscle("quadriceps"))
        assertEquals(Muscle.HAMSTRINGS, libraryMuscle("hamstrings"))
        assertEquals(Muscle.GLUTES, libraryMuscle("glutes"))
        assertEquals(Muscle.CALVES, libraryMuscle("calves"))
        assertEquals(Muscle.HIPS, libraryMuscle("adductors"))
        assertEquals(Muscle.HIPS, libraryMuscle("abductors"))
        assertEquals(Muscle.CHEST, libraryMuscle("chest"))
        assertEquals(Muscle.UPPER_BACK, libraryMuscle("lats"))
        assertEquals(Muscle.UPPER_BACK, libraryMuscle("middle back"))
        assertEquals(Muscle.TRAPS, libraryMuscle("traps"))
        assertEquals(Muscle.TRAPS, libraryMuscle("neck"))
        assertEquals(Muscle.LOWER_BACK, libraryMuscle("lower back"))
        assertEquals(Muscle.SHOULDERS, libraryMuscle("shoulders"))
        assertEquals(Muscle.BICEPS, libraryMuscle("biceps"))
        assertEquals(Muscle.TRICEPS, libraryMuscle("triceps"))
        assertEquals(Muscle.FOREARMS, libraryMuscle("forearms"))
        assertEquals(Muscle.ABS, libraryMuscle("abdominals"))
    }

    @Test
    fun `every library name that is known gives a muscle and an unknown name gives null`() {
        val known = listOf(
            "quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors", "chest", "lats",
            "middle back", "traps", "neck", "lower back", "shoulders", "biceps", "triceps", "forearms", "abdominals",
        )
        known.forEach { assertTrue(it, libraryMuscle(it) != null) }

        assertNull(libraryMuscle("pectorals"))
        assertNull(libraryMuscle("hands"))
        assertNull(libraryMuscle(""))
    }

    @Test
    fun `library muscle names ignore case and surrounding spaces`() {
        assertEquals(Muscle.QUADRICEPS, libraryMuscle("  Quadriceps "))
        assertEquals(Muscle.UPPER_BACK, libraryMuscle("MIDDLE BACK"))
        assertEquals(Muscle.LOWER_BACK, libraryMuscle("Lower Back"))
        assertEquals(Muscle.ABS, libraryMuscle("\tAbdominals\n"))
    }

    @Test
    fun `an inner space is not trimmed away`() {
        assertNull(libraryMuscle("middleback"))
        assertNull(libraryMuscle("lower  back"))
    }
}
