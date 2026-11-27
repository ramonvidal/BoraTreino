package com.boratreino.core.data

import com.boratreino.core.model.BackupCodec
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupDecodeResult
import com.boratreino.core.model.KindConversion
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Record
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
import java.time.Instant
import java.time.LocalDate

/** Timed and distance machines in the data layer: logging, kind changes and suggestions. */
class TimedDistanceMachineDataTest {
    private val t0: Instant = Instant.parse("2026-11-20T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var machines: MachineRepository
    private lateinit var training: DefaultTrainingRepository
    private lateinit var history: HistoryRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(t0)
        training = DefaultTrainingRepository(store, clock, DefaultRoutineRepository(store))
        machines = DefaultMachineRepository(store)
        history = DefaultHistoryRepository(store, clock, training)
    }

    private suspend fun machine(
        name: String,
        kind: MachineKind,
        group: MuscleGroup = MuscleGroup.LEGS,
        stepKg: Double = 2.5,
    ): Long = machines.save(Machine(0, name, group, kind, stepKg, null, null, false))

    private suspend fun stored(id: Long): Machine = requireNotNull(machines.observeMachine(id).first())

    /** Every set in the store, in position order (FakeWorkoutStore keeps them all). */
    private fun setsOf(machineId: Long): List<WorkoutSet> =
        store.allSets().filter { it.machineId == machineId }.sortedBy { it.position }

    // logSet shapes

    @Test
    fun `a time only set is logged with weight 0 kg, its seconds and no reps`() = runTest {
        val plank = machine("Plank", MachineKind.TIME, MuscleGroup.CORE)

        val set = training.logSet(plank, SetDraft(25.0, 0, seconds = 45)).set

        assertEquals(0.0, set.weightKg!!, 0.0)
        assertEquals(45, set.seconds)
        assertNull(set.reps)
        assertNull(set.distanceM)
        assertEquals(listOf(set), setsOf(plank))
    }

