package com.superfit.app.data

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.superfit.app.domain.WorkoutMerge
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the workouts other apps share through Health Connect (sessions plus each one's distance,
 * total calories and average heart rate) into the imported_workouts table. The first import goes
 * 90 days back; later ones re-read the last week so edits and late syncs are picked up.
 */
object WorkoutImporter {
    private const val TAG = "WorkoutImporter"
    private const val FIRST_IMPORT_DAYS = 90L
    private const val REFRESH_DAYS = 7L
    private const val MAX_SESSIONS = 2000

    const val HISTORY_PERMISSION = "android.permission.health.READ_HEALTH_DATA_HISTORY"
    val EXERCISE_PERMISSION = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    /** Asked for when the user connects. Only the exercise one is required; the rest add detail. */
    val permissions: Set<String> = setOf(
        EXERCISE_PERMISSION,
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HISTORY_PERMISSION
    )

    // The app-open import and the background sync must not run over each other.
    private val lock = Mutex()

    /** Returns how many workouts were read, or null when nothing could be read. */
    suspend fun import(
        client: HealthConnectClient,
        dao: ImportedWorkoutDao,
        prefs: WorkoutImportPrefs,
        now: Instant = Instant.now()
    ): Int? = lock.withLock {
        try {
            val granted = client.permissionController.getGrantedPermissions()
            if (EXERCISE_PERMISSION !in granted) return@withLock null

            val firstImport = prefs.lastImportMillis == 0L
            val from = now.minus(Duration.ofDays(if (firstImport) FIRST_IMPORT_DAYS else REFRESH_DAYS))
            val sessions = readSessions(client, from, now) ?: return@withLock null

            val known = dao.getBetween(from.toEpochMilli(), now.toEpochMilli()).associateBy { it.id }
            val reader = DetailReader(client, granted)
            val rows = sessions.mapNotNull { session ->
                val row = ImportedWorkoutEntity(
                    id = session.metadata.id,
                    sourcePackage = session.metadata.dataOrigin.packageName,
                    exerciseType = session.exerciseType,
                    startTime = session.startTime.toEpochMilli(),
                    endTime = session.endTime.toEpochMilli()
                )
                if (!WorkoutMerge.isValid(row)) return@mapNotNull null
                val previous = known[row.id]
                if (previous != null && previous.detailsRead && previous.endTime == row.endTime) {
                    previous.copy(exerciseType = row.exerciseType, startTime = row.startTime)
                } else {
                    reader.read(session, row)
                }
            }
            // The user may have turned the import off while this one was running.
            if (!prefs.enabled) return@withLock null
            dao.upsertAll(rows)
            // Workouts deleted in the other app disappear here too (only when every session was read).
            val gone = known.keys - sessions.map { it.metadata.id }.toSet()
            if (gone.isNotEmpty() && sessions.size < MAX_SESSIONS) dao.deleteByIds(gone.toList())
            // If Health Connect stopped answering partway, the next import goes back as far again
            // and fills in only what's missing.
            if (!reader.stopped) prefs.lastImportMillis = now.toEpochMilli()
            Log.i(TAG, "Imported ${rows.size} workouts from $from (first=$firstImport, complete=${!reader.stopped})")
            rows.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Workout import failed", e)
            null
        }
    }

    private suspend fun readSessions(client: HealthConnectClient, from: Instant, to: Instant): List<ExerciseSessionRecord>? {
        val sessions = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null
        return try {
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken
                    )
                )
                sessions += response.records
                pageToken = response.pageToken
            } while (pageToken != null && sessions.size < MAX_SESSIONS)
            sessions
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading workout sessions failed", e)
            null
        }
    }

    /**
     * Reads distance, calories and heart rate recorded by the same app during each session, one
     * metric per call so a missing permission or one broken record only blanks that value. After
     * many failures in a row (usually Health Connect's rate limit) it stops asking, and sessions
     * read after that are saved without details to be filled in next time.
     */
    private class DetailReader(private val client: HealthConnectClient, private val granted: Set<String>) {
        private var failuresInARow = 0
        private var logged = false
        val stopped: Boolean get() = failuresInARow >= MAX_FAILURES_IN_A_ROW

        suspend fun read(session: ExerciseSessionRecord, row: ImportedWorkoutEntity): ImportedWorkoutEntity {
            if (stopped) return row
            val range = TimeRangeFilter.between(session.startTime, session.endTime)
            val origin = setOf(DataOrigin(row.sourcePackage))
            suspend fun <T : Any> metric(metric: AggregateMetric<T>, permission: String): T? {
                if (permission !in granted || stopped) return null
                return try {
                    client.aggregate(AggregateRequest(setOf(metric), range, origin))[metric]
                        .also { failuresInARow = 0 }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failuresInARow++
                    if (!logged) Log.w(TAG, "Reading workout details failed", e)
                    logged = true
                    null
                }
            }
            val distance = metric(DistanceRecord.DISTANCE_TOTAL, HealthPermission.getReadPermission(DistanceRecord::class))
            val kcal = metric(TotalCaloriesBurnedRecord.ENERGY_TOTAL, HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
            val heartRate = metric(HeartRateRecord.BPM_AVG, HealthPermission.getReadPermission(HeartRateRecord::class))
            return row.copy(
                distanceMeters = distance?.inMeters?.takeIf { it > 0 },
                totalKcal = kcal?.inKilocalories?.takeIf { it > 0 },
                avgHeartRate = heartRate?.takeIf { it > 0 },
                detailsRead = !stopped
            )
        }

        private companion object {
            const val MAX_FAILURES_IN_A_ROW = 10
        }
    }
}
