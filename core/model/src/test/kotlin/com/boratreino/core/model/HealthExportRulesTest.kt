package com.boratreino.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HealthExportRulesTest {
    private val start = Instant.parse("2026-11-02T18:00:00Z")
    private val end = Instant.parse("2026-11-02T19:00:00Z")

    private fun session(id: Long = 5L, startedAt: Instant = start, endedAt: Instant? = end) =
        Session(id = id, startedAt = startedAt, endedAt = endedAt, routineId = null, note = null)

    private fun machine(id: Long, name: String, kind: MachineKind = MachineKind.CARDIO) = Machine(
        id = id,
        name = name,
        muscleGroup = if (kind == MachineKind.CARDIO) MuscleGroup.CARDIO else MuscleGroup.CHEST,
        kind = kind,
        stepKg = 1.0,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun machines(vararg all: Machine): Map<Long, Machine> = all.associateBy { it.id }

    private fun cardio(
        id: Long,
        machineId: Long,
        minutes: Int,
        calories: Int? = null,
        distanceM: Int? = null,
        position: Int = 0,
        sessionId: Long = 5L,
    ) = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = machineId,
        position = position,
        weightKg = null,
        reps = null,
        seconds = minutes * 60,
        calories = calories,
        distanceM = distanceM,
        loggedAt = start.plusSeconds(id * 60),
    )

    private fun strength(id: Long, machineId: Long = 9L, reps: Int = 10, weightKg: Double = 40.0, position: Int = 0) = WorkoutSet(
        id = id,
        sessionId = 5L,
        machineId = machineId,
        position = position,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = start.plusSeconds(id * 60),
    )

    private val treadmill = machine(1L, "Treadmill")
    private val press = machine(9L, "Chest press", MachineKind.WEIGHT_REPS)

    private fun export(sets: List<WorkoutSet>, ofSession: Session = session(), routine: String? = null) =
        HealthExportRules.export(ofSession, sets, machines(treadmill, press), routine)

    private fun kindOf(name: String): HealthExerciseKind {
        val m = machine(1L, name)
        return HealthExportRules.kindFor(listOf(cardio(1, 1L, 20)), machines(m))
    }

    // export: null cases

    @Test
    fun `an open session has no export`() {
        assertNull(export(listOf(strength(1)), session(endedAt = null)))
    }

    @Test
    fun `a session with no sets has no export`() {
        assertNull(export(emptyList()))
    }

    // export: content

    @Test
    fun `a strength session is a strength training with its sets and reps counted`() {
        val result = requireNotNull(export(listOf(strength(1, reps = 10), strength(2, reps = 8, position = 1))))

        assertEquals(HealthExerciseKind.STRENGTH_TRAINING, result.kind)
        assertEquals(2, result.strengthSets)
        assertEquals(18, result.strengthReps)
        assertEquals(0, result.activeCalories)
        assertEquals(0, result.distanceM)
        assertEquals(5L, result.sessionId)
        assertEquals("boratreino-session-5", result.clientRecordId)
        assertEquals(start, result.start)
        assertEquals(end, result.end)
    }

    @Test
    fun `a cardio-only session takes its kind from the machine and sums calories and distance`() {
        val sets = listOf(
            cardio(1, 1L, 20, calories = 150, distanceM = 3000),
            cardio(2, 1L, 10, calories = 80, distanceM = 1500, position = 1),
        )

        val result = requireNotNull(export(sets))

        assertEquals(HealthExerciseKind.TREADMILL, result.kind)
        assertEquals(230, result.activeCalories)
        assertEquals(4500, result.distanceM)
        assertEquals(0, result.strengthSets)
        assertEquals(0, result.strengthReps)
    }

    @Test
    fun `a mixed session is a strength training and only cardio counts toward calories and distance`() {
        val sets = listOf(strength(1, reps = 12), cardio(2, 1L, 15, calories = 100, distanceM = 2000))

        val result = requireNotNull(export(sets))

        assertEquals(HealthExerciseKind.STRENGTH_TRAINING, result.kind)
        assertEquals(100, result.activeCalories)
        assertEquals(2000, result.distanceM)
        assertEquals(1, result.strengthSets)
        assertEquals(12, result.strengthReps)
    }

    @Test
    fun `the title is the routine name, or Training without one`() {
        assertEquals("Legs", requireNotNull(export(listOf(strength(1)), routine = "Legs")).title)
        assertEquals("Training", requireNotNull(export(listOf(strength(1)), routine = null)).title)
    }

    @Test
    fun `a blank routine name falls back to Training`() {
        assertEquals("Training", requireNotNull(export(listOf(strength(1)), routine = "   ")).title)
    }

    // export: times

    @Test
    fun `end is at least one minute after start`() {
        val sameInstant = session(endedAt = start)

        val result = requireNotNull(export(listOf(strength(1)), sameInstant))

        assertEquals(start, result.start)
        assertTrue(!result.end.isBefore(start.plus(Duration.ofMinutes(1))))
    }

    @Test
    fun `a session shorter than a minute is stretched to one minute`() {
        val short = session(endedAt = start.plusSeconds(20))

        val result = requireNotNull(export(listOf(strength(1)), short))

        assertEquals(start.plus(Duration.ofMinutes(1)), result.end)
    }

    @Test
    fun `a long session keeps its own end`() {
        assertEquals(end, requireNotNull(export(listOf(strength(1)))).end)
    }

    // version

    @Test
    fun `the version is not negative`() {
        assertTrue(requireNotNull(export(listOf(strength(1)))).version >= 0)
        assertTrue(requireNotNull(export(listOf(cardio(1, 1L, 20, 100, 1000)), session(id = Long.MAX_VALUE))).version >= 0)
    }

    @Test
    fun `the same content gives the same version`() {
        val a = requireNotNull(export(listOf(strength(1), cardio(2, 1L, 20, 100))))
        val b = requireNotNull(export(listOf(strength(1), cardio(2, 1L, 20, 100))))

        assertEquals(a.version, b.version)
        assertEquals(a, b)
    }

    @Test
    fun `the version does not depend on the order the sets are given in`() {
        val first = strength(1, position = 0)
        val second = strength(2, position = 1)

        val a = requireNotNull(export(listOf(first, second)))
        val b = requireNotNull(export(listOf(second, first)))

        assertEquals(a.version, b.version)
    }

    @Test
    fun `the version changes when a set changes`() {
        val base = requireNotNull(export(listOf(strength(1, reps = 10)))).version

        assertNotEquals(base, requireNotNull(export(listOf(strength(1, reps = 11)))).version)
        assertNotEquals(base, requireNotNull(export(listOf(strength(1, weightKg = 42.5)))).version)
        assertNotEquals(base, requireNotNull(export(listOf(strength(1), strength(2, position = 1)))).version)
    }

    @Test
    fun `the version changes when a cardio value changes`() {
        val base = requireNotNull(export(listOf(cardio(1, 1L, 20, 100, 1000)))).version

        assertNotEquals(base, requireNotNull(export(listOf(cardio(1, 1L, 21, 100, 1000)))).version)
        assertNotEquals(base, requireNotNull(export(listOf(cardio(1, 1L, 20, 110, 1000)))).version)
        assertNotEquals(base, requireNotNull(export(listOf(cardio(1, 1L, 20, 100, 1100)))).version)
    }

    @Test
    fun `the version changes when the times change`() {
        val sets = listOf(strength(1))
        val base = requireNotNull(export(sets)).version

        assertNotEquals(base, requireNotNull(export(sets, session(endedAt = end.plusSeconds(300)))).version)
        assertNotEquals(base, requireNotNull(export(sets, session(startedAt = start.minusSeconds(300)))).version)
    }

    @Test
    fun `the version changes when the routine name changes`() {
        val sets = listOf(strength(1))

        assertNotEquals(
            requireNotNull(export(sets, routine = "Legs")).version,
            requireNotNull(export(sets, routine = "Back")).version,
        )
    }

    // kindFor

    @Test
    fun `any weight set makes the session a strength training`() {
        val kind = HealthExportRules.kindFor(
            listOf(cardio(1, 1L, 60), strength(2)),
            machines(treadmill, press),
        )

        assertEquals(HealthExerciseKind.STRENGTH_TRAINING, kind)
    }

    @Test
    fun `English machine names give their kind`() {
        assertEquals(HealthExerciseKind.TREADMILL, kindOf("Treadmill"))
        assertEquals(HealthExerciseKind.STATIONARY_BIKE, kindOf("Stationary bike"))
        assertEquals(HealthExerciseKind.STATIONARY_BIKE, kindOf("Spin bike"))
        assertEquals(HealthExerciseKind.ELLIPTICAL, kindOf("Elliptical"))
        assertEquals(HealthExerciseKind.ROWING_MACHINE, kindOf("Rowing machine"))
        assertEquals(HealthExerciseKind.STAIR_CLIMBING_MACHINE, kindOf("Stair climber"))
        assertEquals(HealthExerciseKind.WALKING, kindOf("Walking (outdoor)"))
        assertEquals(HealthExerciseKind.RUNNING, kindOf("Running (outdoor)"))
    }

    @Test
    fun `Portuguese machine names give their kind`() {
        assertEquals(HealthExerciseKind.TREADMILL, kindOf("Esteira"))
        assertEquals(HealthExerciseKind.STATIONARY_BIKE, kindOf("Bicicleta ergométrica"))
        assertEquals(HealthExerciseKind.STATIONARY_BIKE, kindOf("Bicicleta de spinning"))
        assertEquals(HealthExerciseKind.ELLIPTICAL, kindOf("Elíptico"))
        assertEquals(HealthExerciseKind.ROWING_MACHINE, kindOf("Remo ergométrico"))
        assertEquals(HealthExerciseKind.STAIR_CLIMBING_MACHINE, kindOf("Escada (simulador)"))
        assertEquals(HealthExerciseKind.WALKING, kindOf("Caminhada"))
        assertEquals(HealthExerciseKind.RUNNING, kindOf("Corrida"))
    }

    @Test
    fun `names are matched ignoring case and accents`() {
        assertEquals(HealthExerciseKind.TREADMILL, kindOf("ESTEIRA"))
        assertEquals(HealthExerciseKind.ELLIPTICAL, kindOf("ELIPTICO"))
        assertEquals(HealthExerciseKind.ELLIPTICAL, kindOf("eliptico"))
    }

    @Test
    fun `an unknown cardio machine is other`() {
        assertEquals(HealthExerciseKind.OTHER, kindOf("Jump rope"))
    }

    @Test
    fun `a cardio set on an unknown machine is other`() {
        assertEquals(HealthExerciseKind.OTHER, HealthExportRules.kindFor(listOf(cardio(1, 77L, 20)), machines(treadmill)))
    }

    @Test
    fun `the machine with the most minutes decides the kind`() {
        val bike = machine(2L, "Stationary bike")
        val sets = listOf(
            cardio(1, 1L, 10),
            cardio(2, 2L, 20, position = 0),
            cardio(3, 1L, 5, position = 1),
        )

        assertEquals(HealthExerciseKind.STATIONARY_BIKE, HealthExportRules.kindFor(sets, machines(treadmill, bike)))
    }

    @Test
    fun `minutes of one machine add up across its sets`() {
        val bike = machine(2L, "Stationary bike")
        val sets = listOf(
            cardio(1, 1L, 25),
            cardio(2, 2L, 15, position = 0),
            cardio(3, 2L, 15, position = 1),
        )

        assertEquals(HealthExerciseKind.STATIONARY_BIKE, HealthExportRules.kindFor(sets, machines(treadmill, bike)))
    }

    // client ids

    @Test
    fun `the client id is the prefix and the session id`() {
        assertEquals("boratreino-session-", HealthExportRules.CLIENT_ID_PREFIX)
        assertEquals("boratreino-session-42", HealthExportRules.clientId(42L))
    }

    @Test
    fun `the session id comes back from the client id and its suffixes`() {
        assertEquals(42L, HealthExportRules.sessionIdOf("boratreino-session-42"))
        assertEquals(42L, HealthExportRules.sessionIdOf("boratreino-session-42-cal"))
        assertEquals(42L, HealthExportRules.sessionIdOf("boratreino-session-42-dist"))
        assertEquals(Long.MAX_VALUE, HealthExportRules.sessionIdOf(HealthExportRules.clientId(Long.MAX_VALUE)))
    }

    @Test
    fun `client id round trips`() {
        for (id in listOf(0L, 1L, 7L, 1234567890123L)) {
            assertEquals(id, HealthExportRules.sessionIdOf(HealthExportRules.clientId(id)))
        }
    }

    @Test
    fun `an id that is not ours or is garbage gives null`() {
        assertNull(HealthExportRules.sessionIdOf(""))
        assertNull(HealthExportRules.sessionIdOf("garbage"))
        assertNull(HealthExportRules.sessionIdOf("boratreino-session-"))
        assertNull(HealthExportRules.sessionIdOf("boratreino-session-abc"))
        assertNull(HealthExportRules.sessionIdOf("other-session-42"))
        assertNull(HealthExportRules.sessionIdOf("boratreino-session-42-xyz"))
        assertNull(HealthExportRules.sessionIdOf("boratreino-session--5"))
        assertNull(HealthExportRules.sessionIdOf("42"))
        assertNull(HealthExportRules.sessionIdOf("boratreino-session-99999999999999999999"))
    }

    @Test
    fun `the export's client record id parses back to its session`() {
        val result = requireNotNull(export(listOf(strength(1)), session(id = 77L)))

        assertNotNull(result.clientRecordId)
        assertEquals(77L, HealthExportRules.sessionIdOf(result.clientRecordId))
    }
}
