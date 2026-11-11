package com.boratreino.feature.today

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.PreviousComparison
import com.boratreino.core.model.Record
import com.boratreino.core.model.RecordEvent
import com.boratreino.core.model.TrainingSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * The share picture and the texts it carries: the texts come from the summary's numbers, the picture is a
 * 1080 x 1350 PNG in cacheDir/shared. Checked through the file, not the drawing code.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TodayShareTest {
    private val context = RuntimeEnvironment.getApplication()
    private val date = LocalDate.of(2026, 11, 9)
    private val bench = Machine(1L, "Leg press", MuscleGroup.LEGS, MachineKind.WEIGHT_REPS, 2.5, null, null, false)

    private fun summary(
        sessionId: Long = 21L,
        routineName: String? = "Routine A",
        minutes: Int? = 62,
        volumeKg: Double = 980.0,
        records: List<RecordEvent> = emptyList(),
        previous: PreviousComparison? = null,
    ) = TrainingSummary(
        sessionId = sessionId,
        routineName = routineName,
        date = date,
        durationMinutes = minutes,
        workingSets = 9,
        volumeKg = volumeKg,
        machineCount = 3,
        records = records,
        previous = previous,
    )

    private fun render(summary: TrainingSummary): java.io.File {
        val ui = SummaryUi(summary)
        return renderSummaryPng(context, ui, summaryTexts(context.resources, summary))
    }

    private fun size(file: java.io.File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        assertEquals("image/png", options.outMimeType)
        return options.outWidth to options.outHeight
    }

    // The picture file

    @Test
    fun `the picture is a 1080 by 1350 PNG under cacheDir shared, named after the session`() {
        val file = render(summary(sessionId = 21L))

        assertTrue(file.exists())
        assertEquals("training-21.png", file.name)
        assertEquals(java.io.File(context.cacheDir, "shared").canonicalPath, file.parentFile!!.canonicalPath)
        assertEquals(1080 to 1350, size(file))
    }

    @Test
    fun `a summary with records and a previous session still makes a 1080 by 1350 PNG`() {
        val file = render(
            summary(
                records = listOf(
                    RecordEvent(bench, date, Record.HeaviestWeight(100.0)),
                    RecordEvent(bench, date, Record.MostReps(80.0, 12)),
                    RecordEvent(bench, date, Record.HeaviestWeight(90.0)),
                    RecordEvent(bench, date, Record.HeaviestWeight(85.0)),
                ),
                previous = PreviousComparison(volumeKg = 900.0, workingSets = 7, volumeChangePercent = 8.4, setsChange = 2),
            ),
        )

        assertEquals(1080 to 1350, size(file))
    }

    @Test
    fun `a free session without records makes a 1080 by 1350 PNG`() {
        val file = render(summary(routineName = null, minutes = null, volumeKg = 0.0, records = emptyList(), previous = null))

        assertEquals(1080 to 1350, size(file))
    }

    @Test
    fun `a very long routine name is cut to fit and the picture is still 1080 by 1350`() {
        val file = render(summary(routineName = "A routine name that goes on and on well past the width of the card"))

        assertEquals(1080 to 1350, size(file))
    }

    @Test
    fun `a second render of the same session replaces the file`() {
        val first = render(summary(sessionId = 33L, volumeKg = 100.0))
        val second = render(summary(sessionId = 33L, volumeKg = 12400.0))

        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals(1080 to 1350, size(second))
    }

    // The texts the picture and the sheet share

    @Test
    fun `the volume is shown in kilograms below a tonne`() {
        val texts = summaryTexts(context.resources, summary(volumeKg = 980.0), Locale.US)

        assertEquals("980 kg", texts.stats.single { it.label == "Volume" }.value)
    }

    @Test
    fun `the volume is shown in tonnes from a tonne up`() {
        val texts = summaryTexts(context.resources, summary(volumeKg = 12400.0), Locale.US)

        assertEquals("12.4 t", texts.stats.single { it.label == "Volume" }.value)
    }

    @Test
    fun `the four numbers are duration, sets, volume and exercises in that order`() {
        val texts = summaryTexts(context.resources, summary(minutes = 62), Locale.US)

        assertEquals(listOf("Duration", "Sets", "Volume", "Exercises"), texts.stats.map { it.label })
        assertEquals(listOf("62 min", "9", "980 kg", "3"), texts.stats.map { it.value })
    }

    @Test
    fun `a session without a real duration shows a dash`() {
        val texts = summaryTexts(context.resources, summary(minutes = null), Locale.US)

        assertEquals("-", texts.stats.first().value)
    }

    @Test
    fun `a free session is named Free training`() {
        val texts = summaryTexts(context.resources, summary(routineName = null), Locale.US)

        assertEquals("Free training", texts.routineName)
    }

    @Test
    fun `records are written in words with the machine name`() {
        val texts = summaryTexts(
            context.resources,
            summary(
                records = listOf(
                    RecordEvent(bench, date, Record.MostReps(80.0, 12)),
                    RecordEvent(bench, date, Record.HeaviestWeight(100.0)),
                ),
            ),
            Locale.US,
        )

        assertEquals(
            listOf("Leg press: Most reps 12 at 80 kg", "Leg press: Heaviest 100 kg"),
            texts.records,
        )
    }

    @Test
    fun `a volume up, down or the same is worded against last time`() {
        fun comparison(percent: Double?) = summaryTexts(
            context.resources,
            summary(previous = PreviousComparison(900.0, 7, percent, 0)),
            Locale.US,
        ).comparison.first()

        assertEquals("Volume +8 % vs last time", comparison(8.4))
        assertEquals("Volume -3 % vs last time", comparison(-2.9))
        assertEquals("Volume same as last time", comparison(0.0))
    }

    @Test
    fun `no percent when the previous volume was zero leaves only the sets line`() {
        val texts = summaryTexts(
            context.resources,
            summary(previous = PreviousComparison(0.0, 0, null, 4)),
            Locale.US,
        )

        assertEquals(listOf("Sets +4"), texts.comparison)
    }

    @Test
    fun `the comparison is empty without a previous session`() {
        val texts = summaryTexts(context.resources, summary(previous = null), Locale.US)

        assertTrue(texts.comparison.isEmpty())
        assertNotNull(texts.compareTitle)
    }
}
