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
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.Sex
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.settings.HealthSettings
import com.boratreino.core.settings.TrainingSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The "Your body" card's view model side: drafts, number pads, saving the profile and logging the weight. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelBodyTest {
    private class QuietSettings : TrainingSettings {
        override val weeklyGoal: Flow<Int> = MutableStateFlow(3)
        override suspend fun setWeeklyGoal(goal: Int) = Unit
        override val reminders: Flow<ReminderSettings> = MutableStateFlow(ReminderSettings())
        override suspend fun setReminders(settings: ReminderSettings) = Unit
        override val restTimer: Flow<RestTimerSettings> = MutableStateFlow(RestTimerSettings())
        override suspend fun setRestTimer(settings: RestTimerSettings) = Unit
        override val warmupCalculator: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setWarmupCalculator(enabled: Boolean) = Unit
    }

    private class QuietScheduler : ReminderScheduler {
        override fun reschedule() = Unit
    }

    private class QuietBackup : BackupRepository {
        override suspend fun export(): String = ""
        override fun suggestedFileName(): String = "backup.json"
        override fun preview(text: String): BackupPreview = BackupPreview.Invalid("")
        override suspend fun import(data: BackupData) = Unit
    }

    private class QuietHealth : HealthSettings {
        override val enabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override val syncedSessionIds: Flow<Set<Long>> = MutableStateFlow(emptySet())
        override suspend fun setSyncedSessionIds(ids: Set<Long>) = Unit
    }

    private class QuietAccess : HealthConnectAccess {
        override suspend fun availability(): HealthAvailability = HealthAvailability.Unsupported
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

    private val today = LocalDate.of(2026, 11, 5)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    private lateinit var fake: FakeBodySettings
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeBodySettings()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        viewModel = SettingsViewModel(
            QuietSettings(), QuietBackup(), QuietScheduler(), QuietHealth(), QuietAccess(),
            fake, RuntimeEnvironment.getApplication(), clock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    private val body: BodyCardState get() = viewModel.state.value.body

    private fun type(field: BodyField, text: String) {
        viewModel.openBodyField(field)
        viewModel.confirmBodyValue(text)
    }

    /** A complete male profile: 1990, 180 cm, 80 kg. */
    private fun fillMale() {
        viewModel.setBodySex(Sex.MALE)
        type(BodyField.BIRTH_YEAR, "1990")
        type(BodyField.HEIGHT, "180")
        type(BodyField.WEIGHT, "80")
    }

    private val male = BodyProfile(Sex.MALE, 1990, 180, 80.0)

    // The card before and after anything is typed

    @Test
    fun `the card starts empty and shows no estimate`() = runTest {
        start()

        assertEquals(BodyDraft(), body.draft)
        assertNull(body.basalKcal)
        assertNull(body.restDayKcal)
        assertNull(body.dialog)
        assertTrue(body.showEstimate)
        assertEquals(2016, body.maxBirthYear)
    }

    @Test
    fun `a saved profile fills the card`() = runTest {
        fake.profileFlow.value = male.copy(goalAdjustmentPercent = -10, manualBmrKcal = 1600)
        start()

        assertEquals(BodyDraft(Sex.MALE, 1990, 180, 80.0, -10, 1600), body.draft)
        assertEquals(1600, body.basalKcal)
        assertEquals(1730, body.restDayKcal) // 1600 * 1.2 * 0.9 = 1728
    }

    // Saving only when complete

    @Test
    fun `choosing a sex alone saves nothing`() = runTest {
        start()

        viewModel.setBodySex(Sex.FEMALE)

        assertEquals(Sex.FEMALE, body.draft.sex)
        assertTrue(fake.savedProfiles.isEmpty())
    }

    @Test
    fun `a profile is saved only when sex, birth year, height and weight are all set`() = runTest {
        start()

        viewModel.setBodySex(Sex.MALE)
        type(BodyField.BIRTH_YEAR, "1990")
        type(BodyField.HEIGHT, "180")
        assertTrue(fake.savedProfiles.isEmpty())
        assertNull(body.basalKcal)

        type(BodyField.WEIGHT, "80")

        assertEquals(listOf(male), fake.savedProfiles)
    }

    @Test
    fun `the order of typing does not matter`() = runTest {
        start()

        type(BodyField.WEIGHT, "60")
        type(BodyField.HEIGHT, "165")
        type(BodyField.BIRTH_YEAR, "1995")
        assertTrue(fake.savedProfiles.isEmpty())
        viewModel.setBodySex(Sex.FEMALE)

        assertEquals(listOf(BodyProfile(Sex.FEMALE, 1995, 165, 60.0)), fake.savedProfiles)
    }

    @Test
    fun `the estimate lines appear once the profile is complete`() = runTest {
        start()

        fillMale()

        // bmr 1750, rest day 1750 * 1.2 = 2100
        assertEquals(1750, body.basalKcal)
        assertEquals(2100, body.restDayKcal)
    }

    @Test
    fun `the estimate is rounded to ten kcal`() = runTest {
        start()
        viewModel.setBodySex(Sex.FEMALE)
        type(BodyField.BIRTH_YEAR, "1995")
        type(BodyField.HEIGHT, "165")
        type(BodyField.WEIGHT, "60")

        // bmr 1315.25, rest day 1578.3
        assertEquals(1320, body.basalKcal)
        assertEquals(1580, body.restDayKcal)
    }

    @Test
    fun `a decimal weight with a comma is accepted`() = runTest {
        start()

        type(BodyField.WEIGHT, "80,5")

        assertEquals(80.5, body.draft.weightKg!!, 1e-9)
    }

    @Test
    fun `changing the sex of a complete profile saves it again`() = runTest {
        start()
        fillMale()

        viewModel.setBodySex(Sex.FEMALE)

        assertEquals(male.copy(sex = Sex.FEMALE), fake.savedProfiles.last())
    }

    // Number pad

    @Test
    fun `opening a field opens its number pad and dismissing closes it`() = runTest {
        start()

        viewModel.openBodyField(BodyField.HEIGHT)
        assertEquals(BodyField.HEIGHT, body.dialog)
        assertFalse(body.dialogError)

        viewModel.dismissBodyField()
        assertNull(body.dialog)
    }

    @Test
    fun `a valid value closes the pad and fills the field`() = runTest {
        start()

        type(BodyField.BIRTH_YEAR, "1990")

        assertNull(body.dialog)
        assertEquals(1990, body.draft.birthYear)
    }

    @Test
    fun `a value out of range keeps the pad open with an error and leaves the field alone`() = runTest {
        start()
        type(BodyField.HEIGHT, "180")

        type(BodyField.HEIGHT, "300")

        assertEquals(BodyField.HEIGHT, body.dialog)
        assertTrue(body.dialogError)
        assertEquals(180, body.draft.heightCm)
    }

    @Test
    fun `a good value after an error clears it`() = runTest {
        start()
        type(BodyField.BIRTH_YEAR, "1800")
        assertTrue(body.dialogError)

        viewModel.confirmBodyValue("1985")

        assertNull(body.dialog)
        assertFalse(body.dialogError)
        assertEquals(1985, body.draft.birthYear)
    }

    @Test
    fun `dismissing after an error clears the error`() = runTest {
        start()
        type(BodyField.WEIGHT, "5")
        assertTrue(body.dialogError)

        viewModel.dismissBodyField()
        viewModel.openBodyField(BodyField.WEIGHT)

        assertFalse(body.dialogError)
    }

    @Test
    fun `an invalid value saves nothing`() = runTest {
        start()
        fillMale()
        val saved = fake.savedProfiles.size

        type(BodyField.WEIGHT, "1000")

        assertEquals(saved, fake.savedProfiles.size)
        assertEquals(80.0, body.draft.weightKg!!, 1e-9)
    }

    @Test
    fun `confirming with no pad open does nothing`() = runTest {
        start()

        viewModel.confirmBodyValue("1990")

        assertNull(body.draft.birthYear)
    }

    @Test
    fun `the birth year limit follows the clock`() = runTest {
        start()

        type(BodyField.BIRTH_YEAR, "2017")
        assertTrue(body.dialogError)

        viewModel.confirmBodyValue("2016")
        assertEquals(2016, body.draft.birthYear)
    }

    // Basal rate

    @Test
    fun `a typed basal rate replaces the formula`() = runTest {
        start()
        fillMale()

        type(BodyField.BASAL, "1500")

        assertEquals(1500, body.draft.manualBmrKcal)
        assertEquals(1500, fake.savedProfiles.last().manualBmrKcal)
        assertEquals(1500, body.basalKcal)
        assertEquals(1800, body.restDayKcal)
    }

    @Test
    fun `a basal rate of zero goes back to the formula`() = runTest {
        start()
        fillMale()
        type(BodyField.BASAL, "1500")

        type(BodyField.BASAL, "0")

        assertNull(body.draft.manualBmrKcal)
        assertNull(fake.savedProfiles.last().manualBmrKcal)
        assertEquals(1750, body.basalKcal)
        assertNull(body.dialog)
    }

    @Test
    fun `a basal rate out of range is refused`() = runTest {
        start()
        fillMale()

        type(BodyField.BASAL, "499")
        assertEquals(BodyField.BASAL, body.dialog)
        assertTrue(body.dialogError)

        viewModel.confirmBodyValue("5001")
        assertTrue(body.dialogError)
        assertNull(body.draft.manualBmrKcal)

        viewModel.confirmBodyValue("5000")
        assertEquals(5000, body.draft.manualBmrKcal)
    }

    // Goal

    @Test
    fun `the goal moves in steps of five`() = runTest {
        start()

        viewModel.stepBodyGoal(up = true)
        assertEquals(5, body.draft.goalPercent)
        viewModel.stepBodyGoal(up = true)
        assertEquals(10, body.draft.goalPercent)
        viewModel.stepBodyGoal(up = false)
        viewModel.stepBodyGoal(up = false)
        viewModel.stepBodyGoal(up = false)
        assertEquals(-5, body.draft.goalPercent)
    }

    @Test
    fun `the goal stops at plus and minus thirty`() = runTest {
        start()

        repeat(9) { viewModel.stepBodyGoal(up = true) }
        assertEquals(30, body.draft.goalPercent)

        repeat(14) { viewModel.stepBodyGoal(up = false) }
        assertEquals(-30, body.draft.goalPercent)
    }

    @Test
    fun `the goal is saved with a complete profile and changes the rest day figure`() = runTest {
        start()
        fillMale()

        viewModel.stepBodyGoal(up = true)
        viewModel.stepBodyGoal(up = true)

        assertEquals(10, fake.savedProfiles.last().goalAdjustmentPercent)
        assertEquals(2310, body.restDayKcal)
    }

    @Test
    fun `a goal under zero lowers the rest day figure`() = runTest {
        start()
        fillMale()

        repeat(4) { viewModel.stepBodyGoal(up = false) }

        assertEquals(-20, fake.savedProfiles.last().goalAdjustmentPercent)
        assertEquals(1680, body.restDayKcal)
    }

    @Test
    fun `the goal alone saves no profile`() = runTest {
        start()

        viewModel.stepBodyGoal(up = true)

        assertTrue(fake.savedProfiles.isEmpty())
    }

    // The switch

    @Test
    fun `the energy switch is written and shown`() = runTest {
        start()

        viewModel.setShowEnergyEstimate(false)

        assertFalse(fake.showFlow.value)
        assertFalse(body.showEstimate)

        viewModel.setShowEnergyEstimate(true)
        assertTrue(body.showEstimate)
    }

    // Weights

    @Test
    fun `logging the weight adds it for today`() = runTest {
        start()
        fillMale()

        type(BodyField.LOG_WEIGHT, "79.5")

        assertEquals(listOf(today to 79.5), fake.addedWeights)
        assertNull(body.dialog)
    }

    @Test
    fun `logging the weight updates a complete profile`() = runTest {
        start()
        fillMale()

        type(BodyField.LOG_WEIGHT, "79.5")

        assertEquals(79.5, fake.profileFlow.value!!.weightKg, 1e-9)
        assertEquals(79.5, body.draft.weightKg!!, 1e-9)
        assertEquals(WeightEntry(today, 79.5), body.weights.first())
    }

    @Test
    fun `logging the weight can complete the profile`() = runTest {
        start()
        viewModel.setBodySex(Sex.MALE)
        type(BodyField.BIRTH_YEAR, "1990")
        type(BodyField.HEIGHT, "180")
        assertNull(fake.profileFlow.value)

        type(BodyField.LOG_WEIGHT, "81")

        assertEquals(male.copy(weightKg = 81.0), fake.profileFlow.value)
    }

    @Test
    fun `logging the weight without a complete profile creates none`() = runTest {
        start()
        viewModel.setBodySex(Sex.MALE)

        type(BodyField.LOG_WEIGHT, "79.5")

        assertEquals(listOf(today to 79.5), fake.addedWeights)
        assertNull(fake.profileFlow.value)
        assertTrue(fake.savedProfiles.isEmpty())
    }

    @Test
    fun `a weight out of range is not logged`() = runTest {
        start()

        type(BodyField.LOG_WEIGHT, "29")

        assertTrue(fake.addedWeights.isEmpty())
        assertTrue(body.dialogError)
    }

    @Test
    fun `only the five latest weights are listed`() = runTest {
        fake.weightsFlow.value = (1..7).map { WeightEntry(LocalDate.of(2026, 10, 30).minusDays(it.toLong()), 80.0 + it) }
        start()

        assertEquals(5, body.weights.size)
        assertEquals(fake.weightsFlow.value.take(5), body.weights)
    }
}
