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
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.ShareCard
import com.superfit.app.domain.ShareCards
import com.superfit.app.domain.WorkoutHeart
import com.superfit.app.domain.WorkoutMoments
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fills in a workout card when its share screen opens: the heart-rate line from Health Connect
 * (read now, never stored) and the one line that stands out against the user's earlier workouts.
 * Anything that can't be read is left out and the card stays as it was.
 */
object WorkoutCardData {
    private const val TAG = "WorkoutCardData"
    private const val MAX_SAMPLES = 20_000

    suspend fun enrich(context: Context, card: ShareCard.Activity): ShareCard.Activity {
        val workout = card.workout ?: return card
        val repository = DataRepository(context.applicationContext, HealthConnectManager(context.applicationContext))
        val profile = try {
            repository.getProfile()
        } catch (e: Exception) {
            null
        }
        val primary = workout.merged.primary
        val samples = readHeartRate(repository.healthConnectManager.healthConnectClient, primary.sourcePackage, primary.startTime, primary.endTime)
        val heart = samples?.let { WorkoutHeart.line(it, primary.startTime, primary.endTime, profile?.age) }
        val moment = try {
            val bmr = profile?.let { PhysiologyEngine.calculateBmr(it) } ?: 0.0
            WorkoutMoments.line(workout, repository.getImportedWorkoutViews(bmr))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't compare with earlier workouts", e)
            null
        }
        return ShareCards.withDetails(card, heart, moment)
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
