package com.boratreino.core.database

import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SetTypeMappersTest {
    private fun entity(type: String) = WorkoutSetEntity(
        id = 1,
        sessionId = 2,
        machineId = 3,
        position = 0,
        weightKg = 40.0,
        reps = 10,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = 1_000L,
        type = type,
    )

    @Test
    fun `an entity is normal by default`() {
        val e = WorkoutSetEntity(sessionId = 1, machineId = 1, position = 0, weightKg = null, reps = null, seconds = null, calories = null, distanceM = null, loggedAt = 0)

        assertEquals("NORMAL", e.type)
        assertEquals(SetType.NORMAL, e.toModel().type)
    }

    @Test
    fun `every type name reads as its type`() {
        SetType.entries.forEach { assertEquals(it, entity(it.name).toModel().type) }
    }

    @Test
    fun `an unknown type string reads as normal`() {
        assertEquals(SetType.NORMAL, entity("SUPERSET").toModel().type)
        assertEquals(SetType.NORMAL, entity("").toModel().type)
        assertEquals(SetType.NORMAL, entity("warmup").toModel().type)
    }

    @Test
    fun `a model set writes its type name and reads it back`() {
        SetType.entries.forEach { type ->
            val set = WorkoutSet(5, 2, 3, 1, 40.0, 10, null, null, null, Instant.ofEpochMilli(5_000), type)

            val entity = set.toEntity()

            assertEquals(type.name, entity.type)
            assertEquals(set, entity.toModel())
        }
    }
}
