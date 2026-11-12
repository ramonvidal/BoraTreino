package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

private fun tabState(beep: Boolean, language: AppLanguage = AppLanguage.ENGLISH) = SettingsUiState(
    loaded = true,
    weeklyGoal = 4,
    reminders = ReminderSettings(
        enabled = true,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        time = LocalTime.of(18, 0),
    ),
    restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90, beep = beep),
    notificationsGranted = true,
    is24Hour = true,
    installedVersion = "0.40",
    language = language,
)

/**
 * Settings as a bottom-bar tab: a title and no back arrow on the list. The rest section and its beep
 * switch are on the Training page, which the tab opens from its first row.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsTabScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(
        name: String,
        state: SettingsUiState,
        dark: Boolean = false,
        actions: SettingsActions = SettingsActions(),
        page: SettingsPage? = null,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = state,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                        initialPage = page,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `settings tab has a title and no back arrow`() {
        shoot("settings-tab", tabState(beep = false))

        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `settings tab shows the rest section with the beep switch on the training page`() {
        shoot("settings-tab-beep-off", tabState(beep = false), page = SettingsPage.TRAINING)

        compose.onNodeWithText("Rest timer").assertExists()
        compose.onNodeWithText("Beep when rest ends").assertExists()
    }

    @Test
    fun `settings tab with the beep on`() {
        shoot("settings-tab-beep-on", tabState(beep = true), page = SettingsPage.TRAINING)

        compose.onNodeWithText("Beep when rest ends").assertExists()
    }

    @Test
    fun `settings tab dark with the beep off`() =
        shoot("settings-tab-beep-off-dark", tabState(beep = false), dark = true, page = SettingsPage.TRAINING)

    @Test
    fun `settings tab dark with the beep on`() =
        shoot("settings-tab-beep-on-dark", tabState(beep = true), dark = true, page = SettingsPage.TRAINING)

    @Test
    fun `tapping the beep switch reports the new value`() {
        var received: Boolean? = null
        shoot(
            "settings-tab-beep-tap",
            tabState(beep = false),
            actions = SettingsActions(onRestBeep = { received = it }),
            page = SettingsPage.TRAINING,
        )

        compose.onNodeWithText("Beep when rest ends").performClick()

        org.junit.Assert.assertEquals(true, received)
    }

    @Test
    fun `settings tab with the rest timer off hides the beep switch`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = tabState(beep = true).copy(restTimer = RestTimerSettings(enabled = false, beep = true)),
                        actions = SettingsActions(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = SettingsPage.TRAINING,
                    )
                }
            }
        }

        compose.onNodeWithText("Beep when rest ends").assertDoesNotExist()
    }
}

/** The same tab with Baianês chosen, to check the translated beep row fits. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SettingsTabPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, beep: Boolean, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = tabState(beep = beep, language = AppLanguage.BAIANES),
                        actions = SettingsActions(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = SettingsPage.TRAINING,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `settings tab pt beep off`() = shoot("settings-tab-pt-beep-off", beep = false)

    @Test
    fun `settings tab pt beep on`() = shoot("settings-tab-pt-beep-on", beep = true)

    @Test
    fun `settings tab pt dark`() = shoot("settings-tab-pt-dark", beep = true, dark = true)
}
