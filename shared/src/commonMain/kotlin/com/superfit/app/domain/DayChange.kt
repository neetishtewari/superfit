package com.superfit.app.domain

import kotlinx.datetime.LocalDateTime

/** When the phone's date next changes, so Today can move to the new day by itself. */
object DayChange {
    private const val DAY_MS = 24L * 3600 * 1000

    /** Milliseconds from [now] to the next midnight, plus a second so the new date has begun. */
    fun millisToNextDay(now: LocalDateTime): Long {
        val sinceMidnight = ((now.hour * 60L + now.minute) * 60 + now.second) * 1000 + now.nanosecond / 1_000_000
        return (DAY_MS - sinceMidnight).coerceAtLeast(0) + 1000
    }
}
