package com.boratreino.feature.update

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The "App updates" card in each status, light and dark; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class UpdatesCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: UpdatesCardUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    UpdatesCardContent(
                        state = state,
                        actions = UpdatesCardActions(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun card(status: UpdatesCardStatus) = UpdatesCardUiState(installedVersion = "0.40", status = status)

    @Test
    fun `idle shows the version and an enabled check button`() {
        shoot("updates-card-idle", card(UpdatesCardStatus.Idle))

        compose.onNodeWithText("Bora pro Treino! 0.40").assertExists()
        compose.onNodeWithText("Check for updates").assert(isEnabled())
    }

    @Test
    fun `up to date`() {
        shoot("updates-card-up-to-date", card(UpdatesCardStatus.UpToDate))

        compose.onNodeWithText("You're up to date").assertExists()
    }

    @Test
    fun `an update is available`() {
        shoot("updates-card-available", card(UpdatesCardStatus.Available("0.41")))

        compose.onNodeWithText("Version 0.41 is available").assertExists()
    }

    @Test
    fun `an update is available in dark`() =
        shoot("updates-card-available-dark", card(UpdatesCardStatus.Available("0.41")), dark = true)

    @Test
    fun `the update page cannot be reached`() {
        shoot("updates-card-unreachable", card(UpdatesCardStatus.Unreachable))

        compose.onNodeWithText("Couldn't reach the update page").assertExists()
    }

    @Test
    fun `checking disables the check button`() {
        shoot("updates-card-checking", card(UpdatesCardStatus.Checking))

        compose.onNodeWithText("Check for updates").assert(isNotEnabled())
    }
}
