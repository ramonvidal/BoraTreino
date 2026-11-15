package com.boratreino.core.model

import java.time.LocalDate
import kotlin.math.roundToLong

/** A body part that can be measured with a tape. The enum order is the display order. */
enum class MeasurementType { WAIST, HIPS, CHEST, ARM, THIGH, NECK }

/** One tape measurement of [type] on [date], in centimetres. */
data class BodyMeasurement(
    val id: Long = 0,
    val date: LocalDate,
    val type: MeasurementType,
    val valueCm: Double,
)

/** One point of a measurement chart. */
data class MeasurementPoint(val date: LocalDate, val valueCm: Double)

/** Rules for body measurements: plain Kotlin, no Android. */
object MeasurementRules {
    /** The step of the plus and minus buttons, in centimetres. */
    const val STEP_CM = 0.5

    /** Smallest value that can be saved, in centimetres. */
    const val MIN_CM = 10.0

    /** Largest value that can be saved, in centimetres. */
    const val MAX_CM = 300.0

    /** [valueCm] rounded to the nearest [STEP_CM] and kept within [MIN_CM]..[MAX_CM]; NaN becomes [MIN_CM]. */
    fun clamp(valueCm: Double): Double {
        if (valueCm.isNaN()) return MIN_CM
        val bounded = valueCm.coerceIn(MIN_CM, MAX_CM)
        return (bounded / STEP_CM).roundToLong() * STEP_CM
    }

    /**
     * The measurements of [type] as chart points, oldest first, one point per date (when a date has
     * several, the one with the highest id wins). With [from], dates before it are left out.
     */
    fun series(all: List<BodyMeasurement>, type: MeasurementType, from: LocalDate? = null): List<MeasurementPoint> =
        all.asSequence()
            .filter { it.type == type && (from == null || it.date >= from) }
            .groupBy { it.date }
            .map { (date, sameDay) -> MeasurementPoint(date, sameDay.maxBy { it.id }.valueCm) }
            .sortedBy { it.date }

    /** The newest measurement of [type]: latest date, then highest id; null when there is none. */
    fun latest(all: List<BodyMeasurement>, type: MeasurementType): BodyMeasurement? =
        all.filter { it.type == type }.maxWithOrNull(compareBy({ it.date }, { it.id }))

    /** Last value minus first value of [series]; null for fewer than two points. */
    fun change(series: List<MeasurementPoint>): Double? =
        if (series.size < 2) null else series.last().valueCm - series.first().valueCm

    /** The types that have at least one measurement, in enum order. */
    fun typesWithData(all: List<BodyMeasurement>): List<MeasurementType> {
        val present = all.map { it.type }.toSet()
        return MeasurementType.entries.filter { it in present }
    }
}
