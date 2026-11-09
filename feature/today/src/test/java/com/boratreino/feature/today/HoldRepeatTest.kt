package com.boratreino.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The stepper's hold-to-repeat, on the Compose test clock: one step on press, the first repeat
 * after 400 ms, then every 150 ms, and every 70 ms from the 12th repeat on; release stops it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class HoldRepeatTest {
    @get:Rule
    val compose = createComposeRule()

    private var steps = 0
    private var now = 0L

    private fun setStepper() {
        steps = 0
        now = 0L
        compose.setContent {
            Box(Modifier.size(120.dp).testTag(TAG).holdRepeat("Add weight") { steps++ })
        }
        // Time moves only when the test says so.
        compose.mainClock.autoAdvance = false
    }

    /** Moves the test clock to [ms] after the press (absolute, from the press at 0). */
    private fun at(ms: Long) {
        compose.mainClock.advanceTimeBy(ms - now, ignoreFrameDuration = true)
        now = ms
    }

    private fun press() = compose.onNodeWithTag(TAG).performTouchInput { down(center) }

    private fun release() = compose.onNodeWithTag(TAG).performTouchInput { up() }

    @Test
    fun `a press gives one step at once`() {
        setStepper()

        press()

        assertEquals(1, steps)
    }

    @Test
    fun `a quick tap gives exactly one step`() {
        setStepper()

        compose.onNodeWithTag(TAG).performClick()
        at(1_000)

        assertEquals(1, steps)
    }

    @Test
    fun `holding waits 400 ms before the first repeat`() {
        setStepper()
        press()

        // Checked either side of the event so the result does not depend on the clock's tick rule.
        at(399)
        assertEquals(1, steps)

        at(401)
        assertEquals(2, steps)
    }

    @Test
    fun `then it repeats every 150 ms`() {
        setStepper()
        press()

        at(549)
        assertEquals(2, steps)

        at(551)
        assertEquals(3, steps)

        at(701)
        assertEquals(4, steps)
    }

    @Test
    fun `after 12 repeats it speeds up to every 70 ms`() {
        setStepper()
        press()

        // Repeats 1 to 11 come every 150 ms: the 12th lands at 400 + 11 * 150 = 2050 ms.
        at(2_049)
        assertEquals(12, steps)

        at(2_051)
        assertEquals(13, steps)

        // From the 12th repeat on the gap is 70 ms: the 13th at 2120 ms, the 14th at 2190 ms.
        at(2_119)
        assertEquals(13, steps)

        at(2_121)
        assertEquals(14, steps)

        at(2_191)
        assertEquals(15, steps)
    }

    @Test
    fun `releasing stops the repeats`() {
        setStepper()
        press()
        at(500)
        assertEquals(2, steps)

        release()
        at(3_000)

        assertEquals(2, steps)
    }

    private companion object {
        const val TAG = "hold-repeat-target"
    }
}
