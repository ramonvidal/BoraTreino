package com.boratreino.core.data

import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

/** What the "next" data reports: the last trained time per routine and the routine of the last finished session. */
class RoutineLastTrainedTest {
    private lateinit var store: FakeWorkoutStore
    private lateinit var routines: RoutineRepository

    @Before
    fun setUp() {
        store = FakeWorkoutStore()
        routines = DefaultRoutineRepository(store)
    }

    private suspend fun routine(name: String, shortName: String, weekdays: Set<DayOfWeek> = emptySet()): Long =
        routines.saveRoutine(Routine(id = 0, planId = 0, name = name, shortName = shortName, position = 0, weekdays = weekdays), emptyList())

    private suspend fun session(routineId: Long?, startedAt: String, endedAt: String?) {
        store.insertSession(
            Session(
                id = 0,
                startedAt = Instant.parse(startedAt),
                endedAt = endedAt?.let { Instant.parse(it) },
                routineId = routineId,
                note = null,
            ),
        )
    }

    // Last trained

    @Test
    fun `a routine never trained is absent from the last trained map`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")

        val trained = routines.observeLastTrained().first()

        assertEquals(setOf(a), trained.keys)
        assertNull(trained[b])
    }

    @Test
    fun `the last trained time is the start of the latest finished session of the routine`() = runTest {
        val a = routine("Treino A", "A")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(a, "2026-11-05T18:00:00Z", "2026-11-05T19:00:00Z")
        session(a, "2026-11-03T18:00:00Z", "2026-11-03T19:00:00Z")

        val trained = routines.observeLastTrained().first()

        assertEquals(Instant.parse("2026-11-05T18:00:00Z"), trained[a])
    }

    @Test
    fun `each routine keeps its own latest session`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(b, "2026-11-04T18:00:00Z", "2026-11-04T19:00:00Z")

        val trained = routines.observeLastTrained().first()

        assertEquals(Instant.parse("2026-11-02T18:00:00Z"), trained[a])
        assertEquals(Instant.parse("2026-11-04T18:00:00Z"), trained[b])
    }

    @Test
    fun `an open session does not count as trained`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(a, "2026-11-09T18:00:00Z", null)
        session(b, "2026-11-09T18:00:00Z", null)

        val trained = routines.observeLastTrained().first()

        assertEquals(Instant.parse("2026-11-02T18:00:00Z"), trained[a])
        assertNull(trained[b])
    }

    @Test
    fun `a session without a routine counts for no routine`() = runTest {
        val a = routine("Treino A", "A")
        session(null, "2026-11-09T18:00:00Z", "2026-11-09T19:00:00Z")

        assertEquals(emptyMap<Long, Instant>(), routines.observeLastTrained().first())
        assertNull(routines.observeLastTrained().first()[a])
    }

    // Last routine

    @Test
    fun `the last routine is the one of the most recent finished session`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(b, "2026-11-04T18:00:00Z", "2026-11-04T19:00:00Z")

        assertEquals(b, routines.observeLastRoutineId().first())
    }

    @Test
    fun `an open session is ignored for the last routine`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(b, "2026-11-09T18:00:00Z", null)

        assertEquals(a, routines.observeLastRoutineId().first())
    }

    @Test
    fun `no last routine when nothing has been trained`() = runTest {
        routine("Treino A", "A")

        assertNull(routines.observeLastRoutineId().first())
    }

    @Test
    fun `the last routine skips a later session of a routine on specific days`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B", weekdays = setOf(DayOfWeek.THURSDAY))
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(b, "2026-11-05T18:00:00Z", "2026-11-05T19:00:00Z")

        assertEquals(a, routines.observeLastRoutineId().first())
    }

    @Test
    fun `no last routine when only routines on specific days were trained`() = runTest {
        val b = routine("Treino B", "B", weekdays = setOf(DayOfWeek.THURSDAY))
        session(b, "2026-11-05T18:00:00Z", "2026-11-05T19:00:00Z")

        assertNull(routines.observeLastRoutineId().first())
    }

    @Test
    fun `a routine moved to specific days is no longer the last routine`() = runTest {
        val a = routine("Treino A", "A")
        val b = routine("Treino B", "B")
        session(a, "2026-11-02T18:00:00Z", "2026-11-02T19:00:00Z")
        session(b, "2026-11-04T18:00:00Z", "2026-11-04T19:00:00Z")
        val stored = requireNotNull(routines.observeRoutine(b).first()).routine
        routines.saveRoutine(stored.copy(weekdays = setOf(DayOfWeek.THURSDAY)), emptyList())

        assertEquals(a, routines.observeLastRoutineId().first())
    }
}
