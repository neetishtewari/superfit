package com.superfit.app.data

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
}

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

    private suspend fun sum(kind: String, start: Long, end: Long): Double? = suspendCancellableCoroutine { c ->
        ios.sum(kind, start, end) { value, error ->
            if (error != null) c.resumeWithException(IosHealthException(error)) else c.resume(value)
        }
    }

    private fun range(date: LocalDate): Pair<Long, Long> =
        date.atStartOfDayIn(zone()).toEpochMilliseconds() to date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone()).toEpochMilliseconds()
}
