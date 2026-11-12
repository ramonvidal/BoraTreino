package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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

/** Goal 4, reminders on at 18:00 on weekdays, rest timer on at 90 s, a body filled in, Health Connect on. */
private fun pagesState(language: AppLanguage = AppLanguage.ENGLISH) = SettingsUiState(
    loaded = true,
    weeklyGoal = 4,
    reminders = ReminderSettings(
        enabled = true,
        weekdays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ),
        time = LocalTime.of(18, 0),
    ),
    restTimer = RestTimerSettings(enabled = true, defaultSeconds = 90),
    notificationsGranted = true,
    is24Hour = true,
    installedVersion = "0.40",
    language = language,
    health = HealthCardState.On,
    body = SettingsBodySamples.filled,
)

/**
 * The Settings list and each sub-page, in light, dark and large text; tall viewports so a whole page
 * fits. Saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class SettingsPagesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** [page] is the sub-page opened first; null is the list. */
    private fun shoot(
        name: String,
        state: SettingsUiState,
        page: SettingsPage?,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        SettingsContent(
                            state = state,
                            actions = SettingsActions(),
                            modifier = Modifier.fillMaxSize(),
                            initialPage = page,
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `settings list`() {
        shoot("settings-list", pagesState(), null)

        compose.onNodeWithText("Training").assertExists()
        compose.onNodeWithText("Goal: 4 days · Rest: 90 s").assertExists()
    }

    @Test
    fun `settings list dark`() = shoot("settings-list-dark", pagesState(), null, dark = true)

    @Test
    fun `settings list large text`() = shoot("settings-list-large", pagesState(), null, fontScale = 2f)

    @Test
    fun `training page`() = shoot("settings-page-training", pagesState(), SettingsPage.TRAINING)

    @Test
    fun `training page large text`() =
        shoot("settings-page-training-large", pagesState(), SettingsPage.TRAINING, fontScale = 2f)

    @Test
    fun `body page filled`() = shoot("settings-page-body", pagesState(), SettingsPage.BODY)

    @Test
    fun `body page empty`() =
        shoot("settings-page-body-empty", pagesState().copy(body = SettingsBodySamples.empty), SettingsPage.BODY)

    @Test
    fun `data page`() = shoot("settings-page-data", pagesState(), SettingsPage.DATA)

    @Test
    fun `data page without Health Connect`() =
        shoot("settings-page-data-no-health", pagesState().copy(health = HealthCardState.Hidden), SettingsPage.DATA)

    @Test
    fun `app page`() = shoot("settings-page-app", pagesState(), SettingsPage.APP)

    @Test
    fun `app page dark`() = shoot("settings-page-app-dark", pagesState(), SettingsPage.APP, dark = true)
}

/** The list and the Data page in Baianês, to check the translated rows fit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h2400dp")
class SettingsPagesPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, page: SettingsPage?) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = pagesState(AppLanguage.BAIANES),
                        actions = SettingsActions(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = page,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `settings list pt`() {
        shoot("settings-list-pt", null)

        compose.onNodeWithText("Ajustes").assertExists()
    }

    @Test
    fun `settings data page pt`() = shoot("settings-page-data-pt", SettingsPage.DATA)
}
