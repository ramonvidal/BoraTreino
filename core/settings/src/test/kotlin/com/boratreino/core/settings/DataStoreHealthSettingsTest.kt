package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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

/** The Health Connect choices on a real preferences file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreHealthSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()

    private fun newDataStore(): DataStore<Preferences> {
        val job = SupervisorJob()
        jobs += job
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(tmp.root, "health.preferences_pb") },
        )
    }

    private fun newSettings(dataStore: DataStore<Preferences> = newDataStore()): HealthSettings =
        DataStoreHealthSettings(dataStore)

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `sending to Health Connect is off until it is turned on`() = runTest {
        assertEquals(false, newSettings().enabled.first())
    }

    @Test
    fun `no sessions are synced until some are stored`() = runTest {
        assertEquals(emptySet<Long>(), newSettings().syncedSessionIds.first())
    }

    @Test
    fun `turning it on and off is read back`() = runTest {
        val settings = newSettings()

        settings.setEnabled(true)
        assertEquals(true, settings.enabled.first())

        settings.setEnabled(false)
        assertEquals(false, settings.enabled.first())
    }

    @Test
    fun `synced session ids are read back as they were set`() = runTest {
        val settings = newSettings()

        settings.setSyncedSessionIds(setOf(3L, 1L, 42L, 1_234_567_890_123L))

        assertEquals(setOf(3L, 1L, 42L, 1_234_567_890_123L), settings.syncedSessionIds.first())
    }

    @Test
    fun `a single id round trips`() = runTest {
        val settings = newSettings()

        settings.setSyncedSessionIds(setOf(7L))

        assertEquals(setOf(7L), settings.syncedSessionIds.first())
    }

    @Test
    fun `an empty set round trips to empty after ids were stored`() = runTest {
        val settings = newSettings()
        settings.setSyncedSessionIds(setOf(1L, 2L))

        settings.setSyncedSessionIds(emptySet())

        assertEquals(emptySet<Long>(), settings.syncedSessionIds.first())
    }

    @Test
    fun `an empty set stored first is empty`() = runTest {
        val settings = newSettings()

        settings.setSyncedSessionIds(emptySet())

        assertEquals(emptySet<Long>(), settings.syncedSessionIds.first())
    }

    @Test
    fun `setting the ids replaces the earlier ones`() = runTest {
        val settings = newSettings()
        settings.setSyncedSessionIds(setOf(1L, 2L, 3L))

        settings.setSyncedSessionIds(setOf(3L, 4L))

        assertEquals(setOf(3L, 4L), settings.syncedSessionIds.first())
    }

    @Test
    fun `the two choices do not disturb each other`() = runTest {
        val settings = newSettings()

        settings.setEnabled(true)
        settings.setSyncedSessionIds(setOf(5L))
        settings.setEnabled(false)

        assertEquals(setOf(5L), settings.syncedSessionIds.first())
        assertEquals(false, settings.enabled.first())
    }

    @Test
    fun `the choices survive a new settings object on the same file`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).apply {
            setEnabled(true)
            setSyncedSessionIds(setOf(8L, 9L))
        }

        val again = newSettings(dataStore)

        assertEquals(true, again.enabled.first())
        assertEquals(setOf(8L, 9L), again.syncedSessionIds.first())
    }
}
