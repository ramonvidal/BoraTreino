package com.boratreino.feature.settings

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.data.BackupPreview
import com.boratreino.core.data.BackupRepository
import com.boratreino.core.health.HealthAvailability
import com.boratreino.core.health.HealthConnectAccess
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.notify.RestTimer
import com.boratreino.core.notify.RestTimerState
import com.boratreino.core.settings.HealthSettings
import com.boratreino.core.settings.TrainingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock

/** The test alarm button: it starts the shortest rest on the timer, and a view model without a timer takes it too. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelRestTest {
    private class FakeRestTimer : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState())
        override val finished: SharedFlow<Unit> = MutableSharedFlow()
        val starts = mutableListOf<Int>()
        override fun start(seconds: Int) {
            starts += seconds
        }
        override fun extend(seconds: Int) = Unit
        override fun skip() = Unit
    }

    private class FakeTrainingSettings : TrainingSettings {
        val rest = MutableStateFlow(RestTimerSettings(enabled = true))
        override val weeklyGoal: Flow<Int> = MutableStateFlow(4)
        override suspend fun setWeeklyGoal(goal: Int) = Unit
        override val reminders: Flow<ReminderSettings> = MutableStateFlow(ReminderSettings())
        override suspend fun setReminders(settings: ReminderSettings) = Unit
        override val warmupCalculator: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setWarmupCalculator(enabled: Boolean) = Unit
        override val restTimer: Flow<RestTimerSettings> get() = rest
        override suspend fun setRestTimer(settings: RestTimerSettings) {
            rest.value = settings
        }
    }

    private class FakeScheduler : ReminderScheduler {
        override fun reschedule() = Unit
    }

    private class FakeBackup : BackupRepository {
        override suspend fun export(): String = ""
        override fun suggestedFileName(): String = "boratreino-backup.json"
        override fun preview(text: String): BackupPreview = BackupPreview.Invalid("")
        override suspend fun import(data: BackupData) = Unit
    }

    private class FakeHealthSettings : HealthSettings {
        override val enabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override val syncedSessionIds: Flow<Set<Long>> = MutableStateFlow(emptySet())
        override suspend fun setSyncedSessionIds(ids: Set<Long>) = Unit
    }

    private class FakeHealthAccess : HealthConnectAccess {
        override suspend fun availability(): HealthAvailability = HealthAvailability.Ready
        override val permissions: Set<String> = emptySet()
        override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
            object : ActivityResultContract<Set<String>, Set<String>>() {
                override fun createIntent(context: Context, input: Set<String>): Intent = Intent()
                override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = emptySet()
            }
        override fun installIntent(): Intent = Intent()
        override suspend fun write(exports: List<HealthExport>) = Unit
        override suspend fun delete(sessionIds: Set<Long>) = Unit
    }

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun viewModel(timer: RestTimer): SettingsViewModel = SettingsViewModel(
        FakeTrainingSettings(),
        FakeBackup(),
        FakeScheduler(),
        FakeHealthSettings(),
        FakeHealthAccess(),
        FakeBodySettings(),
        context,
        Clock.systemUTC(),
        timer,
    )

    @Test
    fun `the test alarm starts a 15 second rest`() {
        val timer = FakeRestTimer()

        viewModel(timer).testRestAlarm()

        assertEquals(listOf(15), timer.starts)
    }

    @Test
    fun `each press of the test alarm starts its own 15 second rest`() {
        val timer = FakeRestTimer()
        val model = viewModel(timer)

        model.testRestAlarm()
        model.testRestAlarm()

        assertEquals(listOf(15, 15), timer.starts)
    }

    @Test
    fun `the test alarm with the default timer does not fail`() {
        val model = SettingsViewModel(
            FakeTrainingSettings(),
            FakeBackup(),
            FakeScheduler(),
            FakeHealthSettings(),
            FakeHealthAccess(),
            FakeBodySettings(),
            context,
            Clock.systemUTC(),
        )

        model.testRestAlarm()
    }
}
