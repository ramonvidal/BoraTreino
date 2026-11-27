package com.boratreino.feature.calendar

import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.RemovedSets
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.MachineUsage
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.StarterMachine
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.testing.StubHistoryRepository
import com.boratreino.core.testing.StubMachineRepository
import com.boratreino.core.testing.StubTrainingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** The id of the session the new-mode tests create with its first set. */
internal const val NEW_SESSION_ID = 77L

/** One write that created a session with its first set: what the repository was asked for. */
internal data class FirstWrite(
    val date: LocalDate,
    val routineId: Long?,
    val isExtra: Boolean,
    val machineId: Long,
    val weightKg: Double?,
    val reps: Int?,
    val cardio: Boolean,
)

/**
 * The one session the editor works on, shared by the fakes below. It is null until a first set is written; a delete
 * that removes the last set removes the session too, and restoring brings it back with its id. [writes] lists every
 * repository write, in order.
 */
internal class NewSessionStore(private val machines: List<Machine>) {
    val detail = MutableStateFlow<SessionDetail?>(null)
    val writes = mutableListOf<String>()
    val firstWrites = mutableListOf<FirstWrite>()
    val lastTimeCalls = mutableListOf<Pair<LocalDate, Long>>()
    /** What [HistoryRepository.lastTimeBeforeDate] answers. */
    var lastTimeBeforeDate: List<WorkoutSet> = emptyList()
    private var nextSetId = 1000L

    private fun machine(id: Long): Machine = machines.first { it.id == id }

    fun newSetId(): Long = nextSetId++

    /** The session as a past day gets it: closed at 18:00 UTC. */
    private fun sessionOn(date: LocalDate, routineId: Long?, isExtra: Boolean): Session {
        val start = date.atTime(18, 0).atZone(ZoneOffset.UTC).toInstant()
        return Session(NEW_SESSION_ID, start, start, routineId, null, isExtra)
    }

    /** Creates the session with the set that [build] makes for it. */
    fun writeFirst(date: LocalDate, routineId: Long?, isExtra: Boolean, build: (sessionId: Long, loggedAt: Instant) -> WorkoutSet): WorkoutSet {
        val session = sessionOn(date, routineId, isExtra)
        val saved = build(NEW_SESSION_ID, session.startedAt)
        detail.value = SessionDetail(session, null, listOf(SessionMachine(machine(saved.machineId), listOf(saved))))
        return saved
    }

    /** Adds the set that [build] makes (its position is the machine's sets so far) to the open session. */
    fun append(machineId: Long, build: (position: Int) -> WorkoutSet): WorkoutSet {
        val current = requireNotNull(detail.value) { "no open session in the fake" }
        val sets = current.machines.firstOrNull { it.machine.id == machineId }?.sets.orEmpty()
        val saved = build(sets.size)
        val others = current.machines.filter { it.machine.id != machineId }
        detail.value = current.copy(machines = others + SessionMachine(machine(machineId), sets + saved))
        return saved
    }

    /** Deletes [set]; the session goes with it when no set is left. */
    fun removeSet(set: WorkoutSet): RemovedSets {
        val current = requireNotNull(detail.value) { "no open session in the fake" }
        val left = current.machines
            .map { entry -> entry.copy(sets = entry.sets.filterNot { it.id == set.id }) }
            .filter { it.sets.isNotEmpty() }
        return if (left.isEmpty()) {
            detail.value = null
            RemovedSets(listOf(set), current.session)
        } else {
            detail.value = current.copy(machines = left)
            RemovedSets(listOf(set), null)
        }
    }

    /** Puts back what [removed] holds: the whole session when it was removed with the sets. */
    fun restore(removed: RemovedSets) {
        val session = removed.session
        if (session != null) {
            detail.value = SessionDetail(
                session,
                null,
                removed.sets.groupBy { it.machineId }.map { (id, sets) -> SessionMachine(machine(id), sets) },
            )
        }
    }
}

internal class NewSessionHistory(private val store: NewSessionStore) : StubHistoryRepository() {
    override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
    override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = emptyFlow()
    override fun observeSession(sessionId: Long): Flow<SessionDetail?> = store.detail
    override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()

    override suspend fun addSessionWithSet(date: LocalDate, routineId: Long?, isExtra: Boolean, machineId: Long, draft: SetDraft): WorkoutSet {
        store.writes += "addSessionWithSet"
        store.firstWrites += FirstWrite(date, routineId, isExtra, machineId, draft.weightKg, draft.reps, cardio = false)
        return store.writeFirst(date, routineId, isExtra) { sessionId, loggedAt ->
            WorkoutSet(store.newSetId(), sessionId, machineId, 0, draft.weightKg, draft.reps, null, null, null, loggedAt)
        }
    }

    override suspend fun addSessionWithCardio(date: LocalDate, routineId: Long?, isExtra: Boolean, machineId: Long, draft: CardioDraft): WorkoutSet {
        store.writes += "addSessionWithCardio"
        store.firstWrites += FirstWrite(date, routineId, isExtra, machineId, null, null, cardio = true)
        return store.writeFirst(date, routineId, isExtra) { sessionId, loggedAt ->
            CardioRules.toSet(store.newSetId(), sessionId, machineId, 0, draft, loggedAt)
        }
    }

    override suspend fun addSessionRepeating(date: LocalDate, routineId: Long?, isExtra: Boolean, mode: RepeatMode): List<WorkoutSet> {
        store.writes += "addSessionRepeating"
        return emptyList()
    }

    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) {
        store.writes += "setSessionRoutine"
    }

    override suspend fun setSessionExtra(sessionId: Long, extra: Boolean) {
        store.writes += "setSessionExtra"
    }

    override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet {
        store.writes += "addSet"
        return store.append(machineId) { position ->
            WorkoutSet(store.newSetId(), sessionId, machineId, position, draft.weightKg, draft.reps, null, null, null, Instant.EPOCH)
        }
    }

    override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet {
        store.writes += "addCardioSet"
        return store.append(machineId) { position ->
            CardioRules.toSet(store.newSetId(), sessionId, machineId, position, draft, Instant.EPOCH)
        }
    }

    override suspend fun lastTimeBeforeDate(date: LocalDate, machineId: Long): List<WorkoutSet> {
        store.lastTimeCalls += date to machineId
        return store.lastTimeBeforeDate
    }

    override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
    override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
}

internal class NewSessionTraining(private val store: NewSessionStore) : StubTrainingRepository() {
    override suspend fun updateSet(set: WorkoutSet) {
        store.writes += "updateSet"
    }

    override suspend fun deleteSet(set: WorkoutSet): RemovedSets {
        store.writes += "deleteSet"
        return store.removeSet(set)
    }

    override suspend fun restoreSets(removed: RemovedSets) {
        store.writes += "restoreSets"
        store.restore(removed)
    }

    override fun observeOpenSession(): Flow<OpenSession?> = emptyFlow()
    override fun observeTodayPlan(): Flow<TodayPlan> = emptyFlow()
    override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = emptyFlow()
    override fun observeRepeatOffer(): Flow<RepeatOffer?> = emptyFlow()
}

internal class NewSessionMachines(private val all: List<Machine>) : StubMachineRepository(all) {
    override fun observeLibraryIdsInUse(): Flow<Set<String>> = emptyFlow()
    override fun observeRecentMachines(limit: Int): Flow<List<Machine>> = MutableStateFlow(all)
    override fun observeStartersNotAdded(): Flow<List<StarterMachine>> = emptyFlow()
}
