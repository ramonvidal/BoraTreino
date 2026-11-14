package com.boratreino.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The rest alarm check on the Training page: shown under the rest timer only when the timer is on, with
 * a test button, a battery settings button and their hints. Tall viewport so the whole card is composed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h4000dp")
class SettingsRestAlarmTest {
    @get:Rule
    val compose = createComposeRule()

    private fun state(restEnabled: Boolean) = SettingsUiState(
        loaded = true,
        restTimer = RestTimerSettings(enabled = restEnabled, defaultSeconds = 90),
        notificationsGranted = true,
        is24Hour = true,
        installedVersion = "0.40",
        language = AppLanguage.ENGLISH,
    )

    private fun setContent(state: SettingsUiState, actions: SettingsActions) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsContent(
                        state = state,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                        initialPage = SettingsPage.TRAINING,
                    )
                }
            }
        }
    }

    @Test
    fun `the rest alarm check shows its label and both buttons with hints when the rest timer is on`() {
        setContent(state(restEnabled = true), SettingsActions())

        compose.onNodeWithText("Rest alarm check").assertExists()
        compose.onNodeWithText("Test the alarm (15 s)").assertExists()
        compose.onNodeWithText(
            "Starts a 15 second rest. Turn the screen off or open another app and wait: you should feel the buzz and hear the beep.",
        ).assertExists()
        compose.onNodeWithText("Battery settings").assertExists()
        compose.onNodeWithText(
            "If it only rings when you open the app, set this app to Unrestricted battery use in the phone settings.",
        ).assertExists()
    }

    @Test
    fun `the test alarm button reports one tap`() {
        var taps = 0
        setContent(state(restEnabled = true), SettingsActions(onTestRestAlarm = { taps++ }))

        compose.onNodeWithText("Test the alarm (15 s)").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `the battery settings button reports one tap`() {
        var taps = 0
        setContent(state(restEnabled = true), SettingsActions(onOpenBatterySettings = { taps++ }))

        compose.onNodeWithText("Battery settings").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `the rest alarm check is hidden when the rest timer is off`() {
        setContent(state(restEnabled = false), SettingsActions())

        compose.onNodeWithText("Rest alarm check").assertDoesNotExist()
        compose.onNodeWithText("Test the alarm (15 s)").assertDoesNotExist()
        compose.onNodeWithText("Battery settings").assertDoesNotExist()
    }
}
