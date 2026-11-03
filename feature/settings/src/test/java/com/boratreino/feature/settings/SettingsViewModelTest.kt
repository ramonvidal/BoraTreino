package com.boratreino.feature.settings

import com.boratreino.core.data.BackupPreview
import com.boratreino.core.data.BackupRepository
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.health.HealthAvailability
import com.boratreino.core.health.HealthConnectAccess
import com.boratreino.core.model.HealthExport
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.settings.HealthSettings
import com.boratreino.core.settings.TrainingSettings
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
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

    private class FakeHealthSettings : HealthSettings {
        val on = MutableStateFlow(false)
        val synced = MutableStateFlow<Set<Long>>(emptySet())
        override val enabled: Flow<Boolean> get() = on
        override suspend fun setEnabled(enabled: Boolean) {
            on.value = enabled
        }
        override val syncedSessionIds: Flow<Set<Long>> get() = synced
        override suspend fun setSyncedSessionIds(ids: Set<Long>) {
            synced.value = ids
        }
    }

    private class FakeHealthAccess : HealthConnectAccess {
        var current: HealthAvailability = HealthAvailability.Ready
        var failure: Exception? = null
        override suspend fun availability(): HealthAvailability {
            failure?.let { throw it }
            return current
        }
        override val permissions: Set<String> = setOf("write.exercise", "write.calories", "write.distance")
        override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
            object : ActivityResultContract<Set<String>, Set<String>>() {
                override fun createIntent(context: Context, input: Set<String>): Intent = Intent()
                override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = emptySet()
            }
        override fun installIntent(): Intent = Intent()
        override suspend fun write(exports: List<HealthExport>) = Unit
        override suspend fun delete(sessionIds: Set<Long>) = Unit
    }

    private lateinit var health: FakeHealthSettings
    private lateinit var access: FakeHealthAccess
    private lateinit var settings: FakeSettings
    private lateinit var scheduler: FakeScheduler
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = FakeSettings()
        scheduler = FakeScheduler()
        health = FakeHealthSettings()
        access = FakeHealthAccess()
        context = RuntimeEnvironment.getApplication()
        viewModel = SettingsViewModel(settings, FakeBackup(), scheduler, health, access, context, Clock.systemUTC())
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

    @Test
    fun `the rest beep is off by default`() = runTest {
        assertFalse(loaded().restTimer.beep)
    }

    @Test
    fun `turning the beep on keeps the other rest settings and does not reschedule reminders`() = runTest {
        loaded()
        viewModel.setRestSeconds(120)
        viewModel.setRestEnabled(false)

        viewModel.setRestBeep(true)

        assertTrue(settings.rest.value.beep)
        assertEquals(120, settings.rest.value.defaultSeconds)
        assertFalse(settings.rest.value.enabled)
        assertTrue(loaded().restTimer.beep)
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun `turning the beep off again is stored`() = runTest {
        loaded()
        viewModel.setRestBeep(true)

        viewModel.setRestBeep(false)

        assertFalse(settings.rest.value.beep)
    }

    @Test
    fun `switching the rest timer or its default keeps the beep`() = runTest {
        loaded()
        viewModel.setRestBeep(true)

        viewModel.setRestEnabled(false)
        viewModel.setRestSeconds(60)

        assertTrue(settings.rest.value.beep)
    }

    // About and language

    @Test
    fun `the installed version is the one the package manager reports`() = runTest {
        val expected = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

        assertEquals(expected, loaded().installedVersion)
    }

    @Test
    fun `choosing a language changes the language in the state`() = runTest {
        loaded()

        viewModel.chooseLanguage(AppLanguage.BAIANES)
        assertEquals(AppLanguage.BAIANES, loaded().language)

        viewModel.chooseLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, loaded().language)
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

    // Health Connect

    @Test
    fun `the health card is hidden until Health Connect has been asked`() = runTest {
        assertEquals(HealthCardState.Hidden, loaded().health)
    }

    @Test
    fun `an unsupported phone keeps the card hidden`() = runTest {
        access.current = HealthAvailability.Unsupported
        viewModel.refreshHealth()

        assertEquals(HealthCardState.Hidden, loaded().health)
    }

    @Test
    fun `a failing availability check hides the card instead of crashing`() = runTest {
        access.failure = IllegalStateException("boom")
        viewModel.refreshHealth()

        assertEquals(HealthCardState.Hidden, loaded().health)
    }

    @Test
    fun `refreshing reads the availability again`() = runTest {
        access.current = HealthAvailability.NotInstalled
        viewModel.refreshHealth()
        assertEquals(HealthCardState.NeedsInstall, loaded().health)

        access.current = HealthAvailability.Ready
        viewModel.refreshHealth()
        assertEquals(HealthCardState.Off, loaded().health)
    }

    @Test
    fun `all permissions granted turns sync on`() = runTest {
        viewModel.refreshHealth()

        viewModel.onHealthPermissionResult(access.permissions)

        assertTrue(health.on.value)
        assertEquals(HealthCardState.On, loaded().health)
    }

    @Test
    fun `more than the asked permissions is still all granted`() = runTest {
        viewModel.refreshHealth()

        viewModel.onHealthPermissionResult(access.permissions + "something.else")

        assertTrue(health.on.value)
    }

    @Test
    fun `a partial grant keeps sync off and shows permission denied`() = runTest {
        viewModel.refreshHealth()

        viewModel.onHealthPermissionResult(setOf("write.exercise"))

        assertFalse(health.on.value)
        assertEquals(HealthCardState.PermissionDenied, loaded().health)
    }

    @Test
    fun `an empty grant keeps sync off and shows permission denied`() = runTest {
        viewModel.refreshHealth()

        viewModel.onHealthPermissionResult(emptySet())

        assertFalse(health.on.value)
        assertEquals(HealthCardState.PermissionDenied, loaded().health)
    }

    @Test
    fun `a later full grant clears the denied message and turns sync on`() = runTest {
        viewModel.refreshHealth()
        viewModel.onHealthPermissionResult(emptySet())

        viewModel.onHealthPermissionResult(access.permissions)

        assertTrue(health.on.value)
        assertEquals(HealthCardState.On, loaded().health)
    }

    @Test
    fun `turning health off stores off and keeps the synced ids`() = runTest {
        viewModel.refreshHealth()
        health.on.value = true
        health.synced.value = setOf(1L, 2L)

        viewModel.turnHealthOff()

        assertFalse(health.on.value)
        assertEquals(setOf(1L, 2L), health.synced.value)
        assertEquals(HealthCardState.Off, loaded().health)
    }

    @Test
    fun `turning health off clears the denied message`() = runTest {
        viewModel.refreshHealth()
        viewModel.onHealthPermissionResult(emptySet())
        assertEquals(HealthCardState.PermissionDenied, loaded().health)

        viewModel.turnHealthOff()

        assertEquals(HealthCardState.Off, loaded().health)
    }

    @Test
    fun `the view model hands the screen the access permissions`() {
        assertEquals(access.permissions, viewModel.healthPermissions)
    }

    // healthCardState: every combination

    @Test
    fun `healthCardState for every availability, enabled and denied`() {
        val states = listOf(
            // availability, enabled, denied, expected
            listOf(HealthAvailability.Unsupported, false, false, HealthCardState.Hidden),
            listOf(HealthAvailability.Unsupported, false, true, HealthCardState.Hidden),
            listOf(HealthAvailability.Unsupported, true, false, HealthCardState.Hidden),
            listOf(HealthAvailability.Unsupported, true, true, HealthCardState.Hidden),
            listOf(HealthAvailability.NotInstalled, false, false, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NotInstalled, false, true, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NotInstalled, true, false, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NotInstalled, true, true, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NeedsUpdate, false, false, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NeedsUpdate, false, true, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NeedsUpdate, true, false, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NeedsUpdate, true, true, HealthCardState.NeedsInstall),
            listOf(HealthAvailability.NeedsPermission, false, false, HealthCardState.Off),
            listOf(HealthAvailability.NeedsPermission, false, true, HealthCardState.PermissionDenied),
            // Permissions are missing, so sync cannot be on even if the stored choice says so.
            listOf(HealthAvailability.NeedsPermission, true, false, HealthCardState.Off),
            listOf(HealthAvailability.NeedsPermission, true, true, HealthCardState.PermissionDenied),
            listOf(HealthAvailability.Ready, false, false, HealthCardState.Off),
            listOf(HealthAvailability.Ready, false, true, HealthCardState.PermissionDenied),
            listOf(HealthAvailability.Ready, true, false, HealthCardState.On),
            listOf(HealthAvailability.Ready, true, true, HealthCardState.On),
        )
        for ((availability, enabled, denied, expected) in states) {
            assertEquals(
                "$availability enabled=$enabled denied=$denied",
                expected,
                healthCardState(availability as HealthAvailability, enabled as Boolean, denied as Boolean),
            )
        }
    }
}
