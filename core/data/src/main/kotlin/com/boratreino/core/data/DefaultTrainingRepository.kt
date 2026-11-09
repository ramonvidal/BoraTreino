package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.NextRoutineRules
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.ProgressionRules
import com.boratreino.core.model.RecordRules
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RepeatSessionRules
import com.boratreino.core.model.Routine
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionRules
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.RoutineUpdateRules
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.model.TrainingSummaryRules
import com.boratreino.core.model.withoutMachines
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultTrainingRepository @Inject constructor(
    private val store: WorkoutStore,
    private val clock: Clock,
    private val routines: RoutineRepository,
) : TrainingRepository {
    /** The owner's pick for today's training; in memory only, back to Suggested when a session ends. */
    private val choice = MutableStateFlow<RoutineChoice>(RoutineChoice.Suggested)

    /** Machines hidden from today's ficha for this training; in memory, cleared with the choice. */
    private val skipped = MutableStateFlow<Set<Long>>(emptySet())

    /** Back to Suggested with nothing skipped; used wherever a session ends or is replaced. */
    internal fun resetChoice() {
        choice.value = RoutineChoice.Suggested
        skipped.value = emptySet()
    }

    override suspend fun skipMachineToday(machineId: Long) {
        skipped.value = skipped.value + machineId
    }

    override suspend fun unskipMachineToday(machineId: Long) {
        skipped.value = skipped.value - machineId
    }

    private fun todayDate() = LocalDate.now(clock)

    private fun today() = todayDate().dayOfWeek

    /** The last date [dayFlow] reported, to reset the in-memory choice only when the day really changes. */
    @Volatile
    private var lastSeenDate: LocalDate? = null

    /**
     * Today's date, re-emitted after the next midnight so Today follows the day when the app stays open.
     * If the date has not changed after waking up (a clock that does not move, as in tests, or an early wake-up),
     * it checks once more a minute later and then stops, so it never spins.
     * A change of date also resets the in-memory choice (a routine browsed or picked yesterday must not stick).
     */
    private val dayFlow: Flow<LocalDate> = flow {
        var date = LocalDate.now(clock)
        emit(date)
        var unchanged = 0
        while (unchanged < MAX_UNCHANGED_WAKES) {
            val now = ZonedDateTime.now(clock)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val wait = if (unchanged == 0) Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_MARGIN_MS else RECHECK_MS
            delay(wait)
            val again = LocalDate.now(clock)
            if (again != date) {
                date = again
                emit(date)
                unchanged = 0
            } else {
                unchanged++
            }
        }
    }.distinctUntilChanged().onEach { date ->
        val previous = lastSeenDate
        lastSeenDate = date
        if (previous != null && previous != date) resetChoice()
    }

    /** Routines planned on [date] that exist in [all], by position. */
    private fun plannedOn(date: LocalDate, planned: List<PlannedWorkout>, all: List<Routine>): List<Routine> {
        val byId = all.associateBy { it.id }
        return planned.filter { it.date == date }.mapNotNull { byId[it.routineId] }.distinct().sortedBy { it.position }
    }

    /** Ids of the routines of the CLOSED sessions started on [date]; an open session does not count as done. */
    private fun doneOn(date: LocalDate, sessions: List<Session>): Set<Long> {
        val zone = clock.zone
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return sessions.filter { it.endedAt != null && it.startedAt >= start && it.startedAt < end }.mapNotNull { it.routineId }.toSet()
    }

    private fun sessionsOn(date: LocalDate): Flow<List<Session>> {
        val zone = clock.zone
        return store.observeSessionsStartedBetween(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTodayPlan(): Flow<TodayPlan> = dayFlow.flatMapLatest { date -> observePlanFor(date) }

    private fun observePlanFor(date: LocalDate): Flow<TodayPlan> =
        combine(
            routines.observePlan(),
            combine(
                store.observeLastRoutineId(),
                routines.observePlanned(),
                sessionsOn(date),
            ) { last, planned, sessions -> Triple(last, planned, sessions) },
            store.observeOpenSession(),
            choice,
            skipped,
        ) { plan, inner, open, choice, skippedIds ->
            val (lastRoutineId, plannedList, todaySessions) = inner
            val all = plan.routines.map { it.routine }
            val mode = plan.plan.scheduleMode
            val trainingDays = plan.plan.trainingDays
            val plannedToday = plannedOn(date, plannedList, all)
            val doneToday = doneOn(date, todaySessions)
            val suggested = NextRoutineRules.next(
                mode, all, lastRoutineId, date.dayOfWeek, trainingDays, plannedToday, doneToday,
            )
            val restDay = suggested == null && all.isNotEmpty()
            // An open session's own routine decides (null = Free); the choice only counts before one opens.
            val activeId = if (open != null) {
                open.routineId
            } else {
                when (choice) {
                    RoutineChoice.Suggested -> suggested?.id
                    RoutineChoice.Free -> null
                    is RoutineChoice.Chosen -> choice.routineId
                }
            }
            TodayPlan(
                mode = mode,
                routines = all,
                suggested = suggested,
                choice = choice,
                active = activeId
                    ?.let { id -> plan.routines.firstOrNull { it.routine.id == id } }
                    ?.withoutMachines(skippedIds),
                restDay = restDay,
                upcoming = if (restDay) {
                    NextRoutineRules.upcoming(mode, all, lastRoutineId, trainingDays, date, plannedList)
                } else {
                    null
                },
                skipped = skippedIds,
                plannedToday = plannedToday.isNotEmpty(),
            )
        }

    override suspend fun chooseRoutine(choice: RoutineChoice) {
        this.choice.value = choice
        store.inTransaction {
            val open = store.openSession() ?: return@inTransaction
            store.setSessionRoutine(open.id, routineIdFor(choice))
        }
    }

    /** The routine id a session gets for [choice]; null for Free, no routines or a routine that is gone. */
    private suspend fun routineIdFor(choice: RoutineChoice): Long? {
        val plan = store.plan() ?: return null
        val all = store.routines(plan.id)
        return when (choice) {
            RoutineChoice.Free -> null
            is RoutineChoice.Chosen -> all.firstOrNull { it.id == choice.routineId }?.id
            RoutineChoice.Suggested ->
                NextRoutineRules.next(
                    plan.scheduleMode,
                    all,
                    store.lastRoutineId(),
                    today(),
                    plan.trainingDays,
                    plannedOn(todayDate(), store.observePlannedWorkouts().first(), all),
                    doneOn(todayDate(), sessionsOn(todayDate()).first()),
                )?.id
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOpenSession(): Flow<OpenSession?> =
        store.observeOpenSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    store.observeSessionSets(session.id),
                    store.observeMachines(includeArchived = true),
                ) { sets, machines -> openSession(session, sets, machines) }
            }
        }

    /** Machines with sets in the session, most recently logged first; their sets in position order. */
    private fun openSession(session: Session, sets: List<WorkoutSet>, machines: List<Machine>): OpenSession {
        val byId = machines.associateBy { it.id }
        val grouped = sets.groupBy { it.machineId }
        val ordered = grouped.entries
            .mapNotNull { (machineId, machineSets) ->
                val machine = byId[machineId] ?: return@mapNotNull null
                val sorted = machineSets.sortedWith(compareBy({ it.position }, { it.id }))
                val latest = machineSets.maxWith(compareBy({ it.loggedAt }, { it.id }))
                Triple(SessionMachine(machine, sorted), latest.loggedAt, latest.id)
            }
            .sortedWith(compareByDescending<Triple<SessionMachine, Instant, Long>> { it.second }.thenByDescending { it.third })
            .map { it.first }
        return OpenSession(session, ordered)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMachineLog(machineId: Long): Flow<MachineLog?> =
        combine(
            store.observeMachine(machineId),
            store.observeOpenSession().map { it?.id }.distinctUntilChanged(),
            observeTodayPlan()
                .map { plan -> plan.active?.items?.firstOrNull { it.machineId == machineId } }
                .distinctUntilChanged(),
        ) { machine, openId, target -> Triple(machine, openId, target) }
            .flatMapLatest { (machine, openId, target) ->
                if (machine == null) {
                    flowOf(null)
                } else {
                    val thisSession = if (openId == null) {
                        flowOf(emptyList())
                    } else {
                        store.observeMachineSets(openId, machineId)
                    }
                    combine(
                        store.observeLastSessionSets(machineId, openId ?: NO_SESSION, HISTORY_SESSIONS),
                        thisSession,
                    ) { history, current ->
                        val lastTime = history.firstOrNull().orEmpty()
                        MachineLog(
                            machine = machine,
                            lastTime = lastTime,
                            // The day of the last visit: its latest set's time.
                            lastTimeAt = lastTime.maxOfOrNull { it.loggedAt },
                            thisSession = current,
                            target = target,
                            suggestion = if (machine.kind == MachineKind.CARDIO) null else ProgressionRules.suggest(target, machine.stepKg, history),
                        )
                    }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRepeatOffer(): Flow<RepeatOffer?> =
        combine(observeTodayPlan(), store.observeOpenSession()) { plan, open -> plan.active to open }
            .distinctUntilChanged()
            .flatMapLatest { (active, open) ->
                if (active == null) {
                    flowOf(null)
                } else {
                    val before = open?.startedAt ?: Instant.now(clock)
                    val ownId = open?.id
                    combine(
                        store.observeSessions(),
                        store.observeAllSets(),
                    ) { sessions, sets ->
                        val previous = sessions
                            .filter { it.routineId == active.routine.id && it.id != ownId && it.startedAt < before }
                            .maxByOrNull { it.startedAt }
                        if (previous == null) return@combine null
                        val today = if (ownId == null) emptySet() else sets.filter { it.sessionId == ownId }.map { it.machineId }.toSet()
                        val planned = RepeatSessionRules.plan(
                            items = active.items,
                            lastSets = sets.filter { it.sessionId == previous.id },
                            machineIdsWithSetsToday = today,
                            mode = RepeatMode.SAME,
                            suggestions = emptyMap(),
                        )
                        if (planned.isEmpty()) {
                            null
                        } else {
                            RepeatOffer(active.routine.id, RepeatSessionRules.machineCount(planned), planned.size)
                        }
                    }
                }
            }
            .distinctUntilChanged()

    override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? {
        // Flows are read before the write transaction; closing a stale session first keeps both consistent.
        closeStaleSession()
        val now = Instant.now(clock)
        val open = store.openSession()
        val routineId = if (open != null) open.routineId else routineIdFor(choice.value)
        if (routineId == null) return null
        val items = store.routineItems(routineId).filter { it.machineId !in skipped.value }
        val before = open?.startedAt ?: now
        val previous = store.observeSessions().first()
            .filter { it.routineId == routineId && it.id != open?.id && it.startedAt < before }
            .maxByOrNull { it.startedAt }
            ?: return null
        val suggestions = if (mode == RepeatMode.SUGGESTED) store.repeatSuggestions(items, open?.id ?: NO_SESSION) else emptyMap()
        return store.inTransaction {
            val current = store.openSession()
            if (current?.id != open?.id) return@inTransaction null
            val doneToday = if (current == null) emptySet() else store.sessionSets(current.id).map { it.machineId }.toSet()
            val planned = RepeatSessionRules.plan(items, store.sessionSets(previous.id), doneToday, mode, suggestions)
            if (planned.isEmpty()) return@inTransaction null
            val sessionId = current?.id
                ?: store.insertSession(Session(id = 0, startedAt = now, endedAt = null, routineId = routineId, note = null))
            val counts = HashMap<Long, Int>()
            val ids = planned.mapIndexed { index, p ->
                val position = counts[p.machineId] ?: store.setCount(sessionId, p.machineId)
                counts[p.machineId] = position + 1
                store.insertSet(p.toWorkoutSet(sessionId, position, now.plusSeconds(index.toLong())))
            }
            RepeatOutcome(sessionId, current == null, ids, RepeatSessionRules.machineCount(planned))
        }
    }

    override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome {
        val now = Instant.now(clock)
        return store.inTransaction {
            closeIfStale(now)
            val open = store.openSession()?.id
            val sessionId = open
                ?: store.insertSession(
                    Session(id = 0, startedAt = now, endedAt = null, routineId = routineIdFor(choice.value), note = null),
                )
            val first = store.setCount(sessionId, machineId)
            val ids = sets.mapIndexed { index, draft ->
                store.insertSet(
                    WorkoutSet(
                        id = 0,
                        sessionId = sessionId,
                        machineId = machineId,
                        position = first + index,
                        weightKg = draft.weightKg,
                        reps = draft.reps,
                        seconds = null,
                        calories = null,
                        distanceM = null,
                        loggedAt = now.plusSeconds(index.toLong()),
                        type = SetType.WARMUP,
                    ),
                )
            }
            RepeatOutcome(sessionId, open == null, ids, 1)
        }
    }

    override suspend fun undoRepeat(outcome: RepeatOutcome) {
        store.inTransaction {
            outcome.setIds.forEach { store.deleteSet(it) }
            if (outcome.sessionCreated && store.sessionSets(outcome.sessionId).isEmpty()) {
                store.deleteSession(outcome.sessionId)
}
        }
    }

    override suspend fun closeStaleSession() {
        store.inTransaction { closeIfStale(Instant.now(clock)) }
    }

    override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet {
        val now = Instant.now(clock)
        return store.inTransaction {
            closeIfStale(now)
            val sessionId = store.openSession()?.id
                ?: store.insertSession(
                    Session(id = 0, startedAt = now, endedAt = null, routineId = routineIdFor(choice.value), note = null),
                )
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
                loggedAt = now,
                type = draft.type,
            )
            // Earlier sets of this machine, this session's included; read before the insert.
            val record = RecordRules.recordFor(set, store.machineSets(machineId))
            LoggedSet(set.copy(id = store.insertSet(set)), record)
        }
    }

    override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet {
        val now = Instant.now(clock)
        return store.inTransaction {
            closeIfStale(now)
            val sessionId = store.openSession()?.id
                ?: store.insertSession(
                    Session(id = 0, startedAt = now, endedAt = null, routineId = routineIdFor(choice.value), note = null),
                )
            val set = CardioRules.toSet(
                id = 0,
                sessionId = sessionId,
                machineId = machineId,
                position = store.setCount(sessionId, machineId),
                draft = draft,
                loggedAt = now,
            )
            LoggedSet(set.copy(id = store.insertSet(set)), null)
        }
    }

    override suspend fun updateSet(set: WorkoutSet) {
        store.updateSet(set)
    }

    override suspend fun deleteSet(set: WorkoutSet) {
        store.deleteSet(set.id)
    }

    override suspend fun restoreSet(set: WorkoutSet) {
        store.insertSet(set)
    }

    override suspend fun finishSession(): SessionSummary? = store.inTransaction {
        val open = store.openSession() ?: return@inTransaction null
        store.setSessionEnd(open.id, Instant.now(clock))
        resetChoice()
        val sessionSets = store.sessionSets(open.id)
        val earlier = store.machinesSetsBefore(sessionSets.map { it.machineId }.distinct(), open.startedAt)
        store.summary(open.id).copy(recordCount = RecordRules.countRecords(sessionSets, earlier))
    }

    override suspend fun sessionRoutineId(sessionId: Long): Long? {
        val routineId = store.session(sessionId)?.routineId ?: return null
        return routineId.takeIf { id -> store.allRoutines().any { it.id == id } }
    }

    override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? {
        val routineId = store.session(sessionId)?.routineId ?: return null
        if (store.allRoutines().none { it.id == routineId }) return null
        val machineIds = store.machines().map { it.id }.toSet()
        val sets = store.sessionSets(sessionId).filter { it.machineId in machineIds }
        return RoutineUpdateRules.plan(store.routineItems(routineId), sets)
    }

    override suspend fun trainingSummary(sessionId: Long): TrainingSummary? {
        val session = store.session(sessionId) ?: return null
        val sets = store.sessionSets(sessionId)
        val routineId = session.routineId
        val routineName = routineId?.let { id -> store.allRoutines().firstOrNull { it.id == id }?.name }
        val earlier = store.machinesSetsBefore(sets.map { it.machineId }.distinct(), session.startedAt).groupBy { it.machineId }
        val previousSession = routineId?.let { id ->
            store.observeSessions().first()
                .filter { it.routineId == id && it.id != sessionId && it.endedAt != null && it.startedAt < session.startedAt }
                .maxByOrNull { it.startedAt }
        }
        return TrainingSummaryRules.build(
            session = session,
            routineName = routineName,
            sets = sets,
            machines = store.machines(),
            earlierSetsByMachine = earlier,
            previous = previousSession?.let { it to store.sessionSets(it.id) },
            zone = clock.zone,
        )
    }

    override suspend fun reopenSession(sessionId: Long) {
        store.inTransaction {
            if (!store.hasOtherOpenSession(sessionId)) store.setSessionEnd(sessionId, null)
        }
    }

    /** Closes the open session at its closing time when it is stale. Call inside a transaction. */
    private suspend fun closeIfStale(now: Instant) {
        val open = store.openSession() ?: return
        val lastSetAt = store.lastSetAt(open.id)
        if (SessionRules.isStale(open, lastSetAt, now)) {
            store.setSessionEnd(open.id, SessionRules.closeTime(open, lastSetAt))
            resetChoice()
        }
    }

    private companion object {
        /** A second past midnight, so the new day's date is surely the one read. */
        const val MIDNIGHT_MARGIN_MS = 1_000L
        const val RECHECK_MS = 60_000L
        const val MAX_UNCHANGED_WAKES = 2

        /** No session has this id, so "other than the open one" excludes nothing. */
        const val NO_SESSION = -1L

        /** Progression looks at the machine's two most recent other sessions. */
        const val HISTORY_SESSIONS = 2
    }
}
