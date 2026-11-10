package com.superfit.app.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

/**
 * Dev app check for route share cards: for each recent workout, does the app that recorded it
 * share a GPS route (and heights, and a climb total) with Health Connect? Reads only; nothing is saved.
 */
object RouteCheck {
    const val ROUTES_PERMISSION = "android.permission.health.READ_EXERCISE_ROUTES"
    const val ELEVATION_PERMISSION = "android.permission.health.READ_ELEVATION_GAINED"
    val permissions = setOf(ROUTES_PERMISSION, ELEVATION_PERMISSION, WorkoutImporter.EXERCISE_PERMISSION)

    enum class RouteState { HAS_ROUTE, NEEDS_OK, NO_ROUTE }

    data class Row(
        val sessionId: String,
        val start: Instant,
        val type: String,
        val sourcePackage: String,
        val state: RouteState,
        val route: RouteSummary?,
        val climbMetres: Double?
    )

    data class RouteSummary(val points: Int, val km: Double, val withHeight: Int, val climbFromHeights: Double?)

    suspend fun run(client: HealthConnectClient, days: Long = 30, now: Instant = Instant.now()): Result<List<Row>> = try {
        val granted = client.permissionController.getGrantedPermissions()
        val sessions = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now))
        ).records.sortedByDescending { it.startTime }
        Result.success(
            sessions.map { s ->
                val (state, summary) = when (val r = s.exerciseRouteResult) {
                    is ExerciseRouteResult.Data -> RouteState.HAS_ROUTE to summarize(r.exerciseRoute)
                    is ExerciseRouteResult.ConsentRequired -> RouteState.NEEDS_OK to null
                    else -> RouteState.NO_ROUTE to null
                }
                val climb = if (ELEVATION_PERMISSION in granted) climb(client, s) else null
                Row(
                    sessionId = s.metadata.id,
                    start = s.startTime,
                    type = typeName(s.exerciseType),
                    sourcePackage = s.metadata.dataOrigin.packageName,
                    state = state,
                    route = summary,
                    climbMetres = climb
                )
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun climb(client: HealthConnectClient, s: ExerciseSessionRecord): Double? = try {
        client.aggregate(
            AggregateRequest(
                metrics = setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime),
                dataOriginFilter = setOf(DataOrigin(s.metadata.dataOrigin.packageName))
            )
        )[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun typeName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ride"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open-water swim"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool swim"
        else -> "Workout type $type"
    }

    fun summarize(route: ExerciseRoute): RouteSummary {
        val pts = route.route
        var metres = 0.0
        for (i in 1 until pts.size) metres += distanceMetres(pts[i - 1].latitude, pts[i - 1].longitude, pts[i].latitude, pts[i].longitude)
        val heights = pts.mapNotNull { it.altitude?.inMeters }
        val climb = if (heights.size < 2) null else heights.zipWithNext { a, b -> (b - a).coerceAtLeast(0.0) }.sum()
        return RouteSummary(points = pts.size, km = metres / 1000.0, withHeight = heights.size, climbFromHeights = climb)
    }

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
