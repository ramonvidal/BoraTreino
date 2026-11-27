package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineKindMeasureTest {

    @Test
    fun `reps kinds measure reps`() {
        assertEquals(SetMeasure.REPS, MachineKind.WEIGHT_REPS.measure)
        assertEquals(SetMeasure.REPS, MachineKind.BODYWEIGHT_REPS.measure)
    }

    @Test
    fun `weight and time and time only kinds measure seconds`() {
        assertEquals(SetMeasure.SECONDS, MachineKind.WEIGHT_TIME.measure)
        assertEquals(SetMeasure.SECONDS, MachineKind.TIME.measure)
    }

    @Test
    fun `weight and distance kind measures metres`() {
        assertEquals(SetMeasure.METRES, MachineKind.WEIGHT_DISTANCE.measure)
    }

    @Test
    fun `cardio measures nothing`() {
        assertNull(MachineKind.CARDIO.measure)
    }

    @Test
    fun `every kind except cardio is strength`() {
        for (kind in MachineKind.entries) {
            assertEquals(kind.name, kind != MachineKind.CARDIO, kind.isStrength)
        }
    }

    @Test
    fun `every kind except cardio and time only has a load`() {
        assertTrue(MachineKind.WEIGHT_REPS.hasLoad)
        assertTrue(MachineKind.BODYWEIGHT_REPS.hasLoad)
        assertTrue(MachineKind.WEIGHT_TIME.hasLoad)
        assertTrue(MachineKind.WEIGHT_DISTANCE.hasLoad)
        assertFalse(MachineKind.CARDIO.hasLoad)
        assertFalse(MachineKind.TIME.hasLoad)
    }

    @Test
    fun `the first three kinds keep their names and order, and the new ones follow them`() {
        assertEquals(
            listOf("WEIGHT_REPS", "BODYWEIGHT_REPS", "CARDIO"),
            MachineKind.entries.take(3).map { it.name },
        )
        assertEquals(
            listOf("WEIGHT_TIME", "WEIGHT_DISTANCE", "TIME"),
            MachineKind.entries.drop(3).map { it.name },
        )
    }

    @Test
    fun `a kind is read back by its stored name`() {
        for (kind in MachineKind.entries) {
            assertEquals(kind, MachineKind.valueOf(kind.name))
        }
    }
}
