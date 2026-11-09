package com.boratreino.feature.today

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.boratreino.core.model.Record
import com.boratreino.core.model.TrainingSummary
import com.boratreino.core.ui.formatKg
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One big number with its small label. */
internal data class SummaryStat(val value: String, val label: String)

/** Everything the sheet and the share picture say, already in the app's current language. */
internal data class SummaryTexts(
    val appName: String,
    val title: String,
    val routineName: String,
    val date: String,
    /** Duration, Sets, Volume, Machines, in this order. */
    val stats: List<SummaryStat>,
    val recordsTitle: String,
    /** All the records of the training; the sheet shows up to 5, the picture up to 3. */
    val records: List<String>,
    val compareTitle: String,
    val comparison: List<String>,
)

internal const val SHEET_RECORDS = 5
internal const val PICTURE_RECORDS = 3

/** Builds the texts of [summary] from string resources. */
internal fun summaryTexts(resources: Resources, summary: TrainingSummary, locale: Locale = Locale.getDefault()): SummaryTexts {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    val volume = summary.volumeKg
    val volumeText = if (volume >= 1000.0) {
        val tonnes = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            isGroupingUsed = false
        }.format(volume / 1000.0)
        resources.getString(R.string.summary_tonnes_value, tonnes)
    } else {
        resources.getString(R.string.summary_kg_value, formatKg(volume.roundToInt().toDouble(), locale))
    }
    val minutes = summary.durationMinutes
    val records = summary.records.map { event ->
        when (val record = event.record) {
            is Record.HeaviestWeight ->
                resources.getString(R.string.summary_record_heaviest, event.machine.name, formatKg(record.weightKg, locale))
            is Record.MostReps ->
                resources.getString(R.string.summary_record_most_reps, event.machine.name, record.reps, formatKg(record.weightKg, locale))
        }
    }
    val comparison = buildList {
        summary.previous?.let { previous ->
            previous.volumeChangePercent?.let { percent ->
                val rounded = percent.roundToInt()
                add(
                    when {
                        rounded > 0 -> resources.getString(R.string.summary_compare_volume_up, rounded)
                        rounded < 0 -> resources.getString(R.string.summary_compare_volume_down, abs(rounded))
                        else -> resources.getString(R.string.summary_compare_volume_same)
                    },
                )
            }
            add(
                when {
                    previous.setsChange > 0 -> resources.getString(R.string.summary_compare_sets_up, previous.setsChange)
                    previous.setsChange < 0 -> resources.getString(R.string.summary_compare_sets_down, abs(previous.setsChange))
                    else -> resources.getString(R.string.summary_compare_sets_same)
                },
            )
        }
    }
    return SummaryTexts(
        appName = resources.getString(R.string.today_app_name),
        title = resources.getString(R.string.summary_title),
        routineName = summary.routineName ?: resources.getString(R.string.summary_free_training),
        date = summary.date.format(DateTimeFormatter.ofPattern(pattern, locale)),
        stats = listOf(
            SummaryStat(
                if (minutes != null) resources.getString(R.string.summary_minutes_value, minutes) else resources.getString(R.string.summary_no_value),
                resources.getString(R.string.summary_duration),
            ),
            SummaryStat(summary.workingSets.toString(), resources.getString(R.string.summary_sets)),
            SummaryStat(volumeText, resources.getString(R.string.summary_volume)),
            SummaryStat(summary.machineCount.toString(), resources.getString(R.string.summary_machines)),
        ),
        recordsTitle = resources.getString(R.string.summary_records_title),
        records = records,
        compareTitle = resources.getString(R.string.summary_compare_title),
        comparison = comparison,
    )
}

private const val WIDTH = 1080
private const val HEIGHT = 1350
private const val GREEN = 0xFF2E6B52.toInt()
private const val PAGE = 0xFFF4F4F2.toInt()
private const val INK = 0xFF1C1C1C.toInt()
private const val GREY = 0xFF636360.toInt()

/**
 * Draws the 1080 x 1350 share picture with plain android.graphics and writes it to
 * cacheDir/shared/training-<sessionId>.png. Nothing leaves the phone until the owner picks an app.
 */
internal fun renderSummaryPng(context: Context, summary: SummaryUi, texts: SummaryTexts): File {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(PAGE)

    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawRoundRect(RectF(60f, 60f, WIDTH - 60f, HEIGHT - 60f), 48f, 48f, card)

    val left = 120f
    val innerWidth = WIDTH - 2 * left

    fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun text(value: String, x: Float, y: Float, paint: TextPaint, width: Float = innerWidth) {
        canvas.drawText(TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString(), x, y, paint)
    }

    text(texts.appName, left, 160f, paint(44f, GREEN, bold = true))
    text(texts.date, left, 225f, paint(40f, GREY))
    text(texts.routineName, left, 340f, paint(84f, INK, bold = true))

    // The four numbers in a 2 x 2 grid, so long values like "62 min" and "12.4 t" always fit.
    val columnWidth = innerWidth / 2
    texts.stats.take(4).forEachIndexed { index, stat ->
        val x = left + (index % 2) * columnWidth
        val y = 520f + (index / 2) * 230f
        text(stat.value, x, y, paint(100f, INK, bold = true), columnWidth - 20f)
        text(stat.label, x, y + 56f, paint(38f, GREY), columnWidth - 20f)
    }

    val records = texts.records.take(PICTURE_RECORDS)
    if (records.isNotEmpty()) {
        text(texts.recordsTitle, left, 1030f, paint(38f, GREEN, bold = true))
        val line = paint(42f, INK)
        records.forEachIndexed { index, record -> text(record, left, 1096f + index * 62f, line) }
    }

    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "training-${summary.summary.sessionId}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}
