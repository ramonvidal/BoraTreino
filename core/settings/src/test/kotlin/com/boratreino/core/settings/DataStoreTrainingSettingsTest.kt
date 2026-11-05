package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The weekly goal on a real preferences file: default, stored values and clamping to 1..7. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreTrainingSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()

    private fun newDataStore(): DataStore<Preferences> {
        val job = SupervisorJob()
        jobs += job
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
    }

    private fun newSettings(dataStore: DataStore<Preferences> = newDataStore()): TrainingSettings =
        DataStoreTrainingSettings(dataStore)

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `the weekly goal is 3 until it is set`() = runTest {
        assertEquals(3, newSettings().weeklyGoal.first())
    }

    @Test
    fun `a goal that was set is read back`() = runTest {
        val settings = newSettings()
        settings.setWeeklyGoal(5)

        assertEquals(5, settings.weeklyGoal.first())
    }

    @Test
    fun `every goal from 1 to 7 is stored as given`() = runTest {
        val settings = newSettings()
        for (goal in 1..7) {
            settings.setWeeklyGoal(goal)
            assertEquals(goal, settings.weeklyGoal.first())
        }
    }

    @Test
    fun `a goal below 1 is stored as 1`() = runTest {
        val settings = newSettings()
        settings.setWeeklyGoal(0)

        assertEquals(1, settings.weeklyGoal.first())
    }

    @Test
    fun `a goal above 7 is stored as 7`() = runTest {
        val settings = newSettings()
        settings.setWeeklyGoal(9)

        assertEquals(7, settings.weeklyGoal.first())
    }

    @Test
    fun `a stored value outside 1 to 7 is read back clamped`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[intPreferencesKey("weekly_goal")] = 12 }
        assertEquals(7, newSettings(dataStore).weeklyGoal.first())

        dataStore.edit { it[intPreferencesKey("weekly_goal")] = -4 }
        assertEquals(1, newSettings(dataStore).weeklyGoal.first())
    }

    @Test
    fun `the goal survives a new settings object on the same file`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).setWeeklyGoal(4)

        assertEquals(4, newSettings(dataStore).weeklyGoal.first())
    }

    // Training reminders

    @Test
    fun `reminders are off on Monday to Friday at 18 00 until they are set`() = runTest {
        val reminders = newSettings().reminders.first()

        assertEquals(ReminderSettings(), reminders)
        assertEquals(false, reminders.enabled)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), reminders.weekdays)
        assertEquals(LocalTime.of(18, 0), reminders.time)
    }

    @Test
    fun `reminder settings are read back as they were set`() = runTest {
        val settings = newSettings()
        val wanted = ReminderSettings(enabled = true, weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), time = LocalTime.of(7, 45))

        settings.setReminders(wanted)

        assertEquals(wanted, settings.reminders.first())
    }

    @Test
    fun `an empty set of weekdays is stored and read back as empty`() = runTest {
        val settings = newSettings()

        settings.setReminders(ReminderSettings(enabled = true, weekdays = emptySet(), time = LocalTime.of(18, 0)))

        assertEquals(emptySet<DayOfWeek>(), settings.reminders.first().weekdays)
    }

    @Test
    fun `the reminder time is stored as minutes of the day`() = runTest {
        val dataStore = newDataStore()

        newSettings(dataStore).setReminders(ReminderSettings(enabled = true, time = LocalTime.of(7, 45)))

        assertEquals(7 * 60 + 45, dataStore.data.first()[intPreferencesKey("reminder_minute_of_day")])
    }

    @Test
    fun `turning reminders off keeps the chosen days and time`() = runTest {
        val settings = newSettings()
        settings.setReminders(ReminderSettings(enabled = true, weekdays = setOf(DayOfWeek.TUESDAY), time = LocalTime.of(20, 30)))

        settings.setReminders(settings.reminders.first().copy(enabled = false))

        val reminders = settings.reminders.first()
        assertEquals(false, reminders.enabled)
        assertEquals(setOf(DayOfWeek.TUESDAY), reminders.weekdays)
        assertEquals(LocalTime.of(20, 30), reminders.time)
    }

    // Rest timer

    @Test
    fun `the rest timer is on with 90 seconds until it is set`() = runTest {
        assertEquals(RestTimerSettings(enabled = true, defaultSeconds = 90), newSettings().restTimer.first())
    }

    @Test
    fun `rest timer settings are read back as they were set`() = runTest {
        val settings = newSettings()
        val wanted = RestTimerSettings(enabled = false, defaultSeconds = 120)

        settings.setRestTimer(wanted)

        assertEquals(wanted, settings.restTimer.first())
    }

    @Test
    fun `a default below 15 seconds is stored as 15`() = runTest {
        val settings = newSettings()

        settings.setRestTimer(RestTimerSettings(enabled = true, defaultSeconds = 5))

        assertEquals(15, settings.restTimer.first().defaultSeconds)
    }

    @Test
    fun `a default above 600 seconds is stored as 600`() = runTest {
        val settings = newSettings()

        settings.setRestTimer(RestTimerSettings(enabled = true, defaultSeconds = 4_000))

        assertEquals(600, settings.restTimer.first().defaultSeconds)
    }

    @Test
    fun `reminders and rest timer do not change each other`() = runTest {
        val settings = newSettings()
        settings.setRestTimer(RestTimerSettings(enabled = false, defaultSeconds = 60))
        settings.setReminders(ReminderSettings(enabled = true))

        assertEquals(RestTimerSettings(enabled = false, defaultSeconds = 60), settings.restTimer.first())
        assertEquals(3, settings.weeklyGoal.first())
    }

    @Test
    fun `the rest beep is off until it is set`() = runTest {
        assertEquals(false, newSettings().restTimer.first().beep)
    }

    @Test
    fun `the rest beep is read back and the other fields stay`() = runTest {
        val settings = newSettings()
        settings.setRestTimer(RestTimerSettings(enabled = false, defaultSeconds = 120, beep = true))

        assertEquals(RestTimerSettings(enabled = false, defaultSeconds = 120, beep = true), settings.restTimer.first())

        settings.setRestTimer(settings.restTimer.first().copy(beep = false))

        assertEquals(RestTimerSettings(enabled = false, defaultSeconds = 120, beep = false), settings.restTimer.first())
    }

    @Test
    fun `the rest beep survives a new settings object on the same file`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).setRestTimer(RestTimerSettings(beep = true))

        assertEquals(true, newSettings(dataStore).restTimer.first().beep)
    }

    // Warm-up suggestions

    @Test
    fun `the warm-up calculator is on until it is set`() = runTest {
        assertEquals(true, newSettings().warmupCalculator.first())
    }

    @Test
    fun `the warm-up calculator can be switched off and on again`() = runTest {
        val settings = newSettings()

        settings.setWarmupCalculator(false)
        assertEquals(false, settings.warmupCalculator.first())

        settings.setWarmupCalculator(true)
        assertEquals(true, settings.warmupCalculator.first())
    }

    @Test
    fun `the warm-up calculator leaves the other settings alone`() = runTest {
        val settings = newSettings()
        settings.setWeeklyGoal(5)
        val timer = RestTimerSettings(enabled = false, defaultSeconds = 120, beep = true)
        settings.setRestTimer(timer)
        val reminders = ReminderSettings(enabled = true, weekdays = setOf(DayOfWeek.SUNDAY), time = LocalTime.of(7, 45))
        settings.setReminders(reminders)

        settings.setWarmupCalculator(false)

        assertEquals(5, settings.weeklyGoal.first())
        assertEquals(timer, settings.restTimer.first())
        assertEquals(reminders, settings.reminders.first())
    }

    @Test
    fun `other settings leave the warm-up calculator alone`() = runTest {
        val settings = newSettings()
        settings.setWarmupCalculator(false)

        settings.setWeeklyGoal(6)
        settings.setRestTimer(RestTimerSettings(beep = true))

        assertEquals(false, settings.warmupCalculator.first())
    }

    @Test
    fun `the warm-up calculator survives a new settings object on the same file`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).setWarmupCalculator(false)

        assertEquals(false, newSettings(dataStore).warmupCalculator.first())
    }
}
