package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineTest {
    private fun machine(
        name: String = "Leg press",
        stepKg: Double = 2.5,
        restSeconds: Int? = 90,
    ) = Machine(
        id = 1L,
        name = name,
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = stepKg,
        setupNote = "seat 5",
        restSeconds = restSeconds,
        archived = false,
    )

    @Test
    fun `a machine with a name, a step and a rest time is accepted`() {
        val machine = machine()
        assertEquals("Leg press", machine.name)
        assertEquals(2.5, machine.stepKg, 0.0)
        assertEquals(90, machine.restSeconds)
    }

    @Test
    fun `a blank name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { machine(name = "") }
        assertThrows(IllegalArgumentException::class.java) { machine(name = "   ") }
    }

    @Test
    fun `a step of zero is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { machine(stepKg = 0.0) }
    }

    @Test
    fun `a negative step is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { machine(stepKg = -1.0) }
    }

    @Test
    fun `steps of 1, 2_5 and 5 kg are accepted`() {
        listOf(1.0, 2.5, 5.0).forEach { step -> assertEquals(step, machine(stepKg = step).stepKg, 0.0) }
    }

    @Test
    fun `a rest time of zero is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { machine(restSeconds = 0) }
    }

    @Test
    fun `a negative rest time is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { machine(restSeconds = -30) }
    }

    @Test
    fun `a null rest time means the default and is accepted`() {
        assertNull(machine(restSeconds = null).restSeconds)
    }

    @Test
    fun `a null setup note is accepted`() {
        val machine = Machine(
            id = 2L,
            name = "Dips",
            muscleGroup = MuscleGroup.ARMS,
            kind = MachineKind.BODYWEIGHT_REPS,
            stepKg = 5.0,
            setupNote = null,
            restSeconds = null,
            archived = true,
        )
        assertNull(machine.setupNote)
        assertTrue(machine.archived)
    }
}
