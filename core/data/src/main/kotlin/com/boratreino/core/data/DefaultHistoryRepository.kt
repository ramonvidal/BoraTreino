package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.CalendarRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

internal class DefaultHistoryRepository @Inject constructor(
    private val store: WorkoutStore,
    private val clock: Clock,
    private val training: DefaultTrainingRepository,
) : HistoryRepository {
    private val zone: ZoneId get() = clock.zone

    private fun LocalDate.startInstant(): Instant = atStartOfDay(zone).toInstant()

    override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> =
        combine(
            store.observeSessionsStartedBetween(
                month.atDay(1).startInstant(),
                month.plusMonths(1).atDay(1).startInstant(),
            ),
            store.observeSetsOfSessionsStartedBetween(
                month.atDay(1).startInstant(),
                month.plusMonths(1).atDay(1).startInstant(),
            ),
            store.observeAllRoutines(),
        ) { sessions, sets, routines ->
            val routineById = routines.associateBy { it.id }
            val setsBySession = sets.groupBy { it.sessionId }
            sessions.map { session ->
                val own = setsBySession[session.id].orEmpty()
                SessionInfo(
                    session = session,
                    routine = session.routineId?.let(routineById::get),
                    machineCount = own.map { it.machineId }.distinct().size,
                    setCount = own.size,
                )
            }.groupBy { it.session.startedAt.atZone(zone).toLocalDate() }
        }

    override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> {
        val start = date.startInstant()
        val end = date.plusDays(1).startInstant()
        return combine(
            store.observeSessionsStartedBetween(start, end),
            store.observeSetsOfSessionsStartedBetween(start, end),
            store.observeMachines(includeArchived = true),
            store.observeAllRoutines(),
        ) { sessions, sets, machines, routines ->
            val setsBySession = sets.groupBy { it.sessionId }
            sessions.map { detail(it, setsBySession[it.id].orEmpty(), machines, routines) }
        }
    }

    override fun observeSession(sessionId: Long): Flow<SessionDetail?> =
        combine(
            store.observeSession(sessionId),
            store.observeSessionSets(sessionId),
            store.observeMachines(includeArchived = true),
            store.observeAllRoutines(),
        ) { session, sets, machines, routines ->
            session?.let { detail(it, sets, machines, routines) }
        }

    override fun observeTrainingDates(): Flow<Set<LocalDate>> =
        store.observeSessionStarts()
            .map { starts -> starts.mapTo(HashSet()) { it.atZone(zone).toLocalDate() } as Set<LocalDate> }
            .distinctUntilChanged()

    override suspend fun addSession(date: LocalDate, routineId: Long?): Long {
        val now = ZonedDateTime.now(clock)
        return store.inTransaction {
            if (date == now.toLocalDate()) {
                store.openSession()?.let { return@inTransaction it.id }
            }
            val start = CalendarRules.addedSessionStart(date, now)
            store.insertSession(
                Session(id = 0, startedAt = start, endedAt = start, routineId = routineId, note = null),
            )
        }
    }

    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) {
        store.setSessionRoutine(sessionId, routineId)
    }

    override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet =
        store.inTransaction {
            val session = store.session(sessionId) ?: throw IllegalArgumentException("No session $sessionId")
            val loggedAt = store.lastSetAt(sessionId)?.plusSeconds(60) ?: session.startedAt
            val set = WorkoutSet(
                id = 0,
                sessionId = sessionId,
                machineId = machineId,
                position = store.setCount(sessionId, machineId),
                weightKg = draft.weightKg,
                reps = draft.reps,
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = loggedAt,
            )
            val saved = set.copy(id = store.insertSet(set))
            val endedAt = session.endedAt
            if (endedAt != null && loggedAt > endedAt) store.setSessionEnd(sessionId, loggedAt)
            saved
        }

    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> =
        store.lastTimeBefore(sessionId, machineId)

    override suspend fun deleteSession(sessionId: Long): SessionDetail? = store.inTransaction {
        val session = store.session(sessionId) ?: return@inTransaction null
        val detail = detail(
            session,
            store.sessionSets(sessionId),
            store.machines(),
            store.allRoutines(),
        )
        store.deleteSession(sessionId)
        if (session.isOpen) training.resetChoice()
        detail
    }

    override suspend fun restoreSession(detail: SessionDetail) {
        store.inTransaction {
            // The routine may have been deleted since; the FK would refuse a missing one.
            val routineId = detail.session.routineId?.takeIf { id -> store.allRoutines().any { it.id == id } }
            store.restoreSession(
                detail.session.copy(routineId = routineId),
                detail.machines.flatMap { it.sets },
            )
        }
    }

    /** Machines in order of their first set; each machine's sets in position order. */
    private fun detail(
        session: Session,
        sets: List<WorkoutSet>,
        machines: List<Machine>,
        routines: List<Routine>,
    ): SessionDetail {
        val machineById = machines.associateBy { it.id }
        val ordered = sets.groupBy { it.machineId }.entries
            .mapNotNull { (machineId, own) ->
                val machine = machineById[machineId] ?: return@mapNotNull null
                val first = own.minWith(compareBy({ it.loggedAt }, { it.id }))
                Triple(SessionMachine(machine, own.sortedWith(compareBy({ it.position }, { it.id }))), first.loggedAt, first.id)
            }
            .sortedWith(compareBy<Triple<SessionMachine, Instant, Long>>({ it.second }, { it.third }))
            .map { it.first }
        return SessionDetail(session, session.routineId?.let { id -> routines.firstOrNull { it.id == id } }, ordered)
    }
}
