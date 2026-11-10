package com.superfit.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
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

    /** Body fat, lean mass, bone mass and BMR from smart scale apps. Asked for separately, so people without a scale aren't asked. */
    val bodyPermissions = setOf(
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class)
    )

    suspend fun hasBodyPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            client.permissionController.getGrantedPermissions().containsAll(bodyPermissions)
        } catch (e: Exception) {
            false
        }
    }

    /** Every weigh-in since [since], from any app. Empty if weight access is off or the read fails. */
    suspend fun readWeighIns(since: Instant): List<com.superfit.app.domain.ScaleReading> = readAll(WeightRecord::class, since).map {
        com.superfit.app.domain.ScaleReading(
            id = it.metadata.id,
            timeMs = it.time.toEpochMilli(),
            weightKg = it.weight.inKilograms,
            source = it.metadata.dataOrigin.packageName
        )
    }

    suspend fun readBodyFat(since: Instant) = readAll(BodyFatRecord::class, since).map {
        com.superfit.app.domain.BodyValue(it.time.toEpochMilli(), it.metadata.dataOrigin.packageName, it.percentage.value)
    }

    suspend fun readLeanMass(since: Instant) = readAll(LeanBodyMassRecord::class, since).map {
        com.superfit.app.domain.BodyValue(it.time.toEpochMilli(), it.metadata.dataOrigin.packageName, it.mass.inKilograms)
    }

    suspend fun readBoneMass(since: Instant) = readAll(BoneMassRecord::class, since).map {
        com.superfit.app.domain.BodyValue(it.time.toEpochMilli(), it.metadata.dataOrigin.packageName, it.mass.inKilograms)
    }

    suspend fun readScaleBmr(since: Instant) = readAll(BasalMetabolicRateRecord::class, since).map {
        com.superfit.app.domain.BodyValue(it.time.toEpochMilli(), it.metadata.dataOrigin.packageName, it.basalMetabolicRate.inKilocaloriesPerDay)
    }

    /** All records of one type since [since], following pages. Empty when not allowed or on failure. */
    private suspend fun <T : Record> readAll(type: kotlin.reflect.KClass<T>, since: Instant): List<T> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val out = mutableListOf<T>()
            var token: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = type,
                        timeRangeFilter = TimeRangeFilter.between(since, Instant.now()),
                        pageToken = token
                    )
                )
                out += response.records
                token = response.pageToken
            } while (token != null && out.size < 5000)
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE && healthConnectClient != null
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Returns null when Health Connect refuses or fails the steps read (for example when called
     * from the background), so callers keep the last good value instead of saving 0 steps.
     */
    suspend fun readDailyTelemetry(localDate: LocalDate): ActivityTelemetryEntity? {
        val client = healthConnectClient ?: return null

        val zoneId = ZoneId.systemDefault()
        val startTime = localDate.atStartOfDay(zoneId).toInstant()
        val endTime = localDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        val steps = try {
            readStepsTotal(client, startTime, endTime)
        } catch (e: Exception) {
            android.util.Log.e("HealthConnectManager", "Reading steps for $localDate failed", e)
            return null
        }
        val activeCalories = readActiveCaloriesAggregate(client, startTime, endTime)

        return ActivityTelemetryEntity(
            date = localDate.toString(),
            steps = steps.toInt(),
            activeCalories = activeCalories
        )
    }

    /** The day's walked/run distance in meters as a watch or phone app recorded it, or null. Not stored. */
    suspend fun readDistanceMeters(localDate: LocalDate): Double? {
        val client = healthConnectClient ?: return null
        val zoneId = ZoneId.systemDefault()
        return readDistanceTotal(client, localDate.atStartOfDay(zoneId).toInstant(), localDate.plusDays(1).atStartOfDay(zoneId).toInstant())
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
                val recordEndDate = record.endTime.atZone(zoneId).toLocalDate()
                recordEndDate == localDate
            }

            val targetSessions = if (relevantSessions.isNotEmpty()) {
                relevantSessions
            } else {
                val lastRecord = response.records.lastOrNull()
                if (lastRecord != null) {
                    val lastRecordEndDate = lastRecord.endTime.atZone(zoneId).toLocalDate()
                    response.records.filter { record ->
                        record.endTime.atZone(zoneId).toLocalDate() == lastRecordEndDate
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

    // The day's steps from one app, never several added together. Health Connect's merged total
    // and Google Fit can both add a watch app's steps to the phone's own count (on one phone:
    // Samsung Health 23,562 = the watch, Google Fit 11,391, phone 11,705, merged 31,727; on other
    // days Google Fit alone showed watch + phone). So a watch app is trusted first (see pickSource).
    private suspend fun readStepsTotal(client: HealthConnectClient, startTime: Instant, endTime: Instant): Long =
        largestSingleSource(client, StepsRecord.COUNT_TOTAL, startTime, endTime, "steps", compare = { it.toDouble() }) ?: 0L

    /**
     * The day's distance in meters from one app (picked like steps), or null when no app
     * records distance or distance access is off (then the app estimates it from steps).
     */
    private suspend fun readDistanceTotal(client: HealthConnectClient, startTime: Instant, endTime: Instant): Double? {
        val permission = HealthPermission.getReadPermission(DistanceRecord::class)
        return try {
            if (permission !in client.permissionController.getGrantedPermissions()) return null
            largestSingleSource(client, DistanceRecord.DISTANCE_TOTAL, startTime, endTime, "distance", compare = { it.inMeters })
                ?.inMeters?.takeIf { it > 0 }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("HealthConnectManager", "Reading distance failed", e)
            null
        }
    }

    /** [metric] for the day from a single app, chosen by [pickSource]. Throws if the first read fails. */
    private suspend fun <T : Any> largestSingleSource(
        client: HealthConnectClient,
        metric: AggregateMetric<T>,
        startTime: Instant,
        endTime: Instant,
        label: String,
        compare: (T) -> Double
    ): T? {
        val range = TimeRangeFilter.between(startTime, endTime)
        val merged = client.aggregate(AggregateRequest(setOf(metric), range))
        val origins = merged.dataOrigins
        if (origins.size <= 1) {
            android.util.Log.i("HealthConnectManager", "$label $startTime: ${merged[metric]} from ${origins.map { it.packageName }}")
            return merged[metric]
        }
        val perApp = origins.map { origin -> origin.packageName to readFrom(client, metric, range, origin) }
        android.util.Log.i("HealthConnectManager", "$label $startTime: merged=${merged[metric]} " + perApp.joinToString { "${it.first}=${it.second}" })
        return pickSource(perApp, compare) ?: merged[metric]
    }

    private suspend fun <T : Any> readFrom(client: HealthConnectClient, metric: AggregateMetric<T>, range: TimeRangeFilter, origin: DataOrigin): T? =
        try {
            client.aggregate(AggregateRequest(setOf(metric), range, setOf(origin)))[metric]
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
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

    companion object {
        /**
         * Apps of watches and bands. Each already merges its own watch and phone steps without
         * counting twice, so when one has data it beats Google Fit and the phone's own counter.
         */
        internal val WEARABLE_APPS = setOf(
            "com.sec.android.app.shealth", // Samsung Health (Galaxy Watch)
            "com.fitbit.FitbitMobile", // Fitbit and Pixel Watch
            "com.garmin.android.apps.connectmobile",
            "com.ouraring.oura",
            "com.withings.wiscale2",
            "com.huawei.health",
            "com.mi.health",
            "com.xiaomi.wearable",
            "com.huami.watch.hmwatchmanager", // Zepp / Amazfit
            "fi.polar.polarflow",
            "com.coros.coros",
            "com.whoop.android"
        )

        /**
         * One app's value, skipping apps whose read failed or gave nothing: the largest watch app
         * if any has data, otherwise the largest of the rest.
         */
        internal fun <T : Any> pickSource(perApp: List<Pair<String, T?>>, compare: (T) -> Double): T? {
            val usable = perApp.mapNotNull { (app, value) -> value?.takeIf { compare(it) > 0 }?.let { app to it } }
            val wearable = usable.filter { it.first in WEARABLE_APPS }
            return (wearable.ifEmpty { usable }).maxByOrNull { compare(it.second) }?.second
        }
    }
}
