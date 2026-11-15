package com.boratreino.widget

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.Sex
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.UpcomingTraining
import com.boratreino.core.model.WorkoutSet
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The widget's state from the Today plan, the open session and the owner's sex (no Android). */
class NextWorkoutWidgetStateTest {
    private val legPress = machine(1, "Leg press")
    private val squat = machine(2, "Squat")
    private val curl = machine(3, "Bicep curl")

    private val routineA = routine(10, "Routine A")
    private val routineB = routine(11, "Routine B")
    private val routineC = routine(12, "Routine C")

    private val itemsA = listOf(item(10, legPress.id), item(10, squat.id), item(10, curl.id))
    private val activeA = RoutineWithItems(routineA, itemsA)

    private val startedAt = Instant.parse("2026-11-02T10:00:00Z")

    private fun machine(id: Long, name: String) = Machine(
        id = id,
        name = name,
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private fun routine(id: Long, name: String) = Routine(
        id = id,
        planId = 1,
        name = name,
        shortName = "R$id",
        position = id.toInt(),
        weekdays = emptySet(),
    )

    private fun item(routineId: Long, machineId: Long) = RoutineItem(
        routineId = routineId,
        machineId = machineId,
        position = machineId.toInt(),
        targetSets = 3,
        repsMin = 8,
        repsMax = 12,
    )

    private fun plan(
        routines: List<Routine> = listOf(routineA),
        suggested: Routine? = null,
        choice: RoutineChoice = RoutineChoice.Suggested,
        active: RoutineWithItems? = null,
        restDay: Boolean = false,
        skipped: Set<Long> = emptySet(),
        upcoming: UpcomingTraining? = null,
        plannedToday: Boolean = false,
    ) = TodayPlan(
        mode = ScheduleMode.WEEKDAYS,
        routines = routines,
        suggested = suggested,
        choice = choice,
        active = active,
        restDay = restDay,
        skipped = skipped,
        upcoming = upcoming,
        plannedToday = plannedToday,
    )

    private fun loggedSet(machine: Machine) = WorkoutSet(
        id = machine.id * 100,
        sessionId = 1,
        machineId = machine.id,
        position = 1,
        weightKg = 40.0,
        reps = 10,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = startedAt,
    )

    /** An open session whose machines are given, most recently logged first. */
    private fun open(routineId: Long?, vararg logged: Machine): OpenSession = OpenSession(
        session = Session(id = 1, startedAt = startedAt, endedAt = null, routineId = routineId, note = null),
        machines = logged.map { SessionMachine(it, listOf(loggedSet(it))) },
    )

    /** An open session with nothing logged yet. */
    private fun openEmpty(routineId: Long?): OpenSession = OpenSession(
        session = Session(id = 1, startedAt = startedAt, endedAt = null, routineId = routineId, note = null),
        machines = emptyList(),
    )

    // An open session

    @Test
    fun `an open session with a logged set continues the exercise logged last`() {
        val state = widgetStateOf(plan(active = activeA), open(10, legPress, squat), sex = null)

        assertEquals(
            NextWorkoutWidgetState(
                routineName = "Routine A",
                detail = WidgetDetail.CONTINUE,
                continueExercise = "Leg press",
                sex = null,
            ),
            state,
        )
    }

    @Test
    fun `a free workout with a logged set continues without a routine name`() {
        val state = widgetStateOf(
            plan(choice = RoutineChoice.Free, active = null, suggested = routineB),
            open(null, squat),
            sex = null,
        )

        assertEquals(WidgetDetail.CONTINUE, state.detail)
        assertEquals("Squat", state.continueExercise)
        assertNull(state.routineName)
    }

    @Test
    fun `an open session without a logged set is not a continue`() {
        val state = widgetStateOf(plan(active = activeA), openEmpty(10), sex = null)

        assertEquals(
            NextWorkoutWidgetState(routineName = "Routine A", detail = WidgetDetail.EXERCISES, exerciseCount = 3),
            state,
        )
    }

    @Test
    fun `an open session without a logged set on a rest day is not a rest day`() {
        val state = widgetStateOf(
            plan(restDay = true, upcoming = UpcomingTraining(LocalDate.of(2026, 11, 3), routineB)),
            openEmpty(null),
            sex = null,
        )

        assertEquals(WidgetDetail.NONE, state.detail)
        assertNull(state.routineName)
    }

    @Test
    fun `a logged set beats a rest day and having no routines`() {
        val state = widgetStateOf(
            plan(routines = emptyList(), restDay = true),
            open(null, curl),
            sex = null,
        )

        assertEquals(WidgetDetail.CONTINUE, state.detail)
        assertEquals("Bicep curl", state.continueExercise)
    }

    // No routines

    @Test
    fun `without routines the widget says no routine yet and has no name`() {
        val state = widgetStateOf(plan(routines = emptyList()), open = null, sex = null)

        assertEquals(NextWorkoutWidgetState(routineName = null, detail = WidgetDetail.NO_ROUTINE), state)
    }

    @Test
    fun `without routines an open session without a logged set still says no routine yet`() {
        val state = widgetStateOf(plan(routines = emptyList()), openEmpty(null), sex = null)

        assertEquals(WidgetDetail.NO_ROUTINE, state.detail)
        assertNull(state.routineName)
    }

    // Rest day

    @Test
    fun `a rest day shows the upcoming routine and rest day`() {
        val state = widgetStateOf(
            plan(restDay = true, upcoming = UpcomingTraining(LocalDate.of(2026, 11, 3), routineB)),
            open = null,
            sex = null,
        )

        assertEquals(NextWorkoutWidgetState(routineName = "Routine B", detail = WidgetDetail.REST_DAY), state)
    }

    @Test
    fun `a rest day without an upcoming training has no name and no detail`() {
        val state = widgetStateOf(plan(restDay = true, upcoming = null), open = null, sex = null)

        assertEquals(NextWorkoutWidgetState(routineName = null, detail = WidgetDetail.NONE), state)
    }

    // Routine choice

    @Test
    fun `a suggested routine with no skipped machines counts all its exercises`() {
        val state = widgetStateOf(
            plan(suggested = routineA, choice = RoutineChoice.Suggested, active = activeA),
            open = null,
            sex = null,
        )

        assertEquals(
            NextWorkoutWidgetState(routineName = "Routine A", detail = WidgetDetail.EXERCISES, exerciseCount = 3),
            state,
        )
    }

    @Test
    fun `a suggested routine with skipped machines counts the exercises not skipped`() {
        val state = widgetStateOf(
            plan(suggested = routineA, choice = RoutineChoice.Suggested, active = activeA, skipped = setOf(squat.id)),
            open = null,
            sex = null,
        )

        assertEquals(WidgetDetail.EXERCISES, state.detail)
        assertEquals(2, state.exerciseCount)
        assertEquals("Routine A", state.routineName)
    }

    @Test
    fun `skipped machines that are not in the routine do not change the count`() {
        val state = widgetStateOf(
            plan(active = activeA, skipped = setOf(99L)),
            open = null,
            sex = null,
        )

        assertEquals(3, state.exerciseCount)
    }

    @Test
    fun `a suggested routine without an active routine gives its name and no detail`() {
        val state = widgetStateOf(
            plan(suggested = routineB, choice = RoutineChoice.Suggested, active = null),
            open = null,
            sex = null,
        )

        assertEquals(NextWorkoutWidgetState(routineName = "Routine B", detail = WidgetDetail.NONE), state)
    }

    @Test
    fun `a free choice has no routine name even when a routine is suggested`() {
        val state = widgetStateOf(
            plan(suggested = routineB, choice = RoutineChoice.Free, active = null),
            open = null,
            sex = null,
        )

        assertEquals(NextWorkoutWidgetState(routineName = null, detail = WidgetDetail.NONE), state)
    }

    @Test
    fun `a chosen routine is the one the widget shows`() {
        val itemsC = listOf(item(12, curl.id), item(12, squat.id))
        val state = widgetStateOf(
            plan(suggested = routineB, choice = RoutineChoice.Chosen(routineC.id), active = RoutineWithItems(routineC, itemsC)),
            open = null,
            sex = null,
        )

        assertEquals(
            NextWorkoutWidgetState(routineName = "Routine C", detail = WidgetDetail.EXERCISES, exerciseCount = 2),
            state,
        )
    }

    @Test
    fun `a workout planned for today is shown like the routine it has`() {
        val state = widgetStateOf(
            plan(suggested = routineA, active = activeA, plannedToday = true),
            open = null,
            sex = null,
        )

        assertEquals(
            NextWorkoutWidgetState(routineName = "Routine A", detail = WidgetDetail.EXERCISES, exerciseCount = 3),
            state,
        )
    }

    // Sex

    @Test
    fun `a female owner is kept in the state`() {
        val state = widgetStateOf(plan(active = activeA), open = null, sex = Sex.FEMALE)

        assertEquals(Sex.FEMALE, state.sex)
    }

    @Test
    fun `a male owner is kept in the state`() {
        val state = widgetStateOf(plan(active = activeA), open = null, sex = Sex.MALE)

        assertEquals(Sex.MALE, state.sex)
    }

    @Test
    fun `an unknown sex stays null in every kind of state`() {
        val states = listOf(
            widgetStateOf(plan(active = activeA), open = null, sex = null),
            widgetStateOf(plan(routines = emptyList()), open = null, sex = null),
            widgetStateOf(plan(restDay = true), open = null, sex = null),
            widgetStateOf(plan(active = activeA), open(10, squat), sex = null),
        )

        states.forEach { assertNull(it.sex) }
    }

    @Test
    fun `the sex is copied into the continue state`() {
        val state = widgetStateOf(plan(active = activeA), open(10, squat), sex = Sex.FEMALE)

        assertEquals(Sex.FEMALE, state.sex)
        assertEquals(WidgetDetail.CONTINUE, state.detail)
    }
}
