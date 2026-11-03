package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.NextRoutineRules
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.ProgressionRules
import com.boratreino.core.model.RecordRules
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionRules
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.withoutMachines
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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

    private fun today() = LocalDate.now(clock).dayOfWeek

    override fun observeTodayPlan(): Flow<TodayPlan> =
        combine(
            routines.observePlan(),
            store.observeLastRoutineId(),
            store.observeOpenSession(),
            choice,
            skipped,
        ) { plan, lastRoutineId, open, choice, skippedIds ->
            val all = plan.routines.map { it.routine }
            val mode = plan.plan.scheduleMode
            val suggested = NextRoutineRules.next(mode, all, lastRoutineId, today())
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
                restDay = mode == ScheduleMode.WEEKDAYS && suggested == null && all.isNotEmpty(),
                skipped = skippedIds,
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
                NextRoutineRules.next(plan.scheduleMode, all, store.lastRoutineId(), today())?.id
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
                            suggestion = ProgressionRules.suggest(target, machine.stepKg, history),
                        )
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
            )
            // Earlier sets of this machine, this session's included; read before the insert.
            val record = RecordRules.recordFor(set, store.machineSets(machineId))
            LoggedSet(set.copy(id = store.insertSet(set)), record)
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
        /** No session has this id, so "other than the open one" excludes nothing. */
        const val NO_SESSION = -1L

        /** Progression looks at the machine's two most recent other sessions. */
        const val HISTORY_SESSIONS = 2
    }
}
