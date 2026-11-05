package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

private fun healthState(health: HealthCardState, language: AppLanguage = AppLanguage.ENGLISH) = SettingsUiState(
    loaded = true,
    weeklyGoal = 4,
    reminders = ReminderSettings(
        enabled = true,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        time = LocalTime.of(18, 0),
    ),
    restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90),
    notificationsGranted = true,
    is24Hour = true,
    installedVersion = "0.40",
    language = language,
    health = health,
)

/** The Health Connect card in each of its states, light and dark; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h3600dp")
class SettingsHealthScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, health: HealthCardState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(state = healthState(health), actions = SettingsActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `hidden shows no card`() {
        shoot("settings-health-hidden", HealthCardState.Hidden)
        compose.onNodeWithText("Health Connect").assertDoesNotExist()
        compose.onNodeWithText("Get Health Connect").assertDoesNotExist()
    }

    @Test
    fun `needs install shows the get button`() {
        shoot("settings-health-needs-install", HealthCardState.NeedsInstall)
        compose.onNodeWithText("Health Connect").assertExists()
        compose.onNodeWithText("Health Connect is not installed or needs an update.").assertExists()
        compose.onNodeWithText("Get Health Connect").assertExists()
    }

    @Test
    fun `off shows the switch without the denied message`() {
        shoot("settings-health-off", HealthCardState.Off)
        compose.onNodeWithText("Send trainings to Health Connect").assertExists()
        compose.onNodeWithText("Permission not granted").assertDoesNotExist()
        compose.onNodeWithText("Get Health Connect").assertDoesNotExist()
    }

    @Test
    fun `on shows the switch`() {
        shoot("settings-health-on", HealthCardState.On)
        compose.onNodeWithText("Send trainings to Health Connect").assertExists()
        compose.onNodeWithText("Permission not granted").assertDoesNotExist()
    }

    @Test
    fun `permission denied shows the message`() {
        shoot("settings-health-denied", HealthCardState.PermissionDenied)
        compose.onNodeWithText("Send trainings to Health Connect").assertExists()
        compose.onNodeWithText("Permission not granted").assertExists()
    }

    @Test
    fun `on in dark`() = shoot("settings-health-on-dark", HealthCardState.On, dark = true)

    @Test
    fun `permission denied in dark`() = shoot("settings-health-denied-dark", HealthCardState.PermissionDenied, dark = true)
}

/** The Health Connect card with Baianês chosen, to check the translated text fits. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h3600dp")
class SettingsHealthPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, health: HealthCardState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = healthState(health, AppLanguage.BAIANES),
                        actions = SettingsActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `hidden pt`() = shoot("settings-health-hidden-pt", HealthCardState.Hidden)

    @Test
    fun `needs install pt`() {
        shoot("settings-health-needs-install-pt", HealthCardState.NeedsInstall)
        compose.onNodeWithText("Instalar o Health Connect").assertExists()
    }

    @Test
    fun `off pt`() = shoot("settings-health-off-pt", HealthCardState.Off)

    @Test
    fun `on pt`() = shoot("settings-health-on-pt", HealthCardState.On)

    @Test
    fun `permission denied pt`() {
        shoot("settings-health-denied-pt", HealthCardState.PermissionDenied)
        compose.onNodeWithText("Permissão não concedida").assertExists()
    }
}
