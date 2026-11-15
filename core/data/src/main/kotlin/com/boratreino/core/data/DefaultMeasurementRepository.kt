package com.boratreino.core.data

import com.boratreino.core.database.WorkoutStore
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementRules
import com.boratreino.core.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

internal class DefaultMeasurementRepository @Inject constructor(
    private val store: WorkoutStore,
) : MeasurementRepository {
    override fun observeAll(): Flow<List<BodyMeasurement>> = store.observeMeasurements()

    override suspend fun add(date: LocalDate, type: MeasurementType, valueCm: Double): Long =
        store.addMeasurement(BodyMeasurement(date = date, type = type, valueCm = MeasurementRules.clamp(valueCm)))

    override suspend fun delete(id: Long): BodyMeasurement? =
        store.inTransaction {
            val found = store.measurement(id)
            if (found != null) store.deleteMeasurement(id)
            found
        }

    override suspend fun restore(measurement: BodyMeasurement) = store.restoreMeasurement(measurement)
}
