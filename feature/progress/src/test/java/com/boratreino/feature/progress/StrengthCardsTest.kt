package com.boratreino.feature.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MachineTrend
import com.boratreino.core.model.MuscleVolume
import com.boratreino.core.model.TrendVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Strength page's trend summary and weekly muscle volume cards, checked on the texts and taps they show. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2000dp")
class StrengthCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showTrends(trends: List<MachineTrend>, onOpen: (Long) -> Unit = {}) {
        compose.setContent { StatsCardHost { TrendSummaryCard(trends = trends, onOpenMachine = onOpen) } }
    }

    private fun showVolumes(volumes: List<MuscleVolume>) {
        compose.setContent { StatsCardHost { MuscleVolumeCard(volumes = volumes) } }
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun yOf(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `the trend groups show their counts in rising, plateau, falling and needs more data order`() {
        showTrends(Phase46Samples.machineTrends)

        shown("Trends by exercise")
        shown("Rising (2)")
        shown("Plateau (1)")
        shown("Falling (1)")
        shown("Needs more data (1)")
        assertTrue(yOf("Rising (2)") < yOf("Plateau (1)"))
        assertTrue(yOf("Plateau (1)") < yOf("Falling (1)"))
        assertTrue(yOf("Falling (1)") < yOf("Needs more data (1)"))
    }

    @Test
    fun `the machines with a weekly change are listed under their group`() {
        showTrends(Phase46Samples.machineTrends)

        shown("Bench press")
        shown("Leg press")
        shown("Lat pulldown")
        shown("Bicep curl")
    }

    @Test
    fun `a machine that needs more data is counted but not listed`() {
        showTrends(Phase46Samples.machineTrends)

        compose.onNodeWithText("Cable row").assertDoesNotExist()
    }

    @Test
    fun `tapping a machine name opens that machine`() {
        val opened = mutableListOf<Long>()
        showTrends(Phase46Samples.machineTrends, onOpen = { opened += it })

        compose.onNodeWithText("Bench press").performClick()
        compose.onNodeWithText("Bicep curl").performClick()

        assertEquals(listOf(1L, 4L), opened)
    }

    @Test
    fun `only machines that need data gives a count and no rising or falling group`() {
        showTrends(
            listOf(
                Phase46Samples.machineTrend(1, "Cable row", TrendVerdict.NOT_ENOUGH_DATA),
                Phase46Samples.machineTrend(2, "Pec deck", TrendVerdict.NOT_ENOUGH_DATA),
            ),
        )

        shown("Needs more data (2)")
        compose.onNodeWithText("Rising (", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Falling (", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Plateau (", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an empty list shows the title and no group`() {
        showTrends(emptyList())

        shown("Trends by exercise")
        compose.onNodeWithText("Rising (", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Needs more data (", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a group of four rising machines lists them all under the rising count`() {
        showTrends(
            listOf(
                Phase46Samples.machineTrend(1, "Bench press", TrendVerdict.RISING, 1.5),
                Phase46Samples.machineTrend(2, "Leg press", TrendVerdict.RISING, 0.9),
                Phase46Samples.machineTrend(3, "Row", TrendVerdict.RISING, 0.6),
                Phase46Samples.machineTrend(4, "Pull up", TrendVerdict.RISING, 0.3),
            ),
        )

        shown("Rising (4)")
        shown("Bench press")
    }

    @Test
    fun `the muscle volume rows show each muscle with its weekly sets and band`() {
        showVolumes(Phase46Samples.volumes)

        shown("Weekly sets per muscle")
        compose.onNodeWithContentDescription("Chest: 0.0 / No sets").assertIsDisplayed()
        compose.onNodeWithContentDescription("Biceps: 4.0 / Below 10").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quadriceps: 14.0 / In range").assertIsDisplayed()
        compose.onNodeWithContentDescription("Upper back: 24.0 / Above 20").assertIsDisplayed()
    }

    @Test
    fun `the muscle volume card states the 10 to 20 guideline`() {
        showVolumes(Phase46Samples.volumes)

        shown("Direct sets per week in this period. A common guideline for growth is 10 to 20.")
    }
}
