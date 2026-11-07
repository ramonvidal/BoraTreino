package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.TodayPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What Today shows about routines, and how the owner's choice moves the session's routine. */
class TodayPlanTest {
    // Monday, 2 November 2026, evening.
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var clock: TestClock
    private lateinit var routines: RoutineRepository
    private lateinit var training: TrainingRepository
    private lateinit var machines: MachineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        clock = TestClock(monday)
        routines = DefaultRoutineRepository(store)
        training = DefaultTrainingRepository(store, clock, routines)
        machines = DefaultMachineRepository(store)
    }

    private suspend fun machine(name: String): Long =
        machines.save(Machine(0, name, MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 5.0, null, null, false))

    /** Saves a routine with the given machines (3 x 8-12 each); returns its id. */
    private suspend fun routine(
        shortName: String,
        vararg machineIds: Long,
        weekdays: Set<DayOfWeek> = emptySet(),
    ): Long = routines.saveRoutine(
        Routine(id = 0, planId = 0, name = "Routine $shortName", shortName = shortName, position = 0, weekdays = weekdays),
        machineIds.mapIndexed { i, id -> RoutineItem(0, id, i, 3, 8, 12) },
    )

    private suspend fun plan(): TodayPlan = training.observeTodayPlan().first()

    private suspend fun openSessionRoutineId(): Long? =
        training.observeOpenSession().first()?.session?.routineId

    /** Logs one set at the clock's time and finishes the session. The clock does not move. */
    private suspend fun finishedSession(machineId: Long) {
        training.logSet(machineId, SetDraft(100.0, 10))
        training.finishSession()
    }

    // Without routines

    @Test
    fun `without routines the plan has nothing to show`() = runTest {
        val plan = plan()

        assertEquals(emptyList<Routine>(), plan.routines)
        assertNull(plan.suggested)
        assertNull(plan.active)
        assertFalse(plan.restDay)
        assertEquals(RoutineChoice.Suggested, plan.choice)
        assertEquals(ScheduleMode.IN_TURN, plan.mode)
    }

    // Suggested routine, in turn

    @Test
    fun `before anything is done the first routine is suggested and active`() = runTest {
        routine("A", machine("Leg press"))
        routine("B", machine("Lat pulldown"))

        val plan = plan()

        assertEquals("A", plan.suggested?.shortName)
        assertEquals("A", plan.active?.routine?.shortName)
        assertEquals(listOf("A", "B"), plan.routines.map { it.shortName })
        assertFalse(plan.restDay)
    }

    @Test
    fun `after a finished A session, B is suggested`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        finishedSession(press)

        assertEquals("B", plan().suggested?.shortName)
        assertEquals("B", plan().active?.routine?.shortName)
    }

    @Test
    fun `after B the suggestion wraps back to A`() = runTest {
        val press = machine("Leg press")
        val lat = machine("Lat pulldown")
        routine("A", press)
        routine("B", lat)
        finishedSession(press)
        finishedSession(lat)

        assertEquals("A", plan().suggested?.shortName)
    }

    @Test
    fun `an open session does not move the turn on`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        training.logSet(press, SetDraft(100.0, 10))

        val plan = plan()

        assertEquals("A", plan.suggested?.shortName)
        assertEquals("A", plan.active?.routine?.shortName)
    }

    @Test
    fun `the open session's routine stays active when the routines are reordered`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        training.logSet(press, SetDraft(100.0, 10))
        routines.moveRoutine(b, up = true)

        val plan = plan()

        assertEquals("B", plan.suggested?.shortName)
        assertEquals("A", plan.active?.routine?.shortName)
    }

    @Test
    fun `the active routine has its machines in position order`() = runTest {
        val press = machine("Leg press")
        val row = machine("Seated row")
        val a = routine("A", press, row)

        val active = requireNotNull(plan().active)

        assertEquals(a, active.routine.id)
        assertEquals(listOf(press, row), active.items.map { it.machineId })
        assertEquals(listOf(0, 1), active.items.map { it.position })
    }

    // Choosing a routine

    @Test
    fun `choosing a routine with no session only changes the choice`() = runTest {
        routine("A", machine("Leg press"))
        val b = routine("B", machine("Lat pulldown"))

        training.chooseRoutine(RoutineChoice.Chosen(b))

        val plan = plan()
        assertEquals(RoutineChoice.Chosen(b), plan.choice)
        assertEquals("B", plan.active?.routine?.shortName)
        assertNull(training.observeOpenSession().first())
    }

    @Test
    fun `choosing Free shows no active routine but keeps the routines listed`() = runTest {
        routine("A", machine("Leg press"))
        routine("B", machine("Lat pulldown"))

        training.chooseRoutine(RoutineChoice.Free)

        val plan = plan()
        assertEquals(RoutineChoice.Free, plan.choice)
        assertNull(plan.active)
        assertEquals(listOf("A", "B"), plan.routines.map { it.shortName })
    }

    @Test
    fun `choosing another routine on an open session moves the session to it`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        training.logSet(press, SetDraft(100.0, 10))

        training.chooseRoutine(RoutineChoice.Chosen(b))

        assertEquals(b, openSessionRoutineId())
        assertEquals("B", plan().active?.routine?.shortName)
    }

    @Test
    fun `choosing Free on an open session clears its routine`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        training.logSet(press, SetDraft(100.0, 10))
        assertEquals(a, openSessionRoutineId())

        training.chooseRoutine(RoutineChoice.Free)

        assertNull(openSessionRoutineId())
        val plan = plan()
        assertNull(plan.active)
        assertEquals(RoutineChoice.Free, plan.choice)
    }

    @Test
    fun `choosing Suggested on an open session gives it the suggested routine`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        training.chooseRoutine(RoutineChoice.Chosen(b))
        training.logSet(press, SetDraft(100.0, 10))
        assertEquals(b, openSessionRoutineId())

        training.chooseRoutine(RoutineChoice.Suggested)

        assertEquals(a, openSessionRoutineId())
        assertEquals("A", plan().active?.routine?.shortName)
    }

    @Test
    fun `a new session takes the routine of the choice`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        training.chooseRoutine(RoutineChoice.Chosen(b))

        training.logSet(press, SetDraft(100.0, 10))

        assertEquals(b, openSessionRoutineId())
    }

    @Test
    fun `a new session after choosing Free has no routine`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        training.chooseRoutine(RoutineChoice.Free)

        training.logSet(press, SetDraft(100.0, 10))

        assertNull(openSessionRoutineId())
    }

    @Test
    fun `a new session with the default choice takes the suggested routine`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        routine("B", machine("Lat pulldown"))

        training.logSet(press, SetDraft(100.0, 10))

        assertEquals(a, openSessionRoutineId())
    }

    @Test
    fun `a chosen routine that no longer exists is stored as no routine`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        training.logSet(press, SetDraft(100.0, 10))

        training.chooseRoutine(RoutineChoice.Chosen(999L))

        assertNull(openSessionRoutineId())
        assertNull(plan().active)
    }

    @Test
    fun `a new session after choosing a routine that no longer exists has no routine`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        training.chooseRoutine(RoutineChoice.Chosen(999L))

        training.logSet(press, SetDraft(100.0, 10))

        assertNull(openSessionRoutineId())
    }

    @Test
    fun `the choice goes back to Suggested when the session is finished`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        training.logSet(press, SetDraft(100.0, 10))
        training.chooseRoutine(RoutineChoice.Free)

        training.finishSession()

        assertEquals(RoutineChoice.Suggested, plan().choice)
    }

    @Test
    fun `the choice goes back to Suggested when a stale session closes`() = runTest {
        val press = machine("Leg press")
        val a = routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        training.chooseRoutine(RoutineChoice.Chosen(b))
        training.logSet(press, SetDraft(100.0, 10))
        clock.advance(Duration.ofHours(3))

        training.closeStaleSession()

        assertEquals(RoutineChoice.Suggested, plan().choice)
        // The closed session had B, so the turn is now on A again.
        training.logSet(press, SetDraft(100.0, 10))
        assertEquals(a, openSessionRoutineId())
    }

    // On fixed weekdays

    @Test
    fun `on weekdays the routine for today is suggested`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.WEDNESDAY))
        clock.now = Instant.parse("2026-11-04T18:00:00Z") // Wednesday

        val plan = plan()

        assertEquals(ScheduleMode.WEEKDAYS, plan.mode)
        assertEquals("B", plan.suggested?.shortName)
        assertEquals("B", plan.active?.routine?.shortName)
        assertFalse(plan.restDay)
    }

    @Test
    fun `on weekdays a day with no routine is a rest day with routines still listed`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.WEDNESDAY))
        clock.now = Instant.parse("2026-11-03T18:00:00Z") // Tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertNull(plan.suggested)
        assertNull(plan.active)
        assertEquals(listOf("A", "B"), plan.routines.map { it.shortName })
    }

    @Test
    fun `on a rest day a routine chosen by hand is still the active one`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        val b = routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.WEDNESDAY))
        clock.now = Instant.parse("2026-11-03T18:00:00Z") // Tuesday

        training.chooseRoutine(RoutineChoice.Chosen(b))

        val plan = plan()
        assertEquals("B", plan.active?.routine?.shortName)
        assertTrue(plan.restDay)
    }

    @Test
    fun `on weekdays the last routine done does not change the suggestion`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        val press = machine("Leg press")
        val a = routine("A", press, weekdays = setOf(DayOfWeek.MONDAY))
        routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.MONDAY))
        finishedSession(press)

        assertEquals(a, plan().suggested?.id)
    }

    @Test
    fun `in turn the weekday does not matter, so a Tuesday still gets the next routine`() = runTest {
        val press = machine("Leg press")
        routine("A", press, weekdays = setOf(DayOfWeek.MONDAY))
        routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.TUESDAY))
        finishedSession(press)
        clock.now = Instant.parse("2026-11-03T18:00:00Z") // Tuesday

        val plan = plan()

        assertEquals("B", plan.suggested?.shortName)
        assertFalse(plan.restDay)
    }

    // An open session's own routine decides; the choice only counts before one opens.

    @Test
    fun `a reopened Free session has no active routine, though the choice is back to Suggested`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        training.chooseRoutine(RoutineChoice.Free)
        val logged = training.logSet(press, SetDraft(100.0, 10))
        training.finishSession()

        training.reopenSession(logged.set.sessionId)

        assertEquals(RoutineChoice.Suggested, plan().choice)
        assertNull(plan().active)
        assertNull(requireNotNull(training.observeMachineLog(press).first()).target)
    }

    @Test
    fun `an open session keeps its own routine when the choice changes in memory`() = runTest {
        val press = machine("Leg press")
        routine("A", machine("Lat pulldown"))
        val b = routine("B", press)
        training.chooseRoutine(RoutineChoice.Chosen(b))
        val logged = training.logSet(press, SetDraft(100.0, 10))
        training.finishSession()
        training.reopenSession(logged.set.sessionId)

        // The finish reset the choice to Suggested (which would be A), but the open session is B.
        assertEquals(RoutineChoice.Suggested, plan().choice)
        assertEquals(b, plan().active?.routine?.id)
        assertEquals(b, requireNotNull(training.observeMachineLog(press).first()).target?.routineId)
    }

    // Training days (in turn): a rest day shows the next training

    private val tuesday = Instant.parse("2026-11-03T18:00:00Z")
    private val wednesday = Instant.parse("2026-11-04T18:00:00Z")
    private val saturday = Instant.parse("2026-11-07T18:00:00Z")
    private val monWedFri = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    @Test
    fun `in turn on a day that is not a training day is a rest day with the next training`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        routines.setTrainingDays(monWedFri)
        finishedSession(press) // Monday: A
        clock.now = tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertNull(plan.suggested)
        assertNull(plan.active)
        assertEquals(LocalDate.of(2026, 11, 4), plan.upcoming?.date)
        assertEquals("B", plan.upcoming?.routine?.shortName)
        assertEquals(listOf("A", "B"), plan.routines.map { it.shortName })
    }

    @Test
    fun `in turn on a training day is not a rest day and has no upcoming training`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        routines.setTrainingDays(monWedFri)
        finishedSession(press) // Monday: A
        clock.now = wednesday

        val plan = plan()

        assertFalse(plan.restDay)
        assertEquals("B", plan.suggested?.shortName)
        assertNull(plan.upcoming)
    }

    @Test
    fun `in turn with no training days every day is a training day, so there is no rest day`() = runTest {
        routine("A", machine("Leg press"))
        clock.now = tuesday

        val plan = plan()

        assertFalse(plan.restDay)
        assertEquals("A", plan.suggested?.shortName)
        assertNull(plan.upcoming)
    }

    @Test
    fun `in turn the upcoming training wraps over the end of the week`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        routines.setTrainingDays(monWedFri)
        clock.now = Instant.parse("2026-11-06T18:00:00Z") // Friday
        finishedSession(press) // Friday: A
        clock.now = saturday

        val plan = plan()

        assertTrue(plan.restDay)
        assertEquals(LocalDate.of(2026, 11, 9), plan.upcoming?.date)
        assertEquals("B", plan.upcoming?.routine?.shortName)
    }

    @Test
    fun `in turn the upcoming training is found up to six days ahead`() = runTest {
        routine("A", machine("Leg press"))
        routines.setTrainingDays(setOf(DayOfWeek.TUESDAY))
        clock.now = Instant.parse("2026-11-04T18:00:00Z") // Wednesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertEquals(LocalDate.of(2026, 11, 10), plan.upcoming?.date)
        assertEquals("A", plan.upcoming?.routine?.shortName)
    }

    // Fixed weekdays: a rest day shows the next day that has a routine

    @Test
    fun `on weekdays a rest day has the next day with a routine as upcoming`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.WEDNESDAY))
        clock.now = tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertNull(plan.suggested)
        assertEquals(LocalDate.of(2026, 11, 4), plan.upcoming?.date)
        assertEquals("B", plan.upcoming?.routine?.shortName)
    }

    @Test
    fun `on weekdays a rest day with no routine on any weekday has no upcoming training`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"))
        routine("B", machine("Lat pulldown"))
        clock.now = tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertNull(plan.upcoming)
    }

    @Test
    fun `on weekdays the training days do not affect the rest day`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routines.setTrainingDays(setOf(DayOfWeek.TUESDAY))
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        routine("B", machine("Lat pulldown"), weekdays = setOf(DayOfWeek.WEDNESDAY))
        clock.now = tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertEquals("B", plan.upcoming?.routine?.shortName)
    }

    // Planned workouts: a routine planned for a date overrides the schedule for that date only

    @Test
    fun `a routine planned for today is the suggestion and is not a rest day`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        val b = routine("B", machine("Lat pulldown"))
        routines.planWorkout(LocalDate.of(2026, 11, 3), b) // Tuesday, a day with no routine
        clock.now = tuesday

        val plan = plan()

        assertEquals("B", plan.suggested?.shortName)
        assertEquals("B", plan.active?.routine?.shortName)
        assertFalse(plan.restDay)
        assertTrue(plan.plannedToday)
        assertNull(plan.upcoming)
    }

    @Test
    fun `a planned routine on a day off in turn is the suggestion for that day`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        val c = routine("C", machine("Squat rack"))
        routines.setTrainingDays(monWedFri)
        finishedSession(press) // Monday: A
        routines.planWorkout(LocalDate.of(2026, 11, 3), c) // Tuesday, not a training day
        clock.now = tuesday

        val plan = plan()

        assertEquals("C", plan.suggested?.shortName)
        assertFalse(plan.restDay)
        assertTrue(plan.plannedToday)
    }

    @Test
    fun `a plan for another day leaves today a plain rest day`() = runTest {
        routines.setScheduleMode(ScheduleMode.WEEKDAYS)
        routine("A", machine("Leg press"), weekdays = setOf(DayOfWeek.MONDAY))
        val b = routine("B", machine("Lat pulldown"))
        routines.planWorkout(LocalDate.of(2026, 11, 4), b) // Wednesday, not today
        clock.now = tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertNull(plan.suggested)
        assertFalse(plan.plannedToday)
    }

    @Test
    fun `a plan for a past day is ignored`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        routines.planWorkout(LocalDate.of(2026, 11, 1), b) // Sunday, before today

        val plan = plan()

        assertEquals("A", plan.suggested?.shortName)
        assertFalse(plan.plannedToday)
    }

    @Test
    fun `the upcoming training uses a planned day`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        routine("B", machine("Lat pulldown"))
        val c = routine("C", machine("Squat rack"))
        routines.setTrainingDays(monWedFri)
        finishedSession(press) // Monday: A, so Wednesday would be B
        routines.planWorkout(LocalDate.of(2026, 11, 4), c) // Wednesday: C instead
        clock.now = tuesday

        val plan = plan()

        assertTrue(plan.restDay)
        assertEquals(LocalDate.of(2026, 11, 4), plan.upcoming?.date)
        assertEquals("C", plan.upcoming?.routine?.shortName)
    }

    @Test
    fun `a planned routine that is deleted leaves no plan for its day`() = runTest {
        val press = machine("Leg press")
        routine("A", press)
        val b = routine("B", machine("Lat pulldown"))
        routines.planWorkout(LocalDate.of(2026, 11, 2), b) // today
        routines.deleteRoutine(b)

        val plan = plan()

        assertEquals("A", plan.suggested?.shortName)
        assertFalse(plan.plannedToday)
        assertEquals(listOf("A"), plan.routines.map { it.shortName })
    }
}
