package com.boratreino.core.data

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Body (tape) measurements. */
interface MeasurementRepository {
    /** Every measurement, newest date first. */
    fun observeAll(): Flow<List<BodyMeasurement>>

    /** Saves a measurement, clamped with `MeasurementRules.clamp`. Returns its id. */
    suspend fun add(date: LocalDate, type: MeasurementType, valueCm: Double): Long

    /** Deletes a measurement and returns what was deleted (for Undo), or null when it is missing. */
    suspend fun delete(id: Long): BodyMeasurement?

    /** Puts a deleted measurement back with its id. */
    suspend fun restore(measurement: BodyMeasurement)
}
