package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.Session
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class HistoryRepositoryTest {
    /** 2 November 2026, 15:00 in Sao Paulo (UTC-3, no daylight saving). */
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val now: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var training: DefaultTrainingRepository
    private lateinit var history: HistoryRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(now, zone)
        training = DefaultTrainingRepository(store, clock, DefaultRoutineRepository(store))
        history = DefaultHistoryRepository(store, clock, training)
        machines = DefaultMachineRepository(store)
    }

    /** A moment at [time] on [date] in Sao Paulo. */
    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private suspend fun machine(name: String, group: MuscleGroup = MuscleGroup.BACK): Long =
        machines.save(Machine(0, name, group, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun routine(shortName: String): Routine {
        val plan = store.ensurePlan()
        val id = store.insertRoutine(
            Routine(id = 0, planId = plan.id, name = "Routine $shortName", shortName = shortName, position = store.allRoutines().size + 1, weekdays = emptySet()),
        )
        return store.allRoutines().first { it.id == id }
    }

    private suspend fun session(startedAt: Instant, endedAt: Instant? = startedAt, routineId: Long? = null): Long =
        store.insertSession(Session(id = 0, startedAt = startedAt, endedAt = endedAt, routineId = routineId, note = null))

    private suspend fun storeSet(sessionId: Long, machineId: Long, position: Int, loggedAt: Instant, weightKg: Double = 40.0, reps: Int = 10): WorkoutSet {
        val draft = WorkoutSet(
            id = 0, sessionId = sessionId, machineId = machineId, position = position,
            weightKg = weightKg, reps = reps, seconds = null, calories = null, distanceM = null, loggedAt = loggedAt,
        )
        return draft.copy(id = store.insertSet(draft))
    }

    private fun month(year: Int, m: Int) = YearMonth.of(year, m)

    // observeMonth

    @Test
    fun `a session at 23 30 local belongs to its day though UTC is already the next day`() = runTest {
        val late = at("2026-11-02", "23:30")
        assertEquals(LocalDate.of(2026, 11, 3), late.atZone(java.time.ZoneOffset.UTC).toLocalDate())
        session(late)

        val byDate = history.observeMonth(month(2026, 11)).first()

        assertEquals(setOf(LocalDate.of(2026, 11, 2)), byDate.keys)
    }

    @Test
    fun `the month starts and ends at local midnight`() = runTest {
        session(at("2026-10-31", "23:30")) // 1 Nov UTC, but still October here
        session(at("2026-11-01", "00:30"))
        session(at("2026-11-30", "23:30")) // 1 Dec UTC, still November here
        session(at("2026-12-01", "00:30"))

        val byDate = history.observeMonth(month(2026, 11)).first()

        assertEquals(setOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), byDate.keys)
    }

    @Test
    fun `a month holds only its own sessions`() = runTest {
        session(at("2026-10-20", "18:00"))
        session(at("2026-12-03", "18:00"))

        assertTrue(history.observeMonth(month(2026, 11)).first().isEmpty())
    }

    @Test
    fun `each session counts its machines and sets and names its routine`() = runTest {
        val b = routine("B")
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val id = session(at("2026-11-02", "18:05"), at("2026-11-02", "19:10"), routineId = b.id)
        storeSet(id, leg, 0, at("2026-11-02", "18:05"))
        storeSet(id, leg, 1, at("2026-11-02", "18:08"))
        storeSet(id, row, 0, at("2026-11-02", "18:20"))

        val info = history.observeMonth(month(2026, 11)).first().getValue(LocalDate.of(2026, 11, 2)).single()

        assertEquals(id, info.session.id)
        assertEquals(b, info.routine)
        assertEquals(2, info.machineCount)
        assertEquals(3, info.setCount)
    }

    @Test
    fun `a session without a routine has none`() = runTest {
        session(at("2026-11-02", "18:05"))

        val info = history.observeMonth(month(2026, 11)).first().getValue(LocalDate.of(2026, 11, 2)).single()

        assertNull(info.routine)
        assertEquals(0, info.setCount)
    }

    @Test
    fun `two sessions on one day are listed by start time`() = runTest {
        val evening = session(at("2026-11-02", "18:00"))
        val morning = session(at("2026-11-02", "07:00"))

        val day = history.observeMonth(month(2026, 11)).first().getValue(LocalDate.of(2026, 11, 2))

        assertEquals(listOf(morning, evening), day.map { it.session.id })
    }

    // observeDay

    @Test
    fun `observeDay returns that day's sessions by start time`() = runTest {
        val evening = session(at("2026-11-02", "18:00"))
        val morning = session(at("2026-11-02", "07:00"))
        session(at("2026-11-03", "07:00"))

        val day = history.observeDay(LocalDate.of(2026, 11, 2)).first()

        assertEquals(listOf(morning, evening), day.map { it.session.id })
    }

    @Test
    fun `a session at 23 30 local is on its own day in observeDay`() = runTest {
        val late = session(at("2026-11-02", "23:30"))

        assertEquals(listOf(late), history.observeDay(LocalDate.of(2026, 11, 2)).first().map { it.session.id })
        assertTrue(history.observeDay(LocalDate.of(2026, 11, 3)).first().isEmpty())
    }

    @Test
    fun `machines of a session come in the order of their first set`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val id = session(at("2026-11-02", "18:00"), at("2026-11-02", "19:00"))
        storeSet(id, leg, 0, at("2026-11-02", "18:10"))
        storeSet(id, row, 0, at("2026-11-02", "18:05"))
        storeSet(id, leg, 1, at("2026-11-02", "18:20"))

        val detail = history.observeDay(LocalDate.of(2026, 11, 2)).first().single()

        assertEquals(listOf(row, leg), detail.machines.map { it.machine.id })
        assertEquals(listOf(0, 1), detail.machines.last().sets.map { it.position })
    }

    @Test
    fun `a machine's sets come in position order`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val id = session(at("2026-11-02", "18:00"), at("2026-11-02", "19:00"))
        storeSet(id, leg, 1, at("2026-11-02", "18:10"), weightKg = 60.0)
        storeSet(id, leg, 0, at("2026-11-02", "18:05"), weightKg = 50.0)

        val sets = history.observeDay(LocalDate.of(2026, 11, 2)).first().single().machines.single().sets

        assertEquals(listOf(50.0, 60.0), sets.map { it.weightKg })
    }

    @Test
    fun `observeDay carries the routine of the session`() = runTest {
        val b = routine("B")
        session(at("2026-11-02", "18:00"), routineId = b.id)

        assertEquals(b, history.observeDay(LocalDate.of(2026, 11, 2)).first().single().routine)
    }

    // observeSession

    @Test
    fun `observeSession gives the detail, and null for a missing session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val id = session(at("2026-11-02", "18:00"))
        storeSet(id, leg, 0, at("2026-11-02", "18:01"))

        assertEquals(1, history.observeSession(id).first()?.machines?.single()?.sets?.size)
        assertNull(history.observeSession(999).first())
    }

    // observeTrainingDates

    @Test
    fun `training dates hold every local day with a session, once`() = runTest {
        session(at("2026-10-31", "23:30"))
        session(at("2026-11-02", "07:00"))
        session(at("2026-11-02", "18:00"))
        session(at("2026-11-02", "19:00"), endedAt = null)

        assertEquals(setOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2)), history.observeTrainingDates().first())
    }

    // addSession

    @Test
    fun `a session added on a past day is closed at 18 00 that day with the routine`() = runTest {
        val b = routine("B")

        val id = history.addSession(LocalDate.of(2026, 10, 20), b.id)

        val stored = requireNotNull(store.session(id))
        assertEquals(at("2026-10-20", "18:00"), stored.startedAt)
        assertEquals(stored.startedAt, stored.endedAt)
        assertEquals(b.id, stored.routineId)
    }

    @Test
    fun `a session added on a future day is also closed at 18 00`() = runTest {
        val id = history.addSession(LocalDate.of(2026, 11, 10), null)

        val stored = requireNotNull(store.session(id))
        assertEquals(at("2026-11-10", "18:00"), stored.startedAt)
        assertEquals(stored.startedAt, stored.endedAt)
    }

    @Test
    fun `today with nothing open adds a closed session an hour before now`() = runTest {
        val id = history.addSession(LocalDate.of(2026, 11, 2), null)

        val stored = requireNotNull(store.session(id))
        assertEquals(now.minusSeconds(3600), stored.startedAt)
        assertEquals(stored.startedAt, stored.endedAt)
    }

    @Test
    fun `today with a session open returns that session and adds nothing`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val open = training.logSet(leg, SetDraft(120.0, 12)).set.sessionId

        val id = history.addSession(LocalDate.of(2026, 11, 2), null)

        assertEquals(open, id)
        assertEquals(1, store.allSessions().size)
    }

    @Test
    fun `a past day adds its own session even while one is open today`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val open = training.logSet(leg, SetDraft(120.0, 12)).set.sessionId

        val id = history.addSession(LocalDate.of(2026, 10, 20), null)

        assertNotEquals(open, id)
        assertEquals(2, store.allSessions().size)
    }

    @Test
    fun `an added session does not open a training`() = runTest {
        history.addSession(LocalDate.of(2026, 10, 20), null)

        assertNull(training.observeOpenSession().first())
    }

    // setSessionRoutine

    @Test
    fun `setSessionRoutine sets the routine and clears it with null`() = runTest {
        val a = routine("A")
        val id = session(at("2026-11-02", "18:00"))

        history.setSessionRoutine(id, a.id)
        assertEquals(a, history.observeSession(id).first()?.routine)

        history.setSessionRoutine(id, null)
        assertNull(history.observeSession(id).first()?.routine)
    }

    // addSet

    @Test
    fun `a set gets the next position of its machine in the session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val id = session(at("2026-11-02", "18:00"), endedAt = null)
        storeSet(id, leg, 0, at("2026-11-02", "18:05"))

        val nextLeg = history.addSet(id, leg, SetDraft(120.0, 12))
        val firstRow = history.addSet(id, row, SetDraft(40.0, 10))

        assertEquals(1, nextLeg.position)
        assertEquals(0, firstRow.position)
    }

    @Test
    fun `a set is logged one minute after the session's last set`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val id = session(at("2026-11-02", "18:00"), endedAt = null)
        storeSet(id, leg, 0, at("2026-11-02", "18:20"))

        val added = history.addSet(id, leg, SetDraft(120.0, 12))

        assertEquals(at("2026-11-02", "18:21"), added.loggedAt)
    }

    @Test
    fun `the first set of a session without sets is logged at its start`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val start = at("2026-11-02", "18:00")
        val id = session(start, endedAt = null)

        val added = history.addSet(id, leg, SetDraft(120.0, 12))

        assertEquals(start, added.loggedAt)
        assertEquals(120.0, added.weightKg)
        assertEquals(12, added.reps)
        assertEquals(id, added.sessionId)
        assertEquals(leg, added.machineId)
    }

    @Test
    fun `a closed session without sets keeps its end when the first set is logged at its start`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val start = at("2026-10-20", "18:00")
        val id = session(start, endedAt = start)

        val added = history.addSet(id, leg, SetDraft(120.0, 12))

        assertEquals(start, added.loggedAt)
        assertEquals(start, store.session(id)?.endedAt)
    }

    @Test
    fun `a closed session with a later set ends one minute after its last set`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val start = at("2026-10-20", "18:00")
        val id = session(start, endedAt = start)
        storeSet(id, leg, 0, start)

        history.addSet(id, leg, SetDraft(120.0, 12))

        val stored = requireNotNull(store.session(id))
        assertEquals(start.plusSeconds(60), stored.endedAt)
        assertFalse(stored.isOpen)
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `a closed session keeps its end when the new set is before it`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val start = at("2026-10-20", "18:00")
        val end = at("2026-10-20", "19:00")
        val id = session(start, endedAt = end)
        storeSet(id, leg, 0, at("2026-10-20", "18:10"))

        history.addSet(id, leg, SetDraft(120.0, 12))

        assertEquals(end, store.session(id)?.endedAt)
    }

    @Test
    fun `an open session stays open after a set is added`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val id = session(at("2026-11-02", "18:00"), endedAt = null)

        history.addSet(id, leg, SetDraft(120.0, 12))

        assertNull(store.session(id)?.endedAt)
    }

    @Test
    fun `a set for an unknown session is refused`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)

        var refused = false
        try {
            history.addSet(999, leg, SetDraft(120.0, 12))
        } catch (_: IllegalArgumentException) {
            refused = true
        }

        assertTrue(refused)
        assertTrue(store.allSets().isEmpty())
    }

    // lastTimeBefore

    @Test
    fun `lastTimeBefore gives the machine's sets from the latest earlier session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val earlier = session(at("2026-10-20", "18:00"))
        storeSet(earlier, leg, 0, at("2026-10-20", "18:05"), weightKg = 100.0)
        val lastWeek = session(at("2026-10-27", "18:00"))
        storeSet(lastWeek, leg, 0, at("2026-10-27", "18:05"), weightKg = 110.0, reps = 8)
        storeSet(lastWeek, leg, 1, at("2026-10-27", "18:10"), weightKg = 110.0, reps = 8)
        val current = session(at("2026-11-02", "18:00"), endedAt = null)

        val last = history.lastTimeBefore(current, leg)

        assertEquals(listOf(110.0, 110.0), last.map { it.weightKg })
        assertEquals(listOf(8, 8), last.map { it.reps })
    }

    @Test
    fun `lastTimeBefore ignores later sessions and sessions without the machine`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val earlier = session(at("2026-10-20", "18:00"))
        storeSet(earlier, leg, 0, at("2026-10-20", "18:05"), weightKg = 100.0)
        val otherMachineOnly = session(at("2026-10-25", "18:00"))
        storeSet(otherMachineOnly, row, 0, at("2026-10-25", "18:05"))
        val current = session(at("2026-10-27", "18:00"))
        storeSet(current, leg, 0, at("2026-10-27", "18:05"), weightKg = 105.0)
        val later = session(at("2026-11-02", "18:00"))
        storeSet(later, leg, 0, at("2026-11-02", "18:05"), weightKg = 120.0)

        assertEquals(listOf(100.0), history.lastTimeBefore(current, leg).map { it.weightKg })
    }

    @Test
    fun `a session starting at the same time with a lower id counts as earlier`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val start = at("2026-10-27", "18:00")
        val first = session(start)
        storeSet(first, leg, 0, start, weightKg = 90.0)
        val second = session(start)
        storeSet(second, leg, 0, start, weightKg = 95.0)

        assertEquals(listOf(90.0), history.lastTimeBefore(second, leg).map { it.weightKg })
        assertTrue(history.lastTimeBefore(first, leg).isEmpty())
    }

    @Test
    fun `lastTimeBefore is empty when there is no earlier session`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val only = session(at("2026-11-02", "18:00"))
        storeSet(only, leg, 0, at("2026-11-02", "18:05"))

        assertTrue(history.lastTimeBefore(only, leg).isEmpty())
    }

    // deleteSession

    @Test
    fun `deleteSession returns the session with its routine and sets, and removes both`() = runTest {
        val b = routine("B")
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val id = session(at("2026-11-02", "18:00"), at("2026-11-02", "19:00"), routineId = b.id)
        storeSet(id, leg, 0, at("2026-11-02", "18:05"))
        storeSet(id, leg, 1, at("2026-11-02", "18:10"))
        storeSet(id, row, 0, at("2026-11-02", "18:20"))

        val detail = requireNotNull(history.deleteSession(id))

        assertEquals(id, detail.session.id)
        assertEquals(b, detail.routine)
        assertEquals(2, detail.machines.size)
        assertEquals(3, detail.machines.sumOf { it.sets.size })
        assertNull(store.session(id))
        assertTrue(store.sessionSets(id).isEmpty())
        assertTrue(store.allSets().isEmpty())
    }

    @Test
    fun `deleting a missing session gives nothing`() = runTest {
        assertNull(history.deleteSession(999))
    }

    @Test
    fun `deleting the open session resets the routine choice to Suggested`() = runTest {
        routine("B")
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val open = training.logSet(leg, SetDraft(120.0, 12)).set.sessionId
        training.chooseRoutine(RoutineChoice.Free)
        assertEquals(RoutineChoice.Free, training.observeTodayPlan().first().choice)

        history.deleteSession(open)

        assertEquals(RoutineChoice.Suggested, training.observeTodayPlan().first().choice)
    }

    @Test
    fun `deleting a closed session keeps the routine choice`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        training.logSet(leg, SetDraft(120.0, 12))
        training.chooseRoutine(RoutineChoice.Free)
        val closed = session(at("2026-10-20", "18:00"))

        history.deleteSession(closed)

        assertEquals(RoutineChoice.Free, training.observeTodayPlan().first().choice)
    }

    // restoreSession

    @Test
    fun `restoring a deleted session brings back its id and its sets with their ids`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val row = machine("Seated row")
        val id = session(at("2026-11-02", "18:00"), at("2026-11-02", "19:00"))
        val firstSet = storeSet(id, leg, 0, at("2026-11-02", "18:05"), weightKg = 120.0, reps = 12)
        val secondSet = storeSet(id, leg, 1, at("2026-11-02", "18:08"), weightKg = 125.0, reps = 10)
        val rowSet = storeSet(id, row, 0, at("2026-11-02", "18:20"))
        val detail = requireNotNull(history.deleteSession(id))

        history.restoreSession(detail)

        val restored = requireNotNull(history.observeSession(id).first())
        assertEquals(id, restored.session.id)
        assertEquals(at("2026-11-02", "18:00"), restored.session.startedAt)
        assertEquals(at("2026-11-02", "19:00"), restored.session.endedAt)
        assertEquals(listOf(firstSet, secondSet), restored.machines.first { it.machine.id == leg }.sets)
        assertEquals(listOf(rowSet), restored.machines.first { it.machine.id == row }.sets)
        assertEquals(listOf(id), history.observeDay(LocalDate.of(2026, 11, 2)).first().map { it.session.id })
    }

    @Test
    fun `restoring keeps the routine while it still exists`() = runTest {
        val b = routine("B")
        val id = session(at("2026-11-02", "18:00"), routineId = b.id)
        val detail = requireNotNull(history.deleteSession(id))

        history.restoreSession(detail)

        assertEquals(b.id, store.session(id)?.routineId)
    }

    @Test
    fun `restoring drops a routine that was deleted since`() = runTest {
        val b = routine("B")
        val id = session(at("2026-11-02", "18:00"), routineId = b.id)
        val detail = requireNotNull(history.deleteSession(id))
        assertEquals(b, detail.routine)
        store.deleteRoutine(b.id)

        history.restoreSession(detail)

        assertNull(store.session(id)?.routineId)
        assertEquals(id, store.session(id)?.id)
    }

    @Test
    fun `a set added after a restore continues the machine's positions`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val id = session(at("2026-11-02", "18:00"), at("2026-11-02", "19:00"))
        storeSet(id, leg, 0, at("2026-11-02", "18:05"))
        val detail = requireNotNull(history.deleteSession(id))

        history.restoreSession(detail)
        val added = history.addSet(id, leg, SetDraft(120.0, 12))

        assertEquals(1, added.position)
        assertEquals(2, store.allSets().size)
    }

    @Test
    fun `deleting the open session clears the skipped machines`() = runTest {
        val leg = machine("Leg press", MuscleGroup.LEGS)
        val curl = machine("Leg curl", MuscleGroup.LEGS)
        training.logSet(leg, SetDraft(120.0, 12))
        training.skipMachineToday(curl)
        val openId = requireNotNull(store.openSession()).id

        history.deleteSession(openId)

        assertEquals(emptySet<Long>(), training.observeTodayPlan().first().skipped)
    }
}
