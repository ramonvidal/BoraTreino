package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The Settings screen with Baianês chosen, to check the translated text fits; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SettingsPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `settings pt`() {
        val state = SettingsUiState(
            loaded = true,
            weeklyGoal = 4,
            reminders = ReminderSettings(
                enabled = true,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                time = LocalTime.of(18, 0),
            ),
            restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90),
            notificationsGranted = true,
            is24Hour = true,
            installedVersion = "0.40",
            updateStatus = UpdateStatus.Available("0.41"),
            language = AppLanguage.BAIANES,
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(state = state, actions = SettingsActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-pt.png")
    }
}