    @Test
    fun `a weight and time set keeps its load, its seconds and no reps`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_TIME, MuscleGroup.ARMS, stepKg = 2.0)

        val set = training.logSet(farmers, SetDraft(47.5, 0, seconds = 30)).set

        assertEquals(47.5, set.weightKg!!, 0.0)
        assertEquals(30, set.seconds)
        assertNull(set.reps)
        assertNull(set.distanceM)
    }

    @Test
    fun `a weight and distance set keeps its load, its metres and no reps or seconds`() = runTest {
        val sled = machine("Sled push", MachineKind.WEIGHT_DISTANCE, stepKg = 5.0)

        val set = training.logSet(sled, SetDraft(40.0, 0, distanceM = 25)).set

        assertEquals(40.0, set.weightKg!!, 0.0)
        assertEquals(25, set.distanceM)
        assertNull(set.reps)
        assertNull(set.seconds)
    }

    @Test
    fun `a reps set keeps its reps and has no seconds or metres`() = runTest {
        val press = machine("Leg press", MachineKind.WEIGHT_REPS)

        val set = training.logSet(press, SetDraft(120.0, 12)).set

        assertEquals(120.0, set.weightKg!!, 0.0)
        assertEquals(12, set.reps)
        assertNull(set.seconds)
        assertNull(set.distanceM)
    }

    @Test
    fun `a bodyweight set keeps its added weight and reps`() = runTest {
        val dips = machine("Dips", MachineKind.BODYWEIGHT_REPS, MuscleGroup.CHEST)

        val set = training.logSet(dips, SetDraft(10.0, 8)).set

        assertEquals(10.0, set.weightKg!!, 0.0)
        assertEquals(8, set.reps)
    }

    @Test
    fun `a longer hold with no load is a longest time record`() = runTest {
        val plank = machine("Plank", MachineKind.TIME, MuscleGroup.CORE)
        training.logSet(plank, SetDraft(0.0, 0, seconds = 30))

        val second = training.logSet(plank, SetDraft(0.0, 0, seconds = 45))

        assertEquals(Record.LongestTime(0.0, 45), second.record)
    }

    @Test
    fun `the first timed set is not a record`() = runTest {
        val plank = machine("Plank", MachineKind.TIME, MuscleGroup.CORE)

        assertNull(training.logSet(plank, SetDraft(0.0, 0, seconds = 30)).record)
    }

    // save and kind changes

    @Test
    fun `saving a kind change on a machine with sets is refused and changes nothing`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)
        training.logSet(farmers, SetDraft(45.0, 1))
        val changed = stored(farmers).copy(kind = MachineKind.WEIGHT_TIME)

        val thrown = try {
            machines.save(changed)
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull(thrown)
        assertEquals(MachineKind.WEIGHT_REPS, stored(farmers).kind)
        assertEquals(listOf(1), setsOf(farmers).map { it.reps })
    }

    @Test
    fun `a kind change on a machine with no sets is saved as usual`() = runTest {
        val plank = machine("Plank", MachineKind.TIME, MuscleGroup.CORE)

        machines.save(stored(plank).copy(kind = MachineKind.WEIGHT_TIME))

        assertEquals(MachineKind.WEIGHT_TIME, stored(plank).kind)
    }

    @Test
    fun `a rename of a machine with sets is saved as usual`() = runTest {
        val press = machine("Leg press", MachineKind.WEIGHT_REPS)
        training.logSet(press, SetDraft(120.0, 12))

        machines.save(stored(press).copy(name = "Leg press 45"))

        assertEquals("Leg press 45", stored(press).name)
    }

    // kind conversions of a stored machine

    @Test
    fun `kind conversions are read from the stored kind and its sets`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)
        training.logSet(farmers, SetDraft(45.0, 1))
        training.logSet(farmers, SetDraft(47.5, 1))
        training.logSet(farmers, SetDraft(48.0, 1))

        val offers = machines.kindConversions(farmers)

        assertEquals(
            MachineKind.entries.filter { it != MachineKind.WEIGHT_REPS }.toSet(),
            offers.keys,
        )
        val timed = offers.getValue(MachineKind.WEIGHT_TIME)
        assertTrue(timed is KindConversion.NeedsFactor)
        assertEquals(3, (timed as KindConversion.NeedsFactor).setCount)
    }

    @Test
    fun `kind conversions of an unknown machine are empty`() = runTest {
        assertTrue(machines.kindConversions(999L).isEmpty())
    }

    // saveConvertingSets

    @Test
    fun `farmers walk converts from reps to seconds at the factor, keeping each load, in one save`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)
        training.logSet(farmers, SetDraft(45.0, 1))
        training.logSet(farmers, SetDraft(47.5, 1))
        training.logSet(farmers, SetDraft(48.0, 1))

        val saved = machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_TIME), 30)

        assertTrue(saved)
        assertEquals(MachineKind.WEIGHT_TIME, stored(farmers).kind)
        val sets = setsOf(farmers)
        assertEquals(listOf(45.0, 47.5, 48.0), sets.map { it.weightKg })
        assertEquals(listOf(30, 30, 30), sets.map { it.seconds })
        assertTrue(sets.all { it.reps == null && it.distanceM == null })
    }

    @Test
    fun `converting reps to seconds and back with the same factor restores the reps`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)
        training.logSet(farmers, SetDraft(45.0, 1))
        training.logSet(farmers, SetDraft(40.0, 8))
        val before = setsOf(farmers)

        machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_TIME), 30)
        machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_REPS), 30)

        assertEquals(MachineKind.WEIGHT_REPS, stored(farmers).kind)
        assertEquals(before, setsOf(farmers))
    }

    @Test
    fun `a missing factor for seconds changes nothing and returns false`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)
        training.logSet(farmers, SetDraft(45.0, 1))
        val before = setsOf(farmers)

        val saved = machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_TIME), null)

        assertEquals(false, saved)
        assertEquals(MachineKind.WEIGHT_REPS, stored(farmers).kind)
        assertEquals(before, setsOf(farmers))
    }

    @Test
    fun `a factor out of range changes nothing and returns false`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)
        training.logSet(farmers, SetDraft(45.0, 1))
        training.logSet(farmers, SetDraft(47.5, 3))
        val before = setsOf(farmers)

        assertEquals(false, machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_TIME), 0))
        assertEquals(false, machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_TIME), 601))

        assertEquals(MachineKind.WEIGHT_REPS, stored(farmers).kind)
        assertEquals(before, setsOf(farmers))
    }

    @Test
    fun `metres without a factor changes nothing and returns false`() = runTest {
        val sled = machine("Sled push", MachineKind.WEIGHT_REPS, stepKg = 5.0)
        training.logSet(sled, SetDraft(40.0, 4))
        val before = setsOf(sled)

        assertEquals(false, machines.saveConvertingSets(stored(sled).copy(kind = MachineKind.WEIGHT_DISTANCE), null))

        assertEquals(MachineKind.WEIGHT_REPS, stored(sled).kind)
        assertEquals(before, setsOf(sled))
    }

    @Test
    fun `a conversion that is not offered changes nothing and returns false`() = runTest {
        val press = machine("Leg press", MachineKind.WEIGHT_REPS)
        training.logSet(press, SetDraft(120.0, 12))
        val before = setsOf(press)

        assertEquals(false, machines.saveConvertingSets(stored(press).copy(kind = MachineKind.CARDIO), null))
        assertEquals(false, machines.saveConvertingSets(stored(press).copy(kind = MachineKind.TIME), 30))

        assertEquals(MachineKind.WEIGHT_REPS, stored(press).kind)
        assertEquals(before, setsOf(press))
    }

    @Test
    fun `back extension from bodyweight to weight and reps keeps its numbers`() = runTest {
        val back = machine("Back extension", MachineKind.BODYWEIGHT_REPS, MuscleGroup.BACK)
        training.logSet(back, SetDraft(10.0, 12))

        val saved = machines.saveConvertingSets(stored(back).copy(kind = MachineKind.WEIGHT_REPS), null)

        assertTrue(saved)
        assertEquals(MachineKind.WEIGHT_REPS, stored(back).kind)
        val set = setsOf(back).single()
        assertEquals(10.0, set.weightKg!!, 0.0)
        assertEquals(12, set.reps)
    }

    @Test
    fun `a machine with no sets changes kind without a factor`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_REPS)

        assertTrue(machines.saveConvertingSets(stored(farmers).copy(kind = MachineKind.WEIGHT_TIME), null))

        assertEquals(MachineKind.WEIGHT_TIME, stored(farmers).kind)
    }

    // history writes

    @Test
    fun `adding a set to a past session writes the shape of the machine's kind`() = runTest {
        val plank = machine("Plank", MachineKind.TIME, MuscleGroup.CORE)
        val first = history.addSessionWithSet(LocalDate.of(2026, 11, 10), null, false, plank, SetDraft(20.0, 0, seconds = 60))

        assertEquals(0.0, first.weightKg!!, 0.0)
        assertEquals(60, first.seconds)
        assertNull(first.reps)

        val added = history.addSet(first.sessionId, plank, SetDraft(20.0, 0, seconds = 45))

        assertEquals(0.0, added.weightKg!!, 0.0)
        assertEquals(45, added.seconds)
        assertNull(added.reps)
    }

    @Test
    fun `adding a distance set to a past session writes metres and no reps`() = runTest {
        val sled = machine("Sled push", MachineKind.WEIGHT_DISTANCE, stepKg = 5.0)

        val added = history.addSessionWithSet(LocalDate.of(2026, 11, 10), null, false, sled, SetDraft(40.0, 0, distanceM = 25))

        assertEquals(40.0, added.weightKg!!, 0.0)
        assertEquals(25, added.distanceM)
        assertNull(added.reps)
        assertNull(added.seconds)
    }

    @Test
    fun `adding a reps set to a past session keeps reps`() = runTest {
        val press = machine("Leg press", MachineKind.WEIGHT_REPS)

        val added = history.addSessionWithSet(LocalDate.of(2026, 11, 10), null, false, press, SetDraft(120.0, 12))

        assertEquals(12, added.reps)
        assertNull(added.seconds)
        assertNull(added.distanceM)
    }

    // backup

    private val kindsBackup = BackupData(
        machines = listOf(
            Machine(1L, "Farmer's walk", MuscleGroup.ARMS, MachineKind.WEIGHT_TIME, 2.0, null, null, false),
            Machine(2L, "Sled push", MuscleGroup.LEGS, MachineKind.WEIGHT_DISTANCE, 5.0, null, null, false),
            Machine(3L, "Plank", MuscleGroup.CORE, MachineKind.TIME, 2.5, null, null, false),
        ),
        plans = emptyList(),
        routines = emptyList(),
        items = emptyList(),
        sessions = listOf(Session(1L, t0, null, null, null)),
        sets = listOf(
            WorkoutSet(1L, 1L, 1L, 0, 47.5, null, 30, null, null, t0),
            WorkoutSet(2L, 1L, 2L, 0, 40.0, null, null, null, 25, t0),
            WorkoutSet(3L, 1L, 3L, 0, 0.0, null, 45, null, null, t0),
        ),
    )

    @Test
    fun `a backup with the new kinds survives an export and an import into another store`() = runTest {
        store.replaceAll(kindsBackup)
        val repo = DefaultBackupRepository(store, clock, training)
        val decoded = BackupCodec.decode(repo.export()) as BackupDecodeResult.Ok

        val otherStore = FakeWorkoutStore()
        val otherClock = TestClock(t0)
        DefaultBackupRepository(otherStore, otherClock, DefaultTrainingRepository(otherStore, otherClock, DefaultRoutineRepository(otherStore)))
            .import(decoded.data)

        assertEquals(kindsBackup, decoded.data)
        assertEquals(kindsBackup, otherStore.exportAll())
    }

    @Test
    fun `an import over a machine that changes kind is not refused`() = runTest {
        val press = machine("Leg press", MachineKind.WEIGHT_REPS)
        training.logSet(press, SetDraft(120.0, 12))
        val changed = kindsBackup.copy(
            machines = listOf(Machine(press, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_TIME, 2.5, null, null, false)),
            sets = listOf(WorkoutSet(1L, 1L, press, 0, 120.0, null, 30, null, null, t0)),
        )

        DefaultBackupRepository(store, clock, training).import(changed)

        assertEquals(changed, store.exportAll())
    }

    // repeat suggestions

    @Test
    fun `timed and distance machines get no progression suggestion`() = runTest {
        val farmers = machine("Farmer's walk", MachineKind.WEIGHT_TIME, MuscleGroup.ARMS, stepKg = 2.0)
        val sled = machine("Sled push", MachineKind.WEIGHT_DISTANCE, stepKg = 5.0)
        val plank = machine("Plank", MachineKind.TIME, MuscleGroup.CORE)
        training.logSet(farmers, SetDraft(45.0, 0, seconds = 30))
        training.logSet(sled, SetDraft(40.0, 0, distanceM = 25))
        training.logSet(plank, SetDraft(0.0, 0, seconds = 60))
        val items = listOf(
            RoutineItem(routineId = 1L, machineId = farmers, position = 0, targetSets = 2, repsMin = 8, repsMax = 12),
            RoutineItem(routineId = 1L, machineId = sled, position = 1, targetSets = 2, repsMin = 8, repsMax = 12),
            RoutineItem(routineId = 1L, machineId = plank, position = 2, targetSets = 2, repsMin = 8, repsMax = 12),
        )

        assertTrue(store.repeatSuggestions(items, excludeSessionId = 0L).isEmpty())
        for (id in listOf(farmers, sled, plank)) {
            assertNull(training.observeMachineLog(id).first()?.suggestion)
        }
    }
}
