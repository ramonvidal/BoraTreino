package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLibraryKindsTest {

    private fun exercise(
        id: String,
        equipment: LibraryEquipment = LibraryEquipment.BARBELL,
        category: String = "strength",
    ) = LibraryExercise(
        id = id,
        name = id.replace('_', ' '),
        namePt = "",
        equipment = equipment,
        level = LibraryLevel.INTERMEDIATE,
        category = category,
        mechanic = null,
        force = null,
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        steps = listOf("Do it."),
    )

    @Test
    fun `carries and walks are weight and time exercises`() {
        listOf("Farmers_Walk", "Rickshaw_Carry", "Yoke_Walk").forEach { id ->
            assertEquals(id, MachineKind.WEIGHT_TIME, exercise(id, LibraryEquipment.OTHER).kind)
        }
    }

    @Test
    fun `sled exercises are weight and distance exercises`() {
        listOf("Sled_Push", "Sled_Drag_-_Harness", "Bear_Crawl_Sled_Drags", "Sled_Overhead_Backward_Walk").forEach { id ->
            assertEquals(id, MachineKind.WEIGHT_DISTANCE, exercise(id, LibraryEquipment.OTHER).kind)
        }
    }

    @Test
    fun `planks and side bridges are time only, even though they use the body`() {
        listOf("Plank", "Side_Bridge").forEach { id ->
            assertEquals(id, MachineKind.TIME, exercise(id, LibraryEquipment.BODY_ONLY).kind)
        }
    }

    @Test
    fun `other exercises keep the kind their equipment and category give`() {
        assertEquals(MachineKind.WEIGHT_REPS, exercise("Barbell_Bench_Press").kind)
        assertEquals(MachineKind.BODYWEIGHT_REPS, exercise("Push_Ups", LibraryEquipment.BODY_ONLY).kind)
        assertEquals(MachineKind.CARDIO, exercise("Running", category = "cardio").kind)
    }

    @Test
    fun `the library names of the three new starters are found by starter name`() {
        assertEquals("Farmers_Walk", ExerciseLibraryRules.idForStarter("Farmer's walk"))
        assertEquals("Plank", ExerciseLibraryRules.idForStarter("Plank"))
        assertEquals("Sled_Push", ExerciseLibraryRules.idForStarter("Sled push"))
    }

    @Test
    fun `each new starter has the kind of its library exercise`() {
        assertEquals(
            StarterMachines.all.single { it.name == "Farmer's walk" }.kind,
            exercise("Farmers_Walk", LibraryEquipment.OTHER).kind,
        )
        assertEquals(
            StarterMachines.all.single { it.name == "Plank" }.kind,
            exercise("Plank", LibraryEquipment.BODY_ONLY).kind,
        )
        assertEquals(
            StarterMachines.all.single { it.name == "Sled push" }.kind,
            exercise("Sled_Push", LibraryEquipment.OTHER).kind,
        )
    }
}
