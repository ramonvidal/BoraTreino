package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.BackupSummary
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** The Settings screen from fixed states, light, dark, large text and each dialog; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    /** Goal 4, reminders on at 18:00 on weekdays, rest timer on at 90 s, notifications allowed. */
    private val main = SettingsUiState(
        loaded = true,
        weeklyGoal = 4,
        reminders = ReminderSettings(enabled = true, weekdays = weekdays, time = LocalTime.of(18, 0)),
        restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90),
        notificationsGranted = true,
        is24Hour = true,
        installedVersion = "0.40",
        updateStatus = UpdateStatus.UpToDate,
    )

    private fun shoot(name: String, state: SettingsUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        SettingsContent(state = state, actions = SettingsActions(), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `settings main`() {
        shoot("settings-main", main)
        compose.onNodeWithText("Weekly goal").assertExists()
    }

    @Test
    fun `settings dark`() = shoot("settings-dark", main, dark = true)

    @Test
    fun `settings large text`() = shoot("settings-large", main, fontScale = 2f)

    @Test
    fun `settings with reminders off`() = shoot(
        "settings-reminders-off",
        main.copy(reminders = main.reminders.copy(enabled = false)),
    )

    @Test
    fun `settings with notifications off`() = shoot(
        "settings-notifications-off",
        main.copy(notificationsGranted = false),
    )

    @Test
    fun `import confirm dialog`() {
        val summary = BackupSummary(
            machines = 6,
            routines = 3,
            sessions = 120,
            sets = 1_304,
            firstDay = LocalDate.of(2026, 9, 2),
            lastDay = LocalDate.of(2026, 11, 2),
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(state = main.copy(importConfirm = summary), actions = SettingsActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithText(
            "Replace everything on this phone with this backup? 6 machines, 3 routines, 120 trainings, 1,304 sets (2 Sep – 2 Nov 2026).",
        ).assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/settings-import-confirm.png")
    }

    @Test
    fun `settings with an update available`() = shoot(
        "settings-update-available",
        main.copy(updateStatus = UpdateStatus.Available("0.41")),
    )

    @Test
    fun `settings with the update page unreachable`() = shoot(
        "settings-update-unreachable",
        main.copy(updateStatus = UpdateStatus.Unreachable),
    )

    @Test
    fun `settings updates up to date`() = shoot("settings-updates", main)

    @Test
    fun `settings with Baianes chosen`() = shoot(
        "settings-language",
        main.copy(language = AppLanguage.BAIANES),
    )

    @Test
    fun `time picker dialog`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(state = main.copy(timePickerOpen = true), actions = SettingsActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/settings-time-picker.png")
    }
}
