package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The library's muscle names mapped to the app's muscle groups. */
class LibraryMuscleGroupTest {
    @Test
    fun `leg muscles map to legs`() {
        listOf("quadriceps", "hamstrings", "calves", "glutes", "adductors", "abductors").forEach {
            assertEquals(it, MuscleGroup.LEGS, libraryMuscleGroup(it))
        }
    }

    @Test
    fun `chest, back, shoulders, arms and abdominals map to their groups`() {
        assertEquals(MuscleGroup.CHEST, libraryMuscleGroup("chest"))
        listOf("lats", "middle back", "lower back", "traps").forEach {
            assertEquals(it, MuscleGroup.BACK, libraryMuscleGroup(it))
        }
        listOf("shoulders", "neck").forEach {
            assertEquals(it, MuscleGroup.SHOULDERS, libraryMuscleGroup(it))
        }
        listOf("biceps", "triceps", "forearms").forEach {
            assertEquals(it, MuscleGroup.ARMS, libraryMuscleGroup(it))
        }
        assertEquals(MuscleGroup.CORE, libraryMuscleGroup("abdominals"))
    }

    @Test
    fun `names are matched ignoring case and surrounding spaces`() {
        assertEquals(MuscleGroup.LEGS, libraryMuscleGroup("Quadriceps"))
        assertEquals(MuscleGroup.BACK, libraryMuscleGroup("  LATS "))
        assertEquals(MuscleGroup.SHOULDERS, libraryMuscleGroup("\tNeck\n"))
    }

    @Test
    fun `unknown, blank and cardio-like names map to nothing`() {
        assertNull(libraryMuscleGroup("abs"))
        assertNull(libraryMuscleGroup("cardio"))
        assertNull(libraryMuscleGroup("unknown muscle"))
        assertNull(libraryMuscleGroup(""))
        assertNull(libraryMuscleGroup("   "))
    }

    @Test
    fun `a library exercise takes the group of its first primary muscle`() {
        val exercise = LibraryExercise(
            id = "row",
            name = "Row",
            namePt = "Remada",
            equipment = LibraryEquipment.CABLE,
            level = LibraryLevel.BEGINNER,
            category = "strength",
            mechanic = null,
            force = "pull",
            primaryMuscles = listOf("Lats", "biceps"),
            secondaryMuscles = listOf("shoulders"),
            steps = emptyList(),
        )

        assertEquals(MuscleGroup.BACK, exercise.muscleGroup)
    }

    @Test
    fun `a library exercise with an unknown primary muscle is core and cardio is cardio`() {
        val base = LibraryExercise(
            id = "x",
            name = "X",
            namePt = "X",
            equipment = LibraryEquipment.OTHER,
            level = LibraryLevel.BEGINNER,
            category = "strength",
            mechanic = null,
            force = null,
            primaryMuscles = listOf("unknown muscle"),
            secondaryMuscles = emptyList(),
            steps = emptyList(),
        )

        assertEquals(MuscleGroup.CORE, base.muscleGroup)
        assertEquals(MuscleGroup.CARDIO, base.copy(category = "cardio").muscleGroup)
    }
}
