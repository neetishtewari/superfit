package com.superfit.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class)
    )

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE && healthConnectClient != null
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun readDailyTelemetry(localDate: LocalDate): ActivityTelemetryEntity {
        val client = healthConnectClient ?: return ActivityTelemetryEntity(localDate.toString(), 0, 0.0)
        
        val zoneId = ZoneId.systemDefault()
        val startTime = localDate.atStartOfDay(zoneId).toInstant()
        val endTime = localDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        val steps = readStepsAggregate(client, startTime, endTime)
        val activeCalories = readActiveCaloriesAggregate(client, startTime, endTime)

        return ActivityTelemetryEntity(
            date = localDate.toString(),
            steps = steps.toInt(),
            activeCalories = activeCalories
        )
    }

    suspend fun readSleepTelemetry(localDate: LocalDate): SleepTelemetryEntity? {
        val client = healthConnectClient ?: return null

        try {
            val zoneId = ZoneId.systemDefault()
            // Query sleep sessions spanning last night
            val startTime = localDate.minusDays(1).atStartOfDay(zoneId).toInstant()
            val endTime = localDate.plusDays(1).atStartOfDay(zoneId).toInstant()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            // Find the sleep sessions that end on the given date (morning of localDate)
            val relevantSessions = response.records.filter { record ->
                val recordEndDate = LocalDate.ofInstant(record.endTime, zoneId)
                recordEndDate == localDate
            }

            val targetSessions = if (relevantSessions.isNotEmpty()) {
                relevantSessions
            } else {
                val lastRecord = response.records.lastOrNull()
                if (lastRecord != null) {
                    val lastRecordEndDate = LocalDate.ofInstant(lastRecord.endTime, zoneId)
                    response.records.filter { record ->
                        LocalDate.ofInstant(record.endTime, zoneId) == lastRecordEndDate
                    }
                } else {
                    emptyList()
                }
            }

            if (targetSessions.isNotEmpty()) {
                val totalDurationSeconds = targetSessions.sumOf { record ->
                    ChronoUnit.SECONDS.between(record.startTime, record.endTime)
                }
                // Standard ratio estimate for deep sleep (20%)
                val deepSleepSeconds = (totalDurationSeconds * 0.2).toLong()

                // 8 hours (28800s) = 100% sleep score
                val sleepRatio = totalDurationSeconds.toDouble() / 28800.0
                val sleepReadiness = (sleepRatio * 100).toInt().coerceIn(0, 100)

                return SleepTelemetryEntity(
                    date = localDate.toString(),
                    sleepDurationSeconds = totalDurationSeconds,
                    deepSleepDurationSeconds = deepSleepSeconds,
                    readinessScore = sleepReadiness
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private suspend fun readStepsAggregate(client: HealthConnectClient, startTime: Instant, endTime: Instant): Long {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            // Group records by package name and sum the count for each package name
            val stepsByPackage = response.records.groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { entry -> entry.value.sumOf { it.count } }

            // Prioritize Samsung Health
            val shealthSteps = stepsByPackage["com.sec.android.app.shealth"]
            if (shealthSteps != null) {
                shealthSteps
            } else {
                // Fallback to the maximum single source count
                stepsByPackage.values.maxOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun readActiveCaloriesAggregate(client: HealthConnectClient, startTime: Instant, endTime: Instant): Double {
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val energy = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
            // Access inKilocalories (the standard calorie scale used for foods and workouts)
            energy?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun readLatestWeight(): Double? {
        val client = healthConnectClient ?: return null
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                    ascendingOrder = false,
                    pageSize = 1
                )
            )
            response.records.firstOrNull()?.weight?.inKilograms
        } catch (e: Exception) {
            null
        }
    }

    suspend fun readLatestHeight(): Double? {
        val client = healthConnectClient ?: return null
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                    ascendingOrder = false,
                    pageSize = 1
                )
            )
            response.records.firstOrNull()?.height?.inMeters?.let { it * 100.0 }
        } catch (e: Exception) {
            null
        }
    }
}
