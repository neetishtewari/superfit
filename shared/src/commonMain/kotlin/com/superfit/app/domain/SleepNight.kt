package com.superfit.app.domain

import com.superfit.app.data.SleepTelemetryEntity
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One stretch of sleep a health app recorded, in epoch milliseconds. */
data class SleepSpan(val startMs: Long, val endMs: Long)

/**
 * Turns the sleep stretches the iPhone's health app holds into one night, the way Android's Health
 * Connect reading does: the sleep that ended on [date] (the night before plus any nap that day),
 * with deep sleep estimated at a fifth and 8 hours counted as fully rested.
 *
 * Apple Health keeps sleep as many short pieces (core, deep, REM), often from the watch and a sleep
 * app at once, so overlapping pieces are joined before they're added up and nothing is counted twice.
 */
object SleepNight {
    private const val FULL_NIGHT_SECONDS = 8 * 3600.0
    private const val DEEP_SHARE = 0.2

    // Pieces less than this far apart belong to the same sleep, so a night that started before
    // midnight still counts as one sleep ending in the morning.
    private const val SAME_SLEEP_GAP_MS = 2 * 3600 * 1000L

    fun summarise(spans: List<SleepSpan>, date: LocalDate, zone: TimeZone): SleepTelemetryEntity? {
        val sleeps = group(merge(spans))
        val ended = sleeps.filter { day(it.last().endMs, zone) == date }
        val seconds = ended.flatten().sumOf { (it.endMs - it.startMs) / 1000 }
        if (seconds <= 0) return null
        return SleepTelemetryEntity(
            date = date.toString(),
            sleepDurationSeconds = seconds,
            deepSleepDurationSeconds = (seconds * DEEP_SHARE).toLong(),
            readinessScore = (seconds / FULL_NIGHT_SECONDS * 100).toInt().coerceIn(0, 100)
        )
    }

    /** Joins pieces that overlap or touch, so two apps recording the same night count it once. */
    internal fun merge(spans: List<SleepSpan>): List<SleepSpan> {
        val merged = mutableListOf<SleepSpan>()
        for (span in spans.filter { it.endMs > it.startMs }.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && span.startMs <= last.endMs) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, span.endMs))
            } else {
                merged += span
            }
        }
        return merged
    }

    /** Groups joined pieces with short wakes between them into whole sleeps. */
    private fun group(merged: List<SleepSpan>): List<List<SleepSpan>> {
        val sleeps = mutableListOf<MutableList<SleepSpan>>()
        for (span in merged) {
            val current = sleeps.lastOrNull()
            if (current != null && span.startMs - current.last().endMs < SAME_SLEEP_GAP_MS) current += span else sleeps += mutableListOf(span)
        }
        return sleeps
    }

    @OptIn(ExperimentalTime::class)
    private fun day(ms: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
}
