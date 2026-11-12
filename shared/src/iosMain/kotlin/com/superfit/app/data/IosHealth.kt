package com.superfit.app.data

import com.superfit.app.domain.AppleWorkoutTypes
import com.superfit.app.domain.BodyValue
import com.superfit.app.domain.ScaleReading
import com.superfit.app.domain.SleepNight
import com.superfit.app.domain.SleepSpan
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Apple Health, read by the iPhone's Swift code (HealthKit, HealthKitBridge.swift). Swift only
 * answers plain questions over a time range in epoch milliseconds; which days, how sleep becomes a
 * night and what gets saved is decided in shared code. Each answer comes back once, on the main
 * thread, as a value or an error's description.
 */
interface IosHealth {
    fun isAvailable(): Boolean

    /** Shows Apple's access sheet for steps, active energy, walking distance and sleep (read only). */
    fun requestAccess(done: (String?) -> Unit)

    /** [kind] is "steps", "activeKcal" or "distanceM". The total, or null when Apple Health has none. */
    fun sum(kind: String, startMs: Long, endMs: Long, done: (Double?, String?) -> Unit)

    /** Asleep stretches as start, end, start, end… (in bed when nothing says asleep). */
    fun sleepSpans(startMs: Long, endMs: Long, done: (List<Long>?, String?) -> Unit)

    /** Workouts that started between two times, from every app and watch, newest first. */
    fun workouts(startMs: Long, endMs: Long, done: (List<IosWorkout>?, String?) -> Unit)

    /** [kind] is "weightKg", "bodyFatPct" (0 to 100) or "leanKg": every sample since [sinceMs]. */
    fun bodySamples(kind: String, sinceMs: Long, done: (List<IosBodySample>?, String?) -> Unit)
}

/** One weigh-in or body measurement as Apple Health holds it, with the name of the app or scale that saved it. */
class IosBodySample(val id: String, val timeMs: Long, val value: Double, val sourceName: String)

/**
 * One Apple Health workout as Swift reads it. [appleType] is HKWorkoutActivityType's raw value;
 * [indoor] is true for indoor runs, rides and rows and for pool swims. Values Apple Health
 * doesn't have are 0.
 */
class IosWorkout(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val appleType: Long,
    val indoor: Boolean,
    val startMs: Long,
    val endMs: Long,
    val distanceMeters: Double,
    val activeKcal: Double,
    val avgHeartRate: Double
)

class IosHealthException(message: String) : Exception(message)

/** Shared code's view of Apple Health ([HealthSource]) on top of the Swift reader. */
@OptIn(ExperimentalTime::class)
class IosHealthSource(private val ios: IosHealth, private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) : HealthSource {
    override fun isAvailable(): Boolean = ios.isAvailable()

    override suspend fun requestAccess(): Boolean = suspendCancellableCoroutine { c ->
        ios.requestAccess { error -> if (error != null) c.resumeWithException(IosHealthException(error)) else c.resume(true) }
    }

    override suspend fun readActivity(date: LocalDate): ActivityTelemetryEntity? {
        val (start, end) = range(date)
        // A failed steps read gives nothing to save, so a saved day keeps its count.
        val steps = sum("steps", start, end) ?: return null
        val kcal = sum("activeKcal", start, end) ?: 0.0
        return ActivityTelemetryEntity(date = date.toString(), steps = steps.toInt(), activeCalories = kcal)
    }

    override suspend fun readDistanceMeters(date: LocalDate): Double? {
        val (start, end) = range(date)
        return sum("distanceM", start, end)
    }

    override suspend fun readSleep(date: LocalDate): SleepTelemetryEntity? {
        // From the start of the day before, so a night that began before midnight is all there.
        val start = date.minus(DatePeriod(days = 1)).atStartOfDayIn(zone()).toEpochMilliseconds()
        val end = range(date).second
        val flat = suspendCancellableCoroutine<List<Long>?> { c ->
            ios.sleepSpans(start, end) { spans, error ->
                if (error != null) c.resumeWithException(IosHealthException(error)) else c.resume(spans)
            }
        } ?: return null
        val spans = flat.chunked(2).filter { it.size == 2 }.map { SleepSpan(it[0], it[1]) }
        return SleepNight.summarise(spans, date, zone())
    }

    override suspend fun readWorkouts(fromMs: Long, toMs: Long, bmr: Double): List<HealthWorkout>? {
        val workouts = suspendCancellableCoroutine<List<IosWorkout>?> { c ->
            ios.workouts(fromMs, toMs) { list, error ->
                if (error != null) c.resumeWithException(IosHealthException(error)) else c.resume(list)
            }
        } ?: return null
        return workouts.map { HealthWorkout(AppleWorkouts.row(it, bmr), it.sourceName) }
    }

    override suspend fun readBody(sinceMs: Long): BodySamples? {
        val weights = samples("weightKg", sinceMs) ?: return null
        // Body fat and lean mass only add detail; a refused read leaves them out.
        val fat = runCatching { samples("bodyFatPct", sinceMs) }.getOrNull().orEmpty()
        val lean = runCatching { samples("leanKg", sinceMs) }.getOrNull().orEmpty()
        return BodySamples(
            weights = weights.map { ScaleReading(id = it.id, timeMs = it.timeMs, weightKg = it.value, source = it.sourceName) },
            bodyFat = fat.map { BodyValue(it.timeMs, it.sourceName, it.value) },
            lean = lean.map { BodyValue(it.timeMs, it.sourceName, it.value) }
        )
    }

    private suspend fun samples(kind: String, sinceMs: Long): List<IosBodySample>? = suspendCancellableCoroutine { c ->
        ios.bodySamples(kind, sinceMs) { list, error ->
            if (error != null) c.resumeWithException(IosHealthException(error)) else c.resume(list)
        }
    }

    private suspend fun sum(kind: String, start: Long, end: Long): Double? = suspendCancellableCoroutine { c ->
        ios.sum(kind, start, end) { value, error ->
            if (error != null) c.resumeWithException(IosHealthException(error)) else c.resume(value)
        }
    }

    private fun range(date: LocalDate): Pair<Long, Long> =
        date.atStartOfDayIn(zone()).toEpochMilliseconds() to date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone()).toEpochMilliseconds()
}

/** Apple Health workouts in the shape Android's Health Connect import saves. */
object AppleWorkouts {
    private const val DAY_MS = 24.0 * 3600 * 1000

    fun row(w: IosWorkout, bmr: Double): ImportedWorkoutEntity {
        // Shared code takes total calories and removes the resting part (BMR over the workout's
        // length) itself; Apple Health gives active calories, so that same part is added first and
        // the exercise calories shown are exactly what the watch or app recorded.
        val resting = bmr * (w.endMs - w.startMs) / DAY_MS
        return ImportedWorkoutEntity(
            id = w.id,
            sourcePackage = w.sourceId,
            exerciseType = AppleWorkoutTypes.exerciseType(w.appleType, w.indoor),
            startTime = w.startMs,
            endTime = w.endMs,
            distanceMeters = w.distanceMeters.takeIf { it > 0 },
            totalKcal = w.activeKcal.takeIf { it > 0 }?.plus(resting),
            avgHeartRate = w.avgHeartRate.takeIf { it > 0 }?.let { kotlin.math.round(it).toLong() },
            detailsRead = true
        )
    }
}
