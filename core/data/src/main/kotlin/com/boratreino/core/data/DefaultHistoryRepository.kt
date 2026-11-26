package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.CalendarRules
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.HealthExportRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RepeatSessionRules
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.model.working
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
                val own = setsBySession[session.id].orEmpty().working()
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

    /** Today's open session when [date] is today, else null. */
    private suspend fun openSessionOn(date: LocalDate, now: ZonedDateTime): Session? =
        if (date == now.toLocalDate()) store.openSession() else null

    /** A new closed session on [date] (CalendarRules.addedSessionStart, endedAt = start), not stored. */
    private fun newSessionOn(date: LocalDate, now: ZonedDateTime, routineId: Long?, isExtra: Boolean): Session {
        val start = CalendarRules.addedSessionStart(date, now)
        return Session(id = 0, startedAt = start, endedAt = start, routineId = routineId, note = null, isExtra = isExtra)
    }

    /** The open session (today) or a new one with [build]'s set as its first, in one transaction. */
    private suspend fun addSessionWith(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        machineId: Long,
        build: (sessionId: Long, position: Int, loggedAt: Instant) -> WorkoutSet,
    ): WorkoutSet {
        val now = ZonedDateTime.now(clock)
        return store.inTransaction {
            val open = openSessionOn(date, now)
            if (open != null) return@inTransaction writeSet(open.id, machineId, build)
            val session = newSessionOn(date, now, routineId, isExtra)
            val sessionId = store.insertSession(session)
            val set = build(sessionId, 0, session.startedAt)
            set.copy(id = store.insertSet(set))
        }
    }

    override suspend fun addSessionWithSet(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        machineId: Long,
        draft: SetDraft,
    ): WorkoutSet = addSessionWith(date, routineId, isExtra, machineId) { sessionId, position, loggedAt ->
        weightSet(sessionId, machineId, position, loggedAt, draft)
    }

    override suspend fun addSessionWithCardio(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        machineId: Long,
        draft: CardioDraft,
    ): WorkoutSet = addSessionWith(date, routineId, isExtra, machineId) { sessionId, position, loggedAt ->
        CardioRules.toSet(id = 0, sessionId = sessionId, machineId = machineId, position = position, draft = draft, loggedAt = loggedAt)
    }

    override suspend fun addSessionRepeating(
        date: LocalDate,
        routineId: Long?,
        isExtra: Boolean,
        mode: RepeatMode,
    ): List<WorkoutSet> {
        val now = ZonedDateTime.now(clock)
        openSessionOn(date, now)?.let { return repeatLastSession(it.id, mode) }
        routineId ?: return emptyList()
        val session = newSessionOn(date, now, routineId, isExtra)
        // Flows are read before the write transaction.
        val previous = store.observeSessions().first()
            .filter { it.routineId == routineId && it.startedAt < session.startedAt }
            .maxByOrNull { it.startedAt }
            ?: return emptyList()
        val items = store.routineItems(routineId)
        val suggestions = if (mode == RepeatMode.SUGGESTED) store.repeatSuggestions(items, NO_SESSION) else emptyMap()
        return store.inTransaction {
            // A session opened meanwhile today: leave it alone, the owner can repeat again.
            if (openSessionOn(date, now) != null) return@inTransaction emptyList()
            val planned = RepeatSessionRules.plan(items, store.sessionSets(previous.id), emptySet(), mode, suggestions)
            if (planned.isEmpty()) return@inTransaction emptyList()
            val sessionId = store.insertSession(session)
            val counts = HashMap<Long, Int>()
            val saved = planned.mapIndexed { index, p ->
                val position = counts[p.machineId] ?: 0
                counts[p.machineId] = position + 1
                val set = p.toWorkoutSet(sessionId, position, session.startedAt.plusSeconds(index.toLong()))
                set.copy(id = store.insertSet(set))
            }
            store.setSessionEnd(sessionId, saved.maxOf { it.loggedAt })
            saved
        }
    }

    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) {
        store.setSessionRoutine(sessionId, routineId)
    }

    override suspend fun setSessionExtra(sessionId: Long, extra: Boolean) {
        store.setSessionExtra(sessionId, extra)
    }

    override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet =
        store.inTransaction {
            writeSet(sessionId, machineId) { id, position, loggedAt -> weightSet(id, machineId, position, loggedAt, draft) }
        }

    override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet =
        store.inTransaction {
            writeSet(sessionId, machineId) { id, position, loggedAt ->
                CardioRules.toSet(id = 0, sessionId = id, machineId = machineId, position = position, draft = draft, loggedAt = loggedAt)
            }
        }

    /**
     * Writes [build]'s set into an existing session: position = the machine's sets in it; loggedAt = the session's
     * last set + 1 minute, or its start; a closed session's endedAt moves to loggedAt if later. Call inside a transaction.
     */
    private suspend fun writeSet(
        sessionId: Long,
        machineId: Long,
        build: (sessionId: Long, position: Int, loggedAt: Instant) -> WorkoutSet,
    ): WorkoutSet {
        val session = store.session(sessionId) ?: throw IllegalArgumentException("No session $sessionId")
        val loggedAt = store.lastSetAt(sessionId)?.plusSeconds(60) ?: session.startedAt
        val set = build(sessionId, store.setCount(sessionId, machineId), loggedAt)
        val saved = set.copy(id = store.insertSet(set))
        val endedAt = session.endedAt
        if (endedAt != null && loggedAt > endedAt) store.setSessionEnd(sessionId, loggedAt)
        return saved
    }

    private fun weightSet(sessionId: Long, machineId: Long, position: Int, loggedAt: Instant, draft: SetDraft) = WorkoutSet(
        id = 0,
        sessionId = sessionId,
        machineId = machineId,
        position = position,
        weightKg = draft.weightKg,
        reps = draft.reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = loggedAt,
        type = draft.type,
    )

    override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> {
        // Flows are read before the write transaction.
        val session = store.session(sessionId) ?: throw IllegalArgumentException("No session $sessionId")
        val routineId = session.routineId ?: return emptyList()
        val previous = store.observeSessions().first()
            .filter { it.routineId == routineId && it.id != sessionId && it.startedAt < session.startedAt }
            .maxByOrNull { it.startedAt }
            ?: return emptyList()
        val items = store.routineItems(routineId)
        val suggestions = if (mode == RepeatMode.SUGGESTED) store.repeatSuggestions(items, sessionId) else emptyMap()
        return store.inTransaction {
            val current = store.session(sessionId) ?: throw IllegalArgumentException("No session $sessionId")
            val existing = store.sessionSets(sessionId)
            val planned = RepeatSessionRules.plan(
                items, store.sessionSets(previous.id), existing.map { it.machineId }.toSet(), mode, suggestions,
            )
            if (planned.isEmpty()) return@inTransaction emptyList()
            val base = store.lastSetAt(sessionId)?.plusSeconds(60) ?: current.startedAt
            val counts = HashMap<Long, Int>()
            val saved = planned.mapIndexed { index, p ->
                val position = counts[p.machineId] ?: store.setCount(sessionId, p.machineId)
                counts[p.machineId] = position + 1
                val set = p.toWorkoutSet(sessionId, position, base.plusSeconds(index.toLong()))
                set.copy(id = store.insertSet(set))
            }
            val last = saved.maxOf { it.loggedAt }
            val endedAt = current.endedAt
            if (endedAt != null && last > endedAt) store.setSessionEnd(sessionId, last)
            saved
        }
    }

    override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> {
        val since = LocalDate.now(clock).minusDays(sinceDays.toLong()).startInstant()
        val until = LocalDate.now(clock).plusDays(1).startInstant()
        return combine(
            store.observeSessionsStartedBetween(since, until),
            store.observeSetsOfSessionsStartedBetween(since, until),
            store.observeMachines(includeArchived = true),
            store.observeAllRoutines(),
        ) { sessions, sets, machines, routines ->
            val setsBySession = sets.groupBy { it.sessionId }
            val machineById = machines.associateBy { it.id }
            val routineById = routines.associateBy { it.id }
            sessions
                .filter { it.endedAt != null }
                .mapNotNull { session ->
                    HealthExportRules.export(
                        session,
                        setsBySession[session.id].orEmpty(),
                        machineById,
                        session.routineId?.let(routineById::get)?.name,
                    )
                }
        }.distinctUntilChanged()
    }

    override fun observeSessionIds(): Flow<Set<Long>> =
        store.observeSessions()
            .map { sessions -> sessions.mapTo(HashSet()) { it.id } as Set<Long> }
            .distinctUntilChanged()

    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> =
        store.lastTimeBefore(sessionId, machineId)

    override suspend fun lastTimeBeforeDate(date: LocalDate, machineId: Long): List<WorkoutSet> {
        val now = ZonedDateTime.now(clock)
        openSessionOn(date, now)?.let { return store.lastTimeBefore(it.id, machineId) }
        val start = CalendarRules.addedSessionStart(date, now)
        val bySession = store.machineSets(machineId).groupBy { it.sessionId }
        val latest = store.observeSessions().first()
            .filter { it.startedAt < start && it.id in bySession }
            .maxWithOrNull(compareBy({ it.startedAt }, { it.id }))
            ?: return emptyList()
        return bySession.getValue(latest.id).sortedWith(compareBy({ it.position }, { it.id }))
    }

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

    private companion object {
        /** No session has this id, so "other than" excludes nothing. */
        const val NO_SESSION = -1L
    }
}
