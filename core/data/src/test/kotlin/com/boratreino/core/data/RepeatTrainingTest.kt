package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** "Same as last time" from Today: the offer, the repeat and its undo. */
class RepeatTrainingTest {
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: TrainingRepository
    private lateinit var routines: RoutineRepository
    private lateinit var machines: MachineRepository

    private var press = 0L
    private var lat = 0L
    private var curl = 0L
    private var routineA = 0L
    private var routineB = 0L

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(now)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String, kind: MachineKind = MachineKind.WEIGHT_REPS): Long =
        machines.save(Machine(0, name, MuscleGroup.BACK, kind, 2.5, null, null, false))

    private suspend fun routine(shortName: String, vararg machineIds: Long): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = emptySet()),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, 3, 8, 12) },
    )

    private suspend fun pastSession(startedAt: Instant, routineId: Long?): Long =
        store.insertSession(Session(id = 0, startedAt = startedAt, endedAt = startedAt.plusSeconds(3600), routineId = routineId, note = null))

    private suspend fun pastSet(sessionId: Long, machineId: Long, position: Int, kg: Double, reps: Int, at: Instant) =
        store.insertSet(
            WorkoutSet(0, sessionId, machineId, position, kg, reps, null, null, null, at),
        )

    /**
     * Routine A = press, lat, curl. An earlier session of A: press 3 x 40 kg x 12 (hits the top of the range),
     * lat 2 sets (30 x 10, 30 x 9), curl nothing. A is the chosen routine.
     */
    private suspend fun withEarlierSessionOfA(): Long {
        press = machine("Leg press")
        lat = machine("Lat pulldown")
        curl = machine("Curl")
        routineA = routine("A", press, lat, curl)
        val start = now.minus(Duration.ofDays(2))
        val earlier = pastSession(start, routineA)
        repeat(3) { pastSet(earlier, press, it, 40.0, 12, start.plusSeconds(60L * it)) }
        pastSet(earlier, lat, 0, 30.0, 10, start.plusSeconds(600))
        pastSet(earlier, lat, 1, 30.0, 9, start.plusSeconds(660))
        training.chooseRoutine(RoutineChoice.Chosen(routineA))
        return earlier
    }

    private fun setsOf(sessionId: Long, machineId: Long) =
        store.allSets().filter { it.sessionId == sessionId && it.machineId == machineId }.sortedBy { it.position }

    // observeRepeatOffer

    @Test
    fun `the offer counts the machines and sets of the earlier session of the routine`() = runTest {
        withEarlierSessionOfA()

        val offer = training.observeRepeatOffer().first()

        assertEquals(RepeatOffer(routineA, machineCount = 2, setCount = 5), offer)
    }

    @Test
    fun `no offer for a Free training`() = runTest {
        withEarlierSessionOfA()
        training.chooseRoutine(RoutineChoice.Free)

        assertNull(training.observeRepeatOffer().first())
    }

    @Test
    fun `no offer without an earlier session of the routine`() = runTest {
        withEarlierSessionOfA()
        routineB = routine("B", curl)
        training.chooseRoutine(RoutineChoice.Chosen(routineB))

        assertNull(training.observeRepeatOffer().first())
    }

    @Test
    fun `an earlier session of another routine does not count`() = runTest {
        press = machine("Leg press")
        routineA = routine("A", press)
        routineB = routine("B", press)
        val other = pastSession(now.minus(Duration.ofDays(2)), routineB)
        pastSet(other, press, 0, 40.0, 10, now.minus(Duration.ofDays(2)))
        training.chooseRoutine(RoutineChoice.Chosen(routineA))

        assertNull(training.observeRepeatOffer().first())
    }

    @Test
    fun `no offer when every machine with earlier sets already has sets today`() = runTest {
        withEarlierSessionOfA()
        training.logSet(press, SetDraft(40.0, 10))
        training.logSet(lat, SetDraft(30.0, 10))

        assertNull(training.observeRepeatOffer().first())
    }

    @Test
    fun `the offer leaves out machines that already have sets today`() = runTest {
        withEarlierSessionOfA()
        training.logSet(press, SetDraft(40.0, 10))

        assertEquals(RepeatOffer(routineA, machineCount = 1, setCount = 2), training.observeRepeatOffer().first())
    }

    @Test
    fun `the offer leaves out machines skipped for this training`() = runTest {
        withEarlierSessionOfA()
        training.skipMachineToday(lat)

        assertEquals(RepeatOffer(routineA, machineCount = 1, setCount = 3), training.observeRepeatOffer().first())
    }

    // repeatLastTraining

    @Test
    fun `repeating opens a session with the active routine when none is open`() = runTest {
        withEarlierSessionOfA()
        assertNull(training.observeOpenSession().first())

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(open.session.id, outcome.sessionId)
        assertEquals(routineA, open.session.routineId)
        assertEquals(now, open.session.startedAt)
        assertTrue(outcome.sessionCreated)
        assertEquals(2, outcome.machineCount)
        assertEquals(5, outcome.setIds.size)
    }

    @Test
    fun `repeating copies sets, weights and reps per machine with positions from zero`() = runTest {
        withEarlierSessionOfA()

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        val pressSets = setsOf(outcome.sessionId, press)
        assertEquals(listOf(0, 1, 2), pressSets.map { it.position })
        assertEquals(listOf(40.0, 40.0, 40.0), pressSets.map { it.weightKg })
        assertEquals(listOf(12, 12, 12), pressSets.map { it.reps })
        val latSets = setsOf(outcome.sessionId, lat)
        assertEquals(listOf(0, 1), latSets.map { it.position })
        assertEquals(listOf(30.0, 30.0), latSets.map { it.weightKg })
        assertEquals(listOf(10, 9), latSets.map { it.reps })
        assertTrue(setsOf(outcome.sessionId, curl).isEmpty())
    }

    @Test
    fun `repeated sets are logged at now, one second apart in order`() = runTest {
        withEarlierSessionOfA()

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        val written = store.allSets().filter { it.sessionId == outcome.sessionId }.sortedBy { it.id }
        assertEquals(5, written.size)
        assertEquals(now, written.first().loggedAt)
        assertEquals(written.map { it.loggedAt }.sorted(), written.map { it.loggedAt })
        assertEquals(5, written.map { it.loggedAt }.distinct().size)
        assertTrue(written.all { it.loggedAt >= now && it.loggedAt <= now.plusSeconds(10) })
    }

    @Test
    fun `repeating fills an open session and leaves machines with sets alone`() = runTest {
        withEarlierSessionOfA()
        val first = training.logSet(press, SetDraft(45.0, 6)).set

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        assertEquals(first.sessionId, outcome.sessionId)
        assertEquals(false, outcome.sessionCreated)
        assertEquals(1, outcome.machineCount)
        assertEquals(listOf(first), setsOf(first.sessionId, press))
        assertEquals(2, setsOf(first.sessionId, lat).size)
        assertEquals(1, store.allSessions().count { it.endedAt == null })
    }

    @Test
    fun `machines skipped for this training stay out of the repeat`() = runTest {
        withEarlierSessionOfA()
        training.skipMachineToday(lat)

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        assertEquals(1, outcome.machineCount)
        assertEquals(3, outcome.setIds.size)
        assertTrue(setsOf(outcome.sessionId, lat).isEmpty())
    }

    @Test
    fun `suggested mode uses the progression suggestion for every set of the machine`() = runTest {
        withEarlierSessionOfA()

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SUGGESTED))

        val pressSets = setsOf(outcome.sessionId, press)
        assertEquals(3, pressSets.size)
        assertEquals(listOf(42.5, 42.5, 42.5), pressSets.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), pressSets.map { it.reps })
        // No suggestion for the lat pulldown: as last time.
        assertEquals(listOf(10, 9), setsOf(outcome.sessionId, lat).map { it.reps })
        assertEquals(listOf(30.0, 30.0), setsOf(outcome.sessionId, lat).map { it.weightKg })
    }

    @Test
    fun `cardio is copied with seconds, calories and distance`() = runTest {
        val bike = machine("Bike", MachineKind.CARDIO)
        routineA = routine("A", bike)
        val start = now.minus(Duration.ofDays(2))
        val earlier = pastSession(start, routineA)
        store.insertSet(WorkoutSet(0, earlier, bike, 0, null, null, 1200, 150, 5000, start))
        training.chooseRoutine(RoutineChoice.Chosen(routineA))

        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SUGGESTED))

        val copied = setsOf(outcome.sessionId, bike).single()
        assertEquals(1200, copied.seconds)
        assertEquals(150, copied.calories)
        assertEquals(5000, copied.distanceM)
        assertNull(copied.weightKg)
    }

    @Test
    fun `repeating returns null when there is nothing to repeat`() = runTest {
        withEarlierSessionOfA()
        training.chooseRoutine(RoutineChoice.Free)
        assertNull(training.repeatLastTraining(RepeatMode.SAME))

        routineB = routine("B", curl)
        training.chooseRoutine(RoutineChoice.Chosen(routineB))
        assertNull(training.repeatLastTraining(RepeatMode.SAME))

        assertEquals(1, store.allSessions().size)
    }

    @Test
    fun `repeating twice does not duplicate machines`() = runTest {
        withEarlierSessionOfA()
        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        assertNull(training.repeatLastTraining(RepeatMode.SAME))
        assertEquals(5, store.allSets().count { it.sessionId == outcome.sessionId })
    }

    // undoRepeat

    @Test
    fun `undo deletes the sets and the session the repeat created`() = runTest {
        withEarlierSessionOfA()
        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        training.undoRepeat(outcome)

        assertNull(training.observeOpenSession().first())
        assertTrue(store.allSessions().none { it.id == outcome.sessionId })
        assertTrue(store.allSets().none { it.sessionId == outcome.sessionId })
    }

    @Test
    fun `undo keeps an open session that was there before`() = runTest {
        withEarlierSessionOfA()
        val first = training.logSet(press, SetDraft(45.0, 6)).set
        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))

        training.undoRepeat(outcome)

        val open = requireNotNull(training.observeOpenSession().first())
        assertEquals(first.sessionId, open.session.id)
        assertEquals(listOf(first), store.allSets().filter { it.sessionId == first.sessionId })
    }

    @Test
    fun `undo keeps a created session that got other sets since`() = runTest {
        withEarlierSessionOfA()
        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))
        val extra = training.logSet(curl, SetDraft(10.0, 10)).set

        training.undoRepeat(outcome)

        assertNotNull(store.session(outcome.sessionId))
        assertEquals(listOf(extra.id), store.allSets().filter { it.sessionId == outcome.sessionId }.map { it.id })
    }

    @Test
    fun `the offer is back after an undo`() = runTest {
        withEarlierSessionOfA()
        val before = training.observeRepeatOffer().first()
        val outcome = requireNotNull(training.repeatLastTraining(RepeatMode.SAME))
        assertNull(training.observeRepeatOffer().first())

        training.undoRepeat(outcome)

        assertEquals(before, training.observeRepeatOffer().first())
    }
}
