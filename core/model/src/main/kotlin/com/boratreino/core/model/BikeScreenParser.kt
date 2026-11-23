package com.boratreino.core.model

import kotlin.math.abs

/** One line of text an OCR engine found, with its bounding box in image pixels (all 0 = no position known). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * What a bike console summary screen showed; null = not on the screen, unreadable or implausible.
 * [distanceM] is in metres, [minutes] is rounded to the nearest minute.
 */
data class BikeReading(
    val minutes: Int?,
    val calories: Int?,
    val distanceM: Int?,
    val watts: Int?,
    val heartRate: Int?,
    val wattsPerKg: Double?,
    val tss: Int?,
    val intensityFactor: Double?,
) {
    /** True when nothing could be read. */
    val isEmpty: Boolean
        get() = minutes == null && calories == null && distanceM == null && watts == null &&
            heartRate == null && wattsPerKg == null && tss == null && intensityFactor == null
}

/**
 * Reads a bike console summary screen (rows with a label on the left and a value on the right, e.g.
 * "KALORIEN 397", "KM 17.30", "WATT / HR 165 / 142", "TSS / IF 48 / 0.79", header "ZUSAMMENFASSUNG - 00:45:52")
 * from OCR lines. Values are matched to labels by vertical position (tolerating a tilted photo) or, when no
 * line has a box, by text order. Implausible values are left null rather than clamped.
 */
object BikeScreenParser {
    private enum class Field { TIME, CALORIES, DISTANCE, WATTS, HEART_RATE, WATTS_PER_KG, TSS, INTENSITY_FACTOR }

    private const val MAX_KM = 500.0
    private const val MAX_MINUTES = 600

