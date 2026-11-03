package com.boratreino.core.data

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.Session
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Cardio in the progress repository. The first day of the week follows the phone's locale, so the
 * sessions here sit on days that fall in the same week for Monday-first and Sunday-first weeks.
 */
class CardioProgressTest {
    // Monday, 2 November 2026, evening (UTC).
    private val monday: Instant = Instant.parse("2026-11-02T18:00:00Z")
    private lateinit var store: FakeWorkoutStore
    private lateinit var repo: ProgressRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        repo = DefaultProgressRepository(store, TestClock(monday))
    }

    private suspend fun cardioMachine(name: String = "Treadmill"): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, false))

    private suspend fun weightMachine(name: String = "Chest press"): Long =
        store.saveMachine(Machine(0, name, MuscleGroup.CHEST, MachineKind.WEIGHT_REPS, 2.5, null, null, false))

    private suspend fun session(startedAt: String): Long {
        val start = Instant.parse(startedAt)
        return store.insertSession(Session(0, start, start.plusSeconds(3600), null, null))
    }

    private suspend fun cardio(sessionId: Long, machineId: Long, minutes: Int, calories: Int? = null, distanceM: Int? = null, position: Int = 0) {
        val loggedAt = store.session(sessionId)!!.startedAt
        store.insertSet(WorkoutSet(0, sessionId, machineId, position, null, null, minutes * 60, calories, distanceM, loggedAt))
    }

    private suspend fun weights(sessionId: Long, machineId: Long) {
        val loggedAt = store.session(sessionId)!!.startedAt
        store.insertSet(WorkoutSet(0, sessionId, machineId, 0, 40.0, 10, null, null, null, loggedAt))
    }

    // observeMachineProgress

    @Test
    fun `a cardio machine measures minutes, one point per session`() = runTest {
        val treadmill = cardioMachine()
        val a = session("2026-10-20T10:00:00Z")
        val b = session("2026-10-27T10:00:00Z")
        cardio(a, treadmill, 20, 150)
        cardio(a, treadmill, 10, 70, position = 1)
        cardio(b, treadmill, 45, 320)

        val progress = requireNotNull(repo.observeMachineProgress(treadmill).first())

        assertEquals(ProgressMetric.MINUTES, progress.metric)
        assertEquals(listOf(a, b), progress.points.map { it.sessionId })
        assertEquals(listOf(30.0, 45.0), progress.points.map { it.value })
    }

    @Test
    fun `a cardio machine has 12 weeks, oldest first, ending with this week`() = runTest {
        val treadmill = cardioMachine()
        val today = session("2026-11-02T10:00:00Z")
        val lastWeek = session("2026-10-27T10:00:00Z")
        cardio(today, treadmill, 30, 250, 5000)
        cardio(lastWeek, treadmill, 45, 320)

        val weeks = requireNotNull(repo.observeMachineProgress(treadmill).first()).weeks

        assertEquals(12, weeks.size)
        assertEquals(weeks.map { it.weekStart }.sorted(), weeks.map { it.weekStart })
        assertEquals(7L, weeks[11].weekStart.toEpochDay() - weeks[10].weekStart.toEpochDay())
        assertEquals(30, weeks[11].minutes)
        assertEquals(250, weeks[11].calories)
        assertEquals(5000, weeks[11].distanceM)
        assertEquals(1, weeks[11].sessions)
        assertEquals(45, weeks[10].minutes)
        assertEquals(320, weeks[10].calories)
        assertTrue(weeks.take(10).all { it.minutes == 0 && it.sessions == 0 })
    }

    @Test
    fun `the weeks hold only this machine's sets`() = runTest {
        val treadmill = cardioMachine()
        val bike = cardioMachine("Stationary bike")
        val s = session("2026-11-02T10:00:00Z")
        cardio(s, treadmill, 30)
        cardio(s, bike, 15)

        val weeks = requireNotNull(repo.observeMachineProgress(treadmill).first()).weeks

        assertEquals(30, weeks.last().minutes)
    }

    @Test
    fun `a weight machine has no weeks`() = runTest {
        val press = weightMachine()
        val s = session("2026-11-02T10:00:00Z")
        weights(s, press)

        val progress = requireNotNull(repo.observeMachineProgress(press).first())

        assertTrue(progress.weeks.isEmpty())
        assertEquals(ProgressMetric.WEIGHT, progress.metric)
    }

    @Test
    fun `a cardio machine with no sets has 12 empty weeks and no points`() = runTest {
        val treadmill = cardioMachine()

        val progress = requireNotNull(repo.observeMachineProgress(treadmill).first())

        assertTrue(progress.points.isEmpty())
        assertEquals(12, progress.weeks.size)
        assertTrue(progress.weeks.all { it.minutes == 0 })
    }

    // observeOverview

    @Test
    fun `the overview totals this week's cardio across machines`() = runTest {
        val treadmill = cardioMachine()
        val bike = cardioMachine("Stationary bike")
        val monday = session("2026-11-02T10:00:00Z")
        val tuesday = session("2026-11-03T10:00:00Z")
        val before = session("2026-10-20T10:00:00Z")
        cardio(monday, treadmill, 20, 150, 3000)
        cardio(tuesday, bike, 25, 180)
        cardio(before, treadmill, 60, 500)

        val week = requireNotNull(repo.observeOverview().first().cardioThisWeek)

        assertEquals(45, week.minutes)
        assertEquals(330, week.calories)
        assertEquals(3000, week.distanceM)
        assertEquals(2, week.sessions)
    }

    @Test
    fun `weight sets do not count in this week's cardio`() = runTest {
        val press = weightMachine()
        val treadmill = cardioMachine()
        val s = session("2026-11-02T10:00:00Z")
        weights(s, press)
        cardio(s, treadmill, 15, 90)

        val week = requireNotNull(repo.observeOverview().first().cardioThisWeek)

        assertEquals(15, week.minutes)
        assertEquals(90, week.calories)
        assertEquals(1, week.sessions)
    }

    @Test
    fun `an overview with no cardio has nothing for this week`() = runTest {
        val press = weightMachine()
        val s = session("2026-11-02T10:00:00Z")
        weights(s, press)

        val week = repo.observeOverview().first().cardioThisWeek

        assertEquals(0, week?.minutes ?: 0)
        assertEquals(0, week?.calories ?: 0)
    }

    @Test
    fun `a cardio machine appears in the overview with its minutes and weeks`() = runTest {
        val treadmill = cardioMachine()
        val s = session("2026-11-02T10:00:00Z")
        cardio(s, treadmill, 40, 300)

        val entry = repo.observeOverview().first().machines.single()

        assertEquals(ProgressMetric.MINUTES, entry.metric)
        assertEquals(listOf(40.0), entry.points.map { it.value })
        assertNotNull(entry.weeks)
        assertEquals(12, entry.weeks.size)
    }
}
