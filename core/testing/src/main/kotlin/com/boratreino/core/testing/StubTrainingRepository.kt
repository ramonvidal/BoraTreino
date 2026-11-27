package com.boratreino.core.testing

import com.boratreino.core.data.RemovedSets
import com.boratreino.core.data.RepeatOffer
import com.boratreino.core.data.RepeatOutcome
import com.boratreino.core.data.TrainingRepository
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.LoggedSet
import com.boratreino.core.model.MachineLog
import com.boratreino.core.model.OpenSession
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.model.RoutineUpdate
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SessionSummary
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.TodayPlan
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [TrainingRepository] for tests: no open session, [todayPlan] as the plan (no routines by default), nothing
 * to repeat, no pending summary; writes change nothing. Subclass it and override what a test needs.
 *
 * Defaults: [deleteSet] returns that set (session kept), [discardMachineToday] nothing removed, [logWarmups] an
 * empty outcome for one machine, the finish / offer / summary queries null. [logSet] and [logCardio] have no
 * neutral answer and throw [NotStubbedException].
 */
open class StubTrainingRepository(private val todayPlan: TodayPlan = NO_ROUTINE_PLAN) : TrainingRepository {
    override fun observeOpenSession(): Flow<OpenSession?> = flowOf(null)
    override fun observeTodayPlan(): Flow<TodayPlan> = flowOf(todayPlan)
    override suspend fun chooseRoutine(choice: RoutineChoice) = Unit
    override fun observeMachineLog(machineId: Long): Flow<MachineLog?> = flowOf(null)
    override suspend fun closeStaleSession() = Unit
    override suspend fun logSet(machineId: Long, draft: SetDraft): LoggedSet = notStubbed("logSet")
    override suspend fun logCardio(machineId: Long, draft: CardioDraft): LoggedSet = notStubbed("logCardio")
    override suspend fun updateSet(set: WorkoutSet) = Unit
    override suspend fun deleteSet(set: WorkoutSet): RemovedSets = RemovedSets(listOf(set), null)
    override suspend fun restoreSets(removed: RemovedSets) = Unit
    override suspend fun finishSession(): SessionSummary? = null
    override suspend fun reopenSession(sessionId: Long) = Unit
    override suspend fun finishIfRoutineComplete(): SessionSummary? = null
    override val pendingSummarySessionId: Flow<Long?> = MutableStateFlow(null)
    override suspend fun consumePendingSummary() = Unit
    override suspend fun discardMachineToday(machineId: Long): RemovedSets = RemovedSets(emptyList(), null)
    override suspend fun undoDiscardMachineToday(machineId: Long, removed: RemovedSets) = Unit
    override suspend fun skipMachineToday(machineId: Long) = Unit
    override suspend fun unskipMachineToday(machineId: Long) = Unit
    override fun observeRepeatOffer(): Flow<RepeatOffer?> = flowOf(null)
    override suspend fun repeatLastTraining(mode: RepeatMode): RepeatOutcome? = null
    override suspend fun logWarmups(machineId: Long, sets: List<SetDraft>): RepeatOutcome =
        RepeatOutcome(0L, false, emptyList(), 1)
    override suspend fun undoRepeat(outcome: RepeatOutcome) = Unit
    override suspend fun routineUpdateOffer(sessionId: Long): RoutineUpdate? = null
    override suspend fun sessionRoutineId(sessionId: Long): Long? = null
    override suspend fun trainingSummary(sessionId: Long): TrainingSummary? = null

    companion object {
        /** In turn, no routines, nothing suggested, Free, no active routine, not a rest day. */
        val NO_ROUTINE_PLAN: TodayPlan = TodayPlan(
            mode = ScheduleMode.IN_TURN,
            routines = emptyList(),
            suggested = null,
            choice = RoutineChoice.Free,
            active = null,
            restDay = false,
        )
    }
}
