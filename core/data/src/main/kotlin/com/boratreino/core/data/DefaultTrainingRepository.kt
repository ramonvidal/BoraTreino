package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionRules
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

internal class DefaultTrainingRepository @Inject constructor(
    private val store: WorkoutStore,
    private val clock: Clock,
) : TrainingRepository {

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
        ) { machine, openId -> machine to openId }
            .flatMapLatest { (machine, openId) ->
                if (machine == null) {
                    flowOf(null)
                } else {
                    val thisSession = if (openId == null) {
                        flowOf(emptyList())
                    } else {
                        store.observeMachineSets(openId, machineId)
                    }
                    combine(
                        store.observeLastTimeSets(machineId, openId ?: NO_SESSION),
                        thisSession,
                    ) { lastTime, current ->
                        MachineLog(
                            machine = machine,
                            lastTime = lastTime,
                            // The day of the last visit: its latest set's time.
                            lastTimeAt = lastTime.maxOfOrNull { it.loggedAt },
                            thisSession = current,
                        )
                    }
                }
            }

    override suspend fun closeStaleSession() {
        store.inTransaction { closeIfStale(Instant.now(clock)) }
    }

    override suspend fun logSet(machineId: Long, draft: SetDraft): WorkoutSet {
        val now = Instant.now(clock)
        return store.inTransaction {
            closeIfStale(now)
            val sessionId = store.openSession()?.id
                ?: store.insertSession(Session(id = 0, startedAt = now, endedAt = null, routineId = null, note = null))
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
            set.copy(id = store.insertSet(set))
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
        store.summary(open.id)
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
        }
    }

    private companion object {
        /** No session has this id, so "other than the open one" excludes nothing. */
        const val NO_SESSION = -1L
    }
}
