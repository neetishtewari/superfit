package com.superfit.app.ui.dashboard

import com.superfit.app.data.SleepTelemetryEntity
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Which day the Activity tab shows. Just after midnight the new day has no steps, calories or
 * sleep yet, so the tab looked empty. Until the person wakes up it keeps showing yesterday.
 *
 * Waking up is when the night's sleep reaches the phone: sleep apps save a night once it ends.
 * People without a sleep tracker never get that signal, so for them the day turns at 4 am. Nobody
 * stays on yesterday past midday, in case a night was never recorded.
 */
object ActivityDay {
    /** Without a sleep tracker, the new day starts here. */
    const val NO_TRACKER_HOUR = 4

    /** Everyone is on today from here, slept or not. */
    const val LATEST_HOUR = 12

    fun showYesterday(
        hour: Int,
        today: LocalDate,
        todaysSleep: SleepTelemetryEntity?,
        recentSleep: List<SleepTelemetryEntity>
    ): Boolean {
        if (todaysSleep != null || hour >= LATEST_HOUR) return false
        return tracksSleep(today, recentSleep) || hour < NO_TRACKER_HOUR
    }

    /** Whether any night in the week before [today] was saved. */
    fun tracksSleep(today: LocalDate, recentSleep: List<SleepTelemetryEntity>): Boolean {
        val weekAgo = today.minus(7, DateTimeUnit.DAY)
        return recentSleep.any { night ->
            val date = runCatching { LocalDate.parse(night.date) }.getOrNull()
            date != null && date >= weekAgo && date < today
        }
    }
}
