package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
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
}
