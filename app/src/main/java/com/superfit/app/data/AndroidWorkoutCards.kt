package com.superfit.app.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.superfit.app.domain.HeartSample
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** Android's side of a workout card: Health Connect's heart rate, and the shared [WorkoutCardData] built for this phone. */
object AndroidWorkoutCards {
    private const val TAG = "WorkoutCardData"
    private const val MAX_SAMPLES = 20_000

    fun data(context: Context): WorkoutCardData {
        val app = context.applicationContext
        val repository = DataRepository(app, HealthConnectManager(app))
        return WorkoutCardData(
            log = repository.log,
            userPrefs = AndroidPrefs(getUserSharedPrefs(app)),
            share = ShareCardPrefs.of(app),
            importedViews = repository::getImportedWorkoutViews,
            heartRate = { pkg, start, end -> readHeartRate(repository.healthConnectManager.healthConnectClient, pkg, start, end) }
        )
    }

    /** Heart-rate readings the workout's own app recorded during it; null when not allowed or on failure. */
    private suspend fun readHeartRate(client: HealthConnectClient?, sourcePackage: String, start: Long, end: Long): List<HeartSample>? {
        client ?: return null
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return null
            val out = mutableListOf<HeartSample>()
            var token: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)),
                        dataOriginFilter = setOf(DataOrigin(sourcePackage)),
                        pageToken = token
                    )
                )
                response.records.forEach { record ->
                    record.samples.forEach { out += HeartSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) }
                }
                token = response.pageToken
            } while (token != null && out.size < MAX_SAMPLES)
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading heart rate failed", e)
            null
        }
    }
}
