package com.boratreino.feature.calendar

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.WeekGoalRules
import com.boratreino.core.model.WorkoutSet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Fixed dates and realistic data for the Calendar screenshots: a week in November 2026, Monday first. */
internal object CalendarSamples {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 4)
    private val monday = LocalDate.of(2026, 11, 2)

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun routine(id: Long, letter: String, position: Int) =
        Routine(id = id, planId = 1L, name = "Routine $letter", shortName = letter, position = position, weekdays = emptySet())

    val routineA = routine(1L, "A", 0)
    val routineB = routine(2L, "B", 1)
    val routineC = routine(3L, "C", 2)
    private val routines = listOf(routineA, routineB, routineC)

    private fun machine(id: Long, name: String, group: MuscleGroup, step: Double = 2.5) =
        Machine(id, name, group, MachineKind.WEIGHT_REPS, step, null, null, archived = false)

    val legPress = machine(1L, "Leg press", MuscleGroup.LEGS, step = 5.0)
    val seatedRow = machine(2L, "Seated row", MuscleGroup.BACK)
    val chestPress = machine(3L, "Chest press", MuscleGroup.CHEST)
    val latPulldown = machine(4L, "Lat pulldown", MuscleGroup.BACK)
    val shoulderPress = machine(5L, "Shoulder press", MuscleGroup.SHOULDERS)
    val biceps = machine(6L, "Bicep curl", MuscleGroup.ARMS)
    val crunch = machine(7L, "Crunch", MuscleGroup.CORE)
    val hamstring = machine(8L, "Hamstring curl", MuscleGroup.LEGS)

    /** Routine B on Monday 2 November: three machines, eight sets. */
    private val sessionOnMonday = Session(
        id = 20L,
        startedAt = at(monday, 18, 5),
        endedAt = at(monday, 19, 10),
        routineId = routineB.id,
        note = null,
    )

    private fun sets(sessionId: Long, machineId: Long, startMinute: Int, vararg rows: Pair<Double, Int>): List<WorkoutSet> =
        rows.mapIndexed { index, (weight, reps) ->
            WorkoutSet(
                id = sessionId * 100 + machineId * 10 + index,
                sessionId = sessionId,
                machineId = machineId,
                position = index,
                weightKg = weight,
                reps = reps,
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = at(monday, 18, startMinute + index * 3),
            )
        }

    private val mondayLegs = sets(20L, 1L, 5, 120.0 to 12, 120.0 to 11, 120.0 to 10)
    private val mondayRow = sets(20L, 2L, 20, 45.0 to 10, 45.0 to 10, 45.0 to 9)
    private val mondayChest = sets(20L, 3L, 40, 60.0 to 12, 60.0 to 12)

    val mondaySession = SessionDetail(
        session = sessionOnMonday,
        routine = routineB,
        machines = listOf(
            SessionMachine(legPress, mondayLegs),
            SessionMachine(seatedRow, mondayRow),
            SessionMachine(chestPress, mondayChest),
        ),
    )

    /** November's badges: C on Sunday 1, B on Monday 2, A on Tuesday 3. */
    private val labels = mapOf(
        LocalDate.of(2026, 11, 1) to "C",
        monday to "B",
        LocalDate.of(2026, 11, 3) to "A",
    )

    /** Goal 4, two days trained this week, a five-week streak, Monday 2 November selected. */
    val monthWithTraining = CalendarUiState(
        loaded = true,
        today = today,
        zone = zone,
        firstDayOfWeek = DayOfWeek.MONDAY,
        month = YearMonth.of(2026, 11),
        selected = monday,
        weeklyGoal = 4,
        daysTrained = 2,
        streak = 5,
        dayLabels = labels,
        sessions = listOf(mondaySession),
    )

    /** Nothing logged today; the week is goal 3 with no streak yet. */
    val emptyToday = monthWithTraining.copy(
        selected = today,
        weeklyGoal = WeekGoalRules.DEFAULT_GOAL,
        daysTrained = 0,
        streak = 0,
        sessions = emptyList(),
    )

    val goalDialog = monthWithTraining.copy(goalEditor = 4)

    /** The session editor for Monday 2 November, Routine B selected, three machines with sets. */
    val editorState = SessionEditorUiState(
        loaded = true,
        detail = mondaySession,
        date = monday,
        routines = routines,
        machines = listOf(legPress, seatedRow, chestPress, latPulldown, shoulderPress, biceps, crunch, hamstring),
    )

    /** The machine picker: your machines grouped by muscle group. */
    val editorPicking = editorState.copy(picking = true)
}
