package com.boratreino.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.BackupSummary
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.Sex
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Settings list: four rows with their current values, and the sub-page each row opens with
 * only its own sections. Written from what a person sees and taps.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsListTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun state(
        goal: Int = 4,
        rest: RestTimerSettings = RestTimerSettings(enabled = true, defaultSeconds = 90),
        reminders: ReminderSettings = ReminderSettings(enabled = false, time = LocalTime.of(18, 0)),
        notificationsGranted: Boolean = true,
        body: BodyCardState = BodyCardState(),
        health: HealthCardState = HealthCardState.Hidden,
        importConfirm: BackupSummary? = null,
    ) = SettingsUiState(
        loaded = true,
        weeklyGoal = goal,
        reminders = reminders,
        restTimer = rest,
        notificationsGranted = notificationsGranted,
        is24Hour = true,
        installedVersion = "0.40",
        language = AppLanguage.ENGLISH,
        health = health,
        body = body,
        importConfirm = importConfirm,
    )

    private fun setContent(
        state: SettingsUiState,
        page: SettingsPage? = null,
        actions: SettingsActions = SettingsActions(),
        updatesCard: (@Composable () -> Unit)? = null,
    ) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = state,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                        updatesCard = updatesCard,
                        initialPage = page,
                    )
                }
            }
        }
    }

    /** The top edge of a visible title, in root coordinates. */
    private fun top(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    // The list

    @Test
    fun `the list shows the four rows in order`() {
        setContent(state())

        val tops = listOf("Training", "Your body", "Data", "App").map { top(it) }

        assertTrue("rows out of order: $tops", tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `the list shows no section cards until a row is opened`() {
        setContent(state())

        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
        compose.onNodeWithText("Backup").assertDoesNotExist()
        compose.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun `a default state shows the default goal, rest and an empty body`() {
        setContent(SettingsUiState(loaded = true))

        compose.onNodeWithText("Goal: 3 days · Rest: 90 s").assertExists()
        compose.onNodeWithText("Not set").assertExists()
        compose.onNodeWithText("Backup, Health Connect").assertExists()
    }

    @Test
    fun `the rows show the goal, the rest, the body and the app with its version`() {
        val body = BodyCardState(
            draft = BodyDraft(sex = Sex.FEMALE, birthYear = 1990, heightCm = 175, weightKg = 72.0),
        )
        setContent(state(goal = 4, body = body))

        compose.onNodeWithText("Goal: 4 days · Rest: 90 s").assertExists()
        compose.onNodeWithText("72 kg · 175 cm").assertExists()
        compose.onNodeWithText("Backup, Health Connect").assertExists()
        compose.onNodeWithText("English · version 0.40").assertExists()
    }

    @Test
    fun `a goal of one day is said in the singular`() {
        setContent(state(goal = 1))

        compose.onNodeWithText("Goal: 1 day · Rest: 90 s").assertExists()
    }

    @Test
    fun `the rest part says rest off when the rest timer is off`() {
        setContent(state(goal = 4, rest = RestTimerSettings(enabled = false)))

        compose.onNodeWithText("Goal: 4 days · Rest: off").assertExists()
    }

    @Test
    fun `the body row says not set while the weight is missing`() {
        val body = BodyCardState(draft = BodyDraft(heightCm = 175))
        setContent(state(body = body))

        compose.onNodeWithText("Not set").assertExists()
        compose.onNodeWithText("Goal: 4 days · Rest: 90 s").assertExists()
    }

    @Test
    fun `the body row keeps a decimal weight`() {
        val body = BodyCardState(draft = BodyDraft(heightCm = 175, weightKg = 72.5))
        setContent(state(body = body))

        compose.onNodeWithText("72.5 kg · 175 cm").assertExists()
    }

    // Opening the sub-pages

    @Test
    fun `the training row opens the training page and only its sections`() {
        setContent(state(reminders = ReminderSettings(enabled = true, time = LocalTime.of(18, 0))))

        compose.onNodeWithText("Training").performClick()

        compose.onNodeWithText("Weekly goal").assertExists()
        compose.onNodeWithText("Training reminders").assertExists()
        compose.onNodeWithText("Rest timer").assertExists()
        compose.onNodeWithText("Warm-up").assertExists()
        compose.onNodeWithText("Your body").assertDoesNotExist()
        compose.onNodeWithText("Backup").assertDoesNotExist()
        compose.onNodeWithText("Language").assertDoesNotExist()
    }

    @Test
    fun `the notifications section shows on the training page when the permission is missing`() {
        setContent(state(notificationsGranted = false))

        compose.onNodeWithText("Training").performClick()

        compose.onNodeWithText("Notifications").assertExists()
        compose.onNodeWithText("Allow").assertExists()
    }

    @Test
    fun `the notifications section is not shown when the permission is granted`() {
        setContent(state(notificationsGranted = true))

        compose.onNodeWithText("Training").performClick()

        compose.onNodeWithText("Notifications").assertDoesNotExist()
        compose.onNodeWithText("Allow").assertDoesNotExist()
    }

    @Test
    fun `the reminder time opens the time picker from the training page`() {
        var opened = false
        setContent(
            state(reminders = ReminderSettings(enabled = true, time = LocalTime.of(18, 0))),
            actions = SettingsActions(onOpenTimePicker = { opened = true }),
        )

        compose.onNodeWithText("Training").performClick()
        compose.onNodeWithText("Time").performClick()

        assertTrue(opened)
    }

    @Test
    fun `the your body row opens the body page only`() {
        setContent(state())

        compose.onNodeWithText("Your body").performClick()

        compose.onAllNodesWithText("Your body").assertCountEquals(2)
        compose.onNodeWithText("Sex").assertExists()
        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
        compose.onNodeWithText("Backup").assertDoesNotExist()
    }

    @Test
    fun `the data row opens backup and Health Connect only`() {
        setContent(state(health = HealthCardState.Off))

        compose.onNodeWithText("Data").performClick()

        compose.onNodeWithText("Backup").assertExists()
        compose.onNodeWithText("Export backup").assertExists()
        compose.onNodeWithText("Import backup").assertExists()
        compose.onNodeWithText("Health Connect").assertExists()
        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
        compose.onNodeWithText("Language").assertDoesNotExist()
    }

    @Test
    fun `the data page has no Health Connect section when the phone does not have it`() {
        setContent(state(health = HealthCardState.Hidden))

        compose.onNodeWithText("Data").performClick()

        compose.onNodeWithText("Backup").assertExists()
        compose.onNodeWithText("Health Connect").assertDoesNotExist()
        compose.onNodeWithText("Send trainings to Health Connect").assertDoesNotExist()
    }

    @Test
    fun `the app row opens the updates slot, the language and about only`() {
        setContent(state(), updatesCard = { Text("Updates card slot") })

        compose.onNodeWithText("App").performClick()

        compose.onNodeWithText("Updates card slot").assertExists()
        compose.onNodeWithText("Language").assertExists()
        compose.onNodeWithText("About").assertExists()
        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
        compose.onNodeWithText("Backup").assertDoesNotExist()
    }

    // Going back

    @Test
    fun `the back arrow returns from a page to the list`() {
        setContent(state())
        compose.onNodeWithText("Training").performClick()

        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("Your body").assertExists()
        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `the system back returns from a page to the list`() {
        setContent(state())
        compose.onNodeWithText("Data").performClick()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Your body").assertExists()
        compose.onNodeWithText("Backup").assertDoesNotExist()
    }

    @Test
    fun `a page opened from the start shows its back arrow`() {
        setContent(state(), page = SettingsPage.APP)

        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
    }

    // Dialogs from a page

    private val summary = BackupSummary(
        machines = 6,
        routines = 3,
        sessions = 120,
        sets = 1_304,
        firstDay = LocalDate.of(2026, 9, 2),
        lastDay = LocalDate.of(2026, 11, 2),
    )

    @Test
    fun `the import dialog opens from the data page with its counts`() {
        setContent(state(importConfirm = summary), page = SettingsPage.DATA)

        compose.onNodeWithText(
            "Replace everything on this phone with this backup? 6 exercises, 3 routines, 120 trainings, 1,304 sets (2 Sep – 2 Nov 2026).",
        ).assertExists()
    }

    @Test
    fun `replacing from the import dialog on the data page asks to confirm the import`() {
        var confirmed = false
        setContent(
            state(importConfirm = summary),
            page = SettingsPage.DATA,
            actions = SettingsActions(onConfirmImport = { confirmed = true }),
        )

        compose.onNodeWithText("Replace").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `cancelling the import dialog on the data page asks to dismiss it`() {
        var dismissed = false
        setContent(
            state(importConfirm = summary),
            page = SettingsPage.DATA,
            actions = SettingsActions(onDismissImport = { dismissed = true }),
        )

        compose.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `the import dialog keeps the page it was opened from`() {
        setContent(state(importConfirm = summary), page = SettingsPage.DATA)

        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithText("Weekly goal").assertDoesNotExist()
    }
}