    // Longest alternatives first so "CALORIES" wins over "CAL" and "WATT / KG" over "WATT".
    private val POWER = "(?:WATTS|WATT|POWER)"
    private val HR = "(?:HEART\\s*RATE|HR|PULS)"
    private val LABEL = Regex(
        "^\\s*(" +
            "(?:WATTS?|W)\\s*/\\s*KG|" +
            "$POWER\\s*/\\s*$HR|" +
            "TSS\\s*/\\s*IF|" +
            "KALORIEN|KALORIE|CALORIES|KCAL|CAL|" +
            "DISTANCE|DISTANZ|KM|" +
            "TIME|ZEIT|DAUER|" +
            "HEART\\s*RATE|HR|PULS|" +
            "WATTS|WATT|POWER|" +
            "TSS|IF" +
            ")(?![A-Za-z])[\\s:]*",
        RegexOption.IGNORE_CASE,
    )
    private val VALUE_ONLY = Regex("^\\s*[-\\u2013\\u2014\\d.,:/\\s]*[A-Za-z%]{0,5}\\s*$")
    private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
    private val CLOCK = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?![\\d:])")
    private val HH_MM_SS = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2}):(\\d{2})(?![\\d:])")

    private class Entry(val line: OcrLine, val index: Int) {
        var fields: List<Field> = emptyList()
        var inline: String? = null
        var value: String? = null
        val isLabel get() = fields.isNotEmpty()
        val cx get() = (line.left + line.right) / 2.0
        val cy get() = (line.top + line.bottom) / 2.0
        val height get() = (line.bottom - line.top).toDouble()
    }

    fun parse(lines: List<OcrLine>): BikeReading {
        val entries = lines.mapIndexedNotNull { i, l ->
            if (l.text.isBlank()) null else Entry(l, i)
        }
        for (e in entries) {
            val m = LABEL.find(e.line.text) ?: continue
            e.fields = fieldsOf(m.groupValues[1])
            e.inline = e.line.text.substring(m.range.last + 1).trim()
        }
        val labels = entries.filter { it.isLabel }
        val values = entries.filter { !it.isLabel && isValueOnly(it.line.text) }

        for (l in labels) if (!l.inline.isNullOrBlank()) l.value = l.inline
        val open = labels.filter { it.value == null }
        if (entries.all { hasNoBox(it.line) }) pairByOrder(open, labels, values) else pairByPosition(open, values)

        val found = HashMap<Field, String?>()
        for (l in labels) {
            val v = l.value ?: continue
            val parts = if (l.fields.size > 1) v.split('/') else listOf(v)
            l.fields.forEachIndexed { i, f ->
                if (f !in found) found[f] = parts.getOrNull(i)
            }
        }

        val seconds = found[Field.TIME]?.let { clock(it) } ?: lines.firstNotNullOfOrNull { headerTime(it.text) }
        val minutes = seconds?.let { ((it + 30) / 60).toInt() }?.takeIf { it in 0..MAX_MINUTES }
        val km = number(found[Field.DISTANCE])?.takeIf { it in 0.0..MAX_KM }
        return BikeReading(
            minutes = minutes,
            calories = number(found[Field.CALORIES])?.let { Math.round(it).toInt() }
                ?.takeIf { it in 0..CardioRules.MAX_CALORIES },
            distanceM = km?.let { Math.round(it * 1000).toInt() },
            watts = number(found[Field.WATTS])?.let { Math.round(it).toInt() }
                ?.takeIf { it in 0..CardioRules.MAX_WATTS },
            heartRate = number(found[Field.HEART_RATE])?.let { Math.round(it).toInt() }
                ?.takeIf { it in CardioRules.MIN_HEART_RATE..CardioRules.MAX_HEART_RATE },
            wattsPerKg = number(found[Field.WATTS_PER_KG])?.takeIf { it in 0.0..CardioRules.MAX_WATTS_PER_KG }
                ?.let { Math.round(it * 10) / 10.0 },
            tss = number(found[Field.TSS])?.let { Math.round(it).toInt() }?.takeIf { it in 0..CardioRules.MAX_TSS },
            intensityFactor = number(found[Field.INTENSITY_FACTOR])
                ?.takeIf { it in 0.0..CardioRules.MAX_INTENSITY_FACTOR }?.let { Math.round(it * 100) / 100.0 },
        )
    }

    private fun fieldsOf(label: String): List<Field> {
        val key = label.uppercase().replace(Regex("\\s+"), "")
        return when {
            Regex("(WATTS?|W)/KG").matches(key) -> listOf(Field.WATTS_PER_KG)
            "/" in key -> key.split('/').map { single(it) }
            else -> listOf(single(key))
        }
    }

    private fun single(key: String): Field = when (key) {
        "KALORIEN", "KALORIE", "CALORIES", "KCAL", "CAL" -> Field.CALORIES
        "DISTANCE", "DISTANZ", "KM" -> Field.DISTANCE
        "TIME", "ZEIT", "DAUER" -> Field.TIME
        "HEARTRATE", "HR", "PULS" -> Field.HEART_RATE
        "TSS" -> Field.TSS
        "IF" -> Field.INTENSITY_FACTOR
        else -> Field.WATTS
    }

    private fun isValueOnly(text: String): Boolean =
        VALUE_ONLY.matches(text) && text.any { it.isDigit() || it in "-–—" }

    private fun hasNoBox(l: OcrLine) = l.left == 0 && l.top == 0 && l.right == 0 && l.bottom == 0

    /** Text order: a label takes the next unused value-only line before the next label line. */
    private fun pairByOrder(open: List<Entry>, labels: List<Entry>, values: List<Entry>) {
        val used = HashSet<Entry>()
        for (l in open) {
            val next = labels.firstOrNull { it.index > l.index }?.index ?: Int.MAX_VALUE
            val v = values.firstOrNull { it !in used && it.index > l.index && it.index < next && fits(l, it) }
                ?: continue
            used += v
            l.value = v.line.text
        }
    }

    /** A time label takes a clock value; every other label takes a value without one. */
    private fun fits(label: Entry, value: Entry): Boolean =
        (Field.TIME in label.fields) == value.line.text.contains(':')

    private class Match(val pairs: List<Pair<Entry, Entry>>, val error: Double)

    private fun pairByPosition(open: List<Entry>, values: List<Entry>) {
        if (open.isEmpty() || values.isEmpty()) return
        // The photo may be tilted: try slope 0 and every slope some label/value pair suggests, and keep the one
        // that pairs the most labels (then the smallest error, then the flattest slope).
        val slopes = linkedSetOf(0.0)
        for (l in open) for (v in values) {
            if (fits(l, v) && v.cx > l.cx && abs(v.cy - l.cy) <= 3 * maxOf(l.height, v.height, 1.0)) {
                slopes += (v.cy - l.cy) / (v.cx - l.cx)
            }
        }
        val best = slopes
            .map { it to match(open, values, it) }
            .minWithOrNull(
                compareBy<Pair<Double, Match>> { -it.second.pairs.size }
                    .thenBy { it.second.error }
                    .thenBy { abs(it.first) },
            )?.second ?: return
        for ((l, v) in best.pairs) l.value = v.line.text
    }

    /** Greedy closest-first pairing of labels with values to their right, within one line height after [slope]. */
    private fun match(open: List<Entry>, values: List<Entry>, slope: Double): Match {
        val candidates = ArrayList<Triple<Double, Entry, Entry>>()
        for (l in open) for (v in values) {
            if (!fits(l, v) || v.cx <= l.cx) continue
            val dy = abs(v.cy - l.cy - slope * (v.cx - l.cx))
            if (dy <= maxOf(l.height, v.height, 1.0)) candidates += Triple(dy, l, v)
        }
        val usedLabels = HashSet<Entry>()
        val usedValues = HashSet<Entry>()
        val pairs = ArrayList<Pair<Entry, Entry>>()
        var error = 0.0
        for ((dy, l, v) in candidates.sortedBy { it.first }) {
            if (l in usedLabels || v in usedValues) continue
            usedLabels += l
            usedValues += v
            pairs += l to v
            error += dy
        }
        return Match(pairs, error)
    }

    private fun number(text: String?): Double? {
        val m = NUMBER.find(text ?: return null) ?: return null
        return m.value.replace(',', '.').toDoubleOrNull()
    }

    /** Seconds in "hh:mm:ss" or "mm:ss"; null when there is no clock value. */
    private fun clock(text: String): Long? {
        val m = CLOCK.find(text) ?: return null
        val a = m.groupValues[1].toLong()
        val b = m.groupValues[2].toLong()
        val c = m.groupValues[3]
        return if (c.isEmpty()) a * 60 + b else a * 3600 + b * 60 + c.toLong()
    }

    /** A "hh:mm:ss" anywhere in a line, e.g. the header "ZUSAMMENFASSUNG - 00:45:52". */
    private fun headerTime(text: String): Long? {
        val m = HH_MM_SS.find(text) ?: return null
        return m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()
    }
}
