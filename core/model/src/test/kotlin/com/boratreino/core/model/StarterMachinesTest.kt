package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterMachinesTest {
    private val all = StarterMachines.all

    @Test
    fun `every starter has a name`() {
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.name.isNotBlank() })
    }

    @Test
    fun `names are unique ignoring case`() {
        val names = all.map { it.name.lowercase() }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `there are about thirty starters`() {
        assertTrue("was ${all.size}", all.size in 25..35)
    }

    @Test
    fun `no starter is cardio yet`() {
        assertFalse(all.any { it.muscleGroup == MuscleGroup.CARDIO })
        assertFalse(all.any { it.kind == MachineKind.CARDIO })
    }

    @Test
    fun `every muscle group except cardio has at least one starter`() {
        val groups = MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }

        groups.forEach { group ->
            assertTrue("no starter for $group", all.any { it.muscleGroup == group })
        }
    }

    @Test
    fun `dips and pull-ups are bodyweight with reps`() {
        assertEquals(MachineKind.BODYWEIGHT_REPS, all.single { it.name == "Dips" }.kind)
        assertEquals(MachineKind.BODYWEIGHT_REPS, all.single { it.name == "Pull-ups" }.kind)
    }

    @Test
    fun `the other starters are weight and reps`() {
        val others = all.filter { it.name != "Dips" && it.name != "Pull-ups" }

        assertTrue(others.all { it.kind == MachineKind.WEIGHT_REPS })
    }

    @Test
    fun `leg press, hack squat, smith squat and calf raise step 5 kg`() {
        listOf("Leg press", "Hack squat", "Smith machine squat", "Calf raise").forEach { name ->
            assertEquals(name, 5.0, all.single { it.name == name }.stepKg, 0.0)
        }
    }

    @Test
    fun `every other starter steps 2 5 kg`() {
        val fiveKg = setOf("Leg press", "Hack squat", "Smith machine squat", "Calf raise")

        all.filter { it.name !in fiveKg }.forEach { starter ->
            assertEquals(starter.name, 2.5, starter.stepKg, 0.0)
        }
    }

    @Test
    fun `the starters are grouped as the contract lists them`() {
        val expected = mapOf(
            MuscleGroup.LEGS to setOf(
                "Leg press", "Leg extension", "Lying leg curl", "Seated leg curl", "Hip abductor",
                "Hip adductor", "Calf raise", "Hack squat", "Smith machine squat", "Glute kickback",
            ),
            MuscleGroup.CHEST to setOf(
                "Chest press", "Incline chest press", "Pec deck", "Cable crossover", "Dips",
            ),
            MuscleGroup.BACK to setOf(
                "Lat pulldown", "Seated row", "Assisted pull-up", "Back extension", "Pull-ups",
            ),
            MuscleGroup.SHOULDERS to setOf(
                "Shoulder press", "Lateral raise machine", "Rear delt fly", "Shrugs",
            ),
            MuscleGroup.ARMS to setOf(
                "Biceps curl machine", "Cable biceps curl", "Triceps pushdown", "Triceps extension machine",
            ),
            MuscleGroup.CORE to setOf(
                "Ab crunch machine", "Rotary torso",
            ),
        )

        expected.forEach { (group, names) ->
            assertEquals(group.name, names, all.filter { it.muscleGroup == group }.map { it.name }.toSet())
        }
    }
}
