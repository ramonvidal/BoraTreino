package com.boratreino.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.Sex
import com.boratreino.core.model.WeightEntry
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The body profile, weight entries and the estimate switch on a real preferences file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataStoreBodySettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()

    private fun newDataStore(): DataStore<Preferences> {
        val job = SupervisorJob()
        jobs += job
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(tmp.root, "body.preferences_pb") },
        )
    }

    private fun newSettings(dataStore: DataStore<Preferences> = newDataStore()): BodySettings =
        DataStoreBodySettings(dataStore)

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    private val profile = BodyProfile(Sex.FEMALE, birthYear = 1995, heightCm = 165, weightKg = 60.5)

    private val sexKey = stringPreferencesKey("body_sex")
    private val birthKey = intPreferencesKey("body_birth_year")
    private val heightKey = intPreferencesKey("body_height_cm")
    private val weightKey = doublePreferencesKey("body_weight_kg")

    @Test
    fun `there is no profile until it is set`() = runTest {
        assertNull(newSettings().profile.first())
    }

    @Test
    fun `a profile missing a field is not a profile`() = runTest {
        val dataStore = newDataStore()
        val settings = newSettings(dataStore)

        dataStore.edit { it[sexKey] = "MALE"; it[birthKey] = 1990; it[heightKey] = 180 }
        assertNull(settings.profile.first())

        dataStore.edit { it[weightKey] = 80.0 }
        assertEquals(BodyProfile(Sex.MALE, 1990, 180, 80.0), settings.profile.first())
    }

    @Test
    fun `a profile without sex is not a profile`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[birthKey] = 1990; it[heightKey] = 180; it[weightKey] = 80.0 }

        assertNull(newSettings(dataStore).profile.first())
    }

    @Test
    fun `a profile round trips with goal and manual basal rate`() = runTest {
        val settings = newSettings()
        val full = profile.copy(goalAdjustmentPercent = -15, manualBmrKcal = 1500)

        settings.setProfile(full)

        assertEquals(full, settings.profile.first())
    }

    @Test
    fun `the goal and basal rate default to none`() = runTest {
        val settings = newSettings()

        settings.setProfile(profile)

        assertEquals(0, settings.profile.first()?.goalAdjustmentPercent)
        assertNull(settings.profile.first()?.manualBmrKcal)
    }

    @Test
    fun `clearing the basal rate is read back`() = runTest {
        val settings = newSettings()
        settings.setProfile(profile.copy(manualBmrKcal = 1500))

        settings.setProfile(profile.copy(manualBmrKcal = null))

        assertNull(settings.profile.first()?.manualBmrKcal)
    }

    @Test
    fun `setting a profile again replaces it`() = runTest {
        val settings = newSettings()
        settings.setProfile(profile)

        val other = BodyProfile(Sex.MALE, 1980, 178, 85.0, goalAdjustmentPercent = 5)
        settings.setProfile(other)

        assertEquals(other, settings.profile.first())
    }

    @Test
    fun `a goal out of range is clamped`() = runTest {
        val settings = newSettings()

        settings.setProfile(profile.copy(goalAdjustmentPercent = 80))
        assertEquals(30, settings.profile.first()?.goalAdjustmentPercent)

        settings.setProfile(profile.copy(goalAdjustmentPercent = -80))
        assertEquals(-30, settings.profile.first()?.goalAdjustmentPercent)
    }

    @Test
    fun `there are no weights at first`() = runTest {
        assertEquals(emptyList<WeightEntry>(), newSettings().weights.first())
    }

    @Test
    fun `weights come newest first`() = runTest {
        val settings = newSettings()

        settings.addWeight(LocalDate.of(2026, 11, 3), 80.0)
        settings.addWeight(LocalDate.of(2026, 11, 5), 79.5)
        settings.addWeight(LocalDate.of(2026, 11, 1), 81.25)

        assertEquals(
            listOf(
                WeightEntry(LocalDate.of(2026, 11, 5), 79.5),
                WeightEntry(LocalDate.of(2026, 11, 3), 80.0),
                WeightEntry(LocalDate.of(2026, 11, 1), 81.25),
            ),
            settings.weights.first(),
        )
    }

    @Test
    fun `a weight on the same date replaces the earlier one`() = runTest {
        val settings = newSettings()
        val date = LocalDate.of(2026, 11, 5)

        settings.addWeight(date, 80.0)
        settings.addWeight(date, 79.0)

        assertEquals(listOf(WeightEntry(date, 79.0)), settings.weights.first())
    }

    @Test
    fun `at most 200 weights are kept and the oldest are dropped`() = runTest {
        val settings = newSettings()
        val first = LocalDate.of(2026, 1, 1)

        repeat(205) { i -> settings.addWeight(first.plusDays(i.toLong()), 70.0 + i) }

        val weights = settings.weights.first()
        assertEquals(200, weights.size)
        assertEquals(first.plusDays(204), weights.first().date)
        assertEquals(first.plusDays(5), weights.last().date)
    }

    @Test
    fun `adding a weight updates the profile weight`() = runTest {
        val settings = newSettings()
        settings.setProfile(profile)

        settings.addWeight(LocalDate.of(2026, 11, 5), 59.0)

        assertEquals(profile.copy(weightKg = 59.0), settings.profile.first())
    }

    @Test
    fun `adding a weight does not create a profile`() = runTest {
        val settings = newSettings()

        settings.addWeight(LocalDate.of(2026, 11, 5), 59.0)

        assertNull(settings.profile.first())
        assertEquals(1, settings.weights.first().size)
    }

    @Test
    fun `the energy estimate is shown by default`() = runTest {
        assertEquals(true, newSettings().showEnergyEstimate.first())
    }

    @Test
    fun `the energy switch is read back`() = runTest {
        val settings = newSettings()

        settings.setShowEnergyEstimate(false)
        assertEquals(false, settings.showEnergyEstimate.first())

        settings.setShowEnergyEstimate(true)
        assertEquals(true, settings.showEnergyEstimate.first())
    }

    @Test
    fun `the choices survive a new settings object on the same file`() = runTest {
        val dataStore = newDataStore()
        newSettings(dataStore).apply {
            setProfile(profile.copy(goalAdjustmentPercent = 10, manualBmrKcal = 1400))
            addWeight(LocalDate.of(2026, 11, 5), 60.0)
            setShowEnergyEstimate(false)
        }

        val again = newSettings(dataStore)

        assertEquals(profile.copy(weightKg = 60.0, goalAdjustmentPercent = 10, manualBmrKcal = 1400), again.profile.first())
        assertEquals(listOf(WeightEntry(LocalDate.of(2026, 11, 5), 60.0)), again.weights.first())
        assertEquals(false, again.showEnergyEstimate.first())
    }
}
