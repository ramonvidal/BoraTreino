package com.boratreino.feature.settings

import com.boratreino.core.data.BackupPreview
import com.boratreino.core.data.BackupRepository
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.settings.TrainingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime

class SettingsViewModelTest {
    private class FakeSettings : TrainingSettings {
        val goal = MutableStateFlow(4)
        val reminder = MutableStateFlow(ReminderSettings(enabled = true, weekdays = setOf(DayOfWeek.MONDAY)))
        val rest = MutableStateFlow(RestTimerSettings())

        override val weeklyGoal: Flow<Int> get() = goal
        override suspend fun setWeeklyGoal(goal: Int) {
            this.goal.value = goal.coerceIn(1, 7)
        }
        override val reminders: Flow<ReminderSettings> get() = reminder
        override suspend fun setReminders(settings: ReminderSettings) {
            reminder.value = settings
        }
        override val restTimer: Flow<RestTimerSettings> get() = rest
        override suspend fun setRestTimer(settings: RestTimerSettings) {
            rest.value = settings
        }
    }

    private class FakeScheduler : ReminderScheduler {
        var calls = 0
        override fun reschedule() {
            calls++
        }
    }

    private class FakeBackup : BackupRepository {
        override suspend fun export(): String = ""
        override fun suggestedFileName(): String = "boratreino-backup-2026-11-02.json"
        override fun preview(text: String): BackupPreview = BackupPreview.Invalid("")
        override suspend fun import(data: BackupData) = Unit
    }

    private lateinit var settings: FakeSettings
    private lateinit var scheduler: FakeScheduler
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = FakeSettings()
        scheduler = FakeScheduler()
        viewModel = SettingsViewModel(settings, FakeBackup(), scheduler, Clock.systemUTC())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The state once the stored settings have been read. */
    private suspend fun loaded(): SettingsUiState = viewModel.state.first { it.loaded }

    @Test
    fun `the screen is not loaded until the settings have been read`() {
        assertFalse(viewModel.state.value.loaded)
    }

    @Test
    fun `the stored goal and reminders are shown`() = runTest {
        val state = loaded()

        assertEquals(4, state.weeklyGoal)
        assertEquals(settings.reminder.value, state.reminders)
    }

    // Weekly goal

    @Test
    fun `stepping the goal up and down moves it by one`() = runTest {
        loaded()

        viewModel.stepGoal(up = true)
        assertEquals(5, settings.goal.value)

        viewModel.stepGoal(up = false)
        viewModel.stepGoal(up = false)
        assertEquals(3, settings.goal.value)
    }

    @Test
    fun `the goal stays within 1 to 7 when stepped past the ends`() = runTest {
        loaded()
        settings.goal.value = 7
        viewModel.stepGoal(up = true)
        assertEquals(7, settings.goal.value)

        settings.goal.value = 1
        viewModel.stepGoal(up = false)
        assertEquals(1, settings.goal.value)
    }

    // Reminders

    @Test
    fun `turning off the last selected weekday does nothing`() = runTest {
        loaded()

        viewModel.toggleWeekday(DayOfWeek.MONDAY)

        assertEquals(setOf(DayOfWeek.MONDAY), settings.reminder.value.weekdays)
    }

    @Test
    fun `a weekday is added or removed and the reminder is rescheduled`() = runTest {
        loaded()

        viewModel.toggleWeekday(DayOfWeek.SATURDAY)
        assertTrue(DayOfWeek.SATURDAY in settings.reminder.value.weekdays)
        assertEquals(1, scheduler.calls)

        viewModel.toggleWeekday(DayOfWeek.SATURDAY)
        assertFalse(DayOfWeek.SATURDAY in settings.reminder.value.weekdays)
        assertEquals(2, scheduler.calls)
    }

    @Test
    fun `switching reminders on or off reschedules`() = runTest {
        loaded()

        viewModel.setRemindersEnabled(false)

        assertFalse(settings.reminder.value.enabled)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `choosing a reminder time closes the picker, stores the time and reschedules`() = runTest {
        loaded()
        viewModel.openTimePicker()
        assertTrue(loaded().timePickerOpen)

        viewModel.setReminderTime(LocalTime.of(7, 30))

        assertEquals(LocalTime.of(7, 30), settings.reminder.value.time)
        assertFalse(loaded().timePickerOpen)
        assertEquals(1, scheduler.calls)
    }

    // Rest timer

    @Test
    fun `the rest default is kept within 15 to 600 seconds and does not reschedule reminders`() = runTest {
        loaded()

        viewModel.setRestSeconds(5)
        assertEquals(15, settings.rest.value.defaultSeconds)

        viewModel.setRestSeconds(9_000)
        assertEquals(600, settings.rest.value.defaultSeconds)

        assertEquals(0, scheduler.calls)
    }

    @Test
    fun `switching the rest timer on or off keeps its default`() = runTest {
        loaded()
        viewModel.setRestSeconds(120)

        viewModel.setRestEnabled(false)

        assertFalse(settings.rest.value.enabled)
        assertEquals(120, settings.rest.value.defaultSeconds)
    }

    // Notifications section

    @Test
    fun `the notifications section shows only when something needs them and they are missing`() {
        val off = RestTimerSettings(enabled = false)
        val noReminders = ReminderSettings(enabled = false)
        val reminders = ReminderSettings(enabled = true)

        assertTrue(SettingsUiState(reminders = reminders, restTimer = off, notificationsGranted = false).showNotificationsSection)
        assertTrue(SettingsUiState(reminders = noReminders, restTimer = RestTimerSettings(enabled = true), notificationsGranted = false).showNotificationsSection)
        assertFalse(SettingsUiState(reminders = reminders, restTimer = off, notificationsGranted = true).showNotificationsSection)
        assertFalse(SettingsUiState(reminders = noReminders, restTimer = off, notificationsGranted = false).showNotificationsSection)
    }

    @Test
    fun `the permission state from the screen reaches the state`() = runTest {
        loaded()

        viewModel.setNotificationsGranted(false)

        assertFalse(loaded().notificationsGranted)
        assertTrue(loaded().showNotificationsSection)
    }
}
