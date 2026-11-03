package com.boratreino.core.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.boratreino.core.model.HealthExerciseKind
import com.boratreino.core.model.HealthExport
import com.boratreino.core.model.HealthExportRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultHealthConnectAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectAccess {

    override val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
    )

    override suspend fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = HealthConnectClient.getOrCreate(context)
                    .permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) HealthAvailability.Ready else HealthAvailability.NeedsPermission
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsUpdate
            // Before Android 14 the provider is an app; it is simply missing. From 14 it is part of the system.
            else -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !providerInstalled()) {
                HealthAvailability.NotInstalled
            } else {
                HealthAvailability.Unsupported
            }
        }

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    override fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
    ).apply {
        setPackage("com.android.vending")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    override suspend fun write(exports: List<HealthExport>) {
        if (exports.isEmpty()) return
        val records = exports.flatMap { records(it) }
        HealthConnectClient.getOrCreate(context).insertRecords(records)
    }

    override suspend fun delete(sessionIds: Set<Long>) {
        if (sessionIds.isEmpty()) return
        val client = HealthConnectClient.getOrCreate(context)
        val ids = sessionIds.map { HealthExportRules.clientId(it) }
        client.deleteRecords(ExerciseSessionRecord::class, emptyList(), ids)
        client.deleteRecords(ActiveCaloriesBurnedRecord::class, emptyList(), ids.map { it + CAL_SUFFIX })
        client.deleteRecords(DistanceRecord::class, emptyList(), ids.map { it + DIST_SUFFIX })
    }

    private fun records(export: HealthExport): List<androidx.health.connect.client.records.Record> {
        val startOffset = offsetAt(export.start)
        val endOffset = offsetAt(export.end)
        val session = ExerciseSessionRecord(
            startTime = export.start,
            startZoneOffset = startOffset,
            endTime = export.end,
            endZoneOffset = endOffset,
            metadata = Metadata.manualEntry(
                clientRecordId = export.clientRecordId,
                clientRecordVersion = export.version,
            ),
            exerciseType = exerciseType(export.kind),
            title = export.title,
        )
        val calories = if (export.activeCalories > 0) {
            ActiveCaloriesBurnedRecord(
                startTime = export.start,
                startZoneOffset = startOffset,
                endTime = export.end,
                endZoneOffset = endOffset,
                energy = Energy.kilocalories(export.activeCalories.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = export.clientRecordId + CAL_SUFFIX,
                    clientRecordVersion = export.version,
                ),
            )
        } else {
            null
        }
        val distance = if (export.distanceM > 0) {
            DistanceRecord(
                startTime = export.start,
                startZoneOffset = startOffset,
                endTime = export.end,
                endZoneOffset = endOffset,
                distance = Length.meters(export.distanceM.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = export.clientRecordId + DIST_SUFFIX,
                    clientRecordVersion = export.version,
                ),
            )
        } else {
            null
        }
        return listOfNotNull(session, calories, distance)
    }

    private fun offsetAt(instant: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(instant)

    private fun exerciseType(kind: HealthExerciseKind): Int = when (kind) {
        HealthExerciseKind.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        HealthExerciseKind.TREADMILL -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        HealthExerciseKind.STATIONARY_BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
        HealthExerciseKind.ELLIPTICAL -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        HealthExerciseKind.ROWING_MACHINE -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        HealthExerciseKind.STAIR_CLIMBING_MACHINE -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        HealthExerciseKind.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        HealthExerciseKind.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        HealthExerciseKind.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    private fun providerInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(PROVIDER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        const val CAL_SUFFIX = "-cal"
        const val DIST_SUFFIX = "-dist"
    }
}
