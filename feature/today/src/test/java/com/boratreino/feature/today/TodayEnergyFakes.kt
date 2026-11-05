package com.boratreino.feature.today

import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.settings.BodySettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Body settings backed by plain state flows; no profile and the estimate switched on. */
internal class FakeBodySettings : BodySettings {
    val profileFlow = MutableStateFlow<BodyProfile?>(null)
    val weightsFlow = MutableStateFlow<List<WeightEntry>>(emptyList())
    val showFlow = MutableStateFlow(true)

    override val profile: Flow<BodyProfile?> = profileFlow
    override val weights: Flow<List<WeightEntry>> = weightsFlow
    override val showEnergyEstimate: Flow<Boolean> = showFlow

    override suspend fun setProfile(profile: BodyProfile) {
        profileFlow.value = profile
    }

    override suspend fun addWeight(date: LocalDate, kg: Double) {
        weightsFlow.value = (weightsFlow.value.filterNot { it.date == date } + WeightEntry(date, kg)).sortedByDescending { it.date }
    }

    override suspend fun setShowEnergyEstimate(show: Boolean) {
        showFlow.value = show
    }
}

/**
 * A history whose [day] flow always has a value (an empty flow would keep Today's combined state from
 * ever emitting); everything else is not used by the Today view model.
 */
internal class FakeHistory : HistoryRepository {
    val day = MutableStateFlow<List<SessionDetail>>(emptyList())
    val daysAsked = mutableListOf<LocalDate>()

    override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> {
        daysAsked += date
        return day
    }

    override fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<SessionInfo>>> = emptyFlow()
    override fun observeSession(sessionId: Long): Flow<SessionDetail?> = emptyFlow()
    override fun observeTrainingDates(): Flow<Set<LocalDate>> = emptyFlow()
    override suspend fun addSession(date: LocalDate, routineId: Long?): Long = 0L
    override suspend fun setSessionRoutine(sessionId: Long, routineId: Long?) = Unit
    override suspend fun addSet(sessionId: Long, machineId: Long, draft: SetDraft): WorkoutSet = error("not used")
    override suspend fun addCardioSet(sessionId: Long, machineId: Long, draft: CardioDraft): WorkoutSet = error("not used")
    override suspend fun lastTimeBefore(sessionId: Long, machineId: Long): List<WorkoutSet> = emptyList()
    override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
    override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
    override suspend fun deleteSession(sessionId: Long): SessionDetail? = null
    override suspend fun restoreSession(detail: SessionDetail) = Unit
    override suspend fun repeatLastSession(sessionId: Long, mode: RepeatMode): List<WorkoutSet> = emptyList()
}

/** 5 November 2026, 12:00 UTC. */
internal val TodayTestClock: Clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)
