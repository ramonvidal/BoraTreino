package com.boratreino.feature.calendar

import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionInfo
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.settings.TrainingSettings
import com.boratreino.core.testing.StubBodySettings
import com.boratreino.core.testing.StubHistoryRepository
import com.boratreino.core.testing.StubTrainingSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** Body settings backed by plain state flows; no profile and the estimate switched on. */
internal class FakeBodySettings : StubBodySettings() {
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

/** Only the weekly goal is used by the Calendar view model. */
internal class FakeTrainingSettings : StubTrainingSettings() {
    val goalFlow = MutableStateFlow(3)

    override val weeklyGoal: Flow<Int> = goalFlow
    override suspend fun setWeeklyGoal(goal: Int) {
        goalFlow.value = goal
    }

    override val reminders: Flow<ReminderSettings> = emptyFlow()
    override val restTimer: Flow<RestTimerSettings> = emptyFlow()
}

/** Sessions by date; every date has a value (an empty list when nothing is stored). */
internal class EnergyHistory : StubHistoryRepository() {
    val days = MutableStateFlow<Map<LocalDate, List<SessionDetail>>>(emptyMap())
    /** The days with a session (trained); empty unless a test sets them. */
    val trainingDates = MutableStateFlow<Set<LocalDate>>(emptySet())

    override fun observeDay(date: LocalDate): Flow<List<SessionDetail>> = days.map { it[date].orEmpty() }
    override fun observeTrainingDates(): Flow<Set<LocalDate>> = trainingDates
    override fun observeSession(sessionId: Long): Flow<SessionDetail?> = emptyFlow()
    override fun observeClosedSessionExports(sinceDays: Int): Flow<List<HealthExport>> = emptyFlow()
    override fun observeSessionIds(): Flow<Set<Long>> = emptyFlow()
}
