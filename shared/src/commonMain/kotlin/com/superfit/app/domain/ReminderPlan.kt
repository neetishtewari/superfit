package com.superfit.app.domain

import com.superfit.app.data.Prefs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** One evening reminder to put on the phone's notification schedule. */
data class ReminderNote(val id: String, val atEpochSeconds: Long, val title: String, val body: String)

/**
 * The evening reminder where the phone can't run Superfit at reminder time (iPhone), so the
 * reminders are written ahead and rewritten whenever today's log changes. The same rules and words
 * as Android's DailyReminderWorker: under 75% of the target logged gets "Complete your food log?",
 * a full day gets today's coach tip (or the short "Daily log complete" line). The coming days are
 * written as reminders too, so they still come if the app isn't opened.
 */
@OptIn(ExperimentalTime::class)
object ReminderPlan {
    // The same settings as Android's Settings › Daily reminder.
    const val KEY_ENABLED = "reminder_enabled"
    const val KEY_HOUR = "reminder_hour"
    const val KEY_MINUTE = "reminder_minute"
    const val DEFAULT_HOUR = 21
    const val DAYS_AHEAD = 7

    fun enabled(prefs: Prefs): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun hour(prefs: Prefs): Int = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
    fun minute(prefs: Prefs): Int = prefs.getInt(KEY_MINUTE, 0)

    /**
     * Reminders from [now] for [DAYS_AHEAD] days at [hour]:[minute] (today's only if that time is
     * still ahead). [coachTip] is today's tip from Home, if it has one.
     */
    fun notes(
        now: Instant,
        zone: TimeZone,
        today: LocalDate,
        hour: Int,
        minute: Int,
        eatenKcal: Int,
        targetKcal: Int,
        coachTip: String?,
        style: CoachStyle
    ): List<ReminderNote> = (0 until DAYS_AHEAD).mapNotNull { offset ->
        val day = today.plus(DatePeriod(days = offset))
        val at = LocalDateTime(day, LocalTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))).toInstant(zone)
        if (at <= now) return@mapNotNull null
        val (title, body) = when {
            offset > 0 -> style.logReminder(0, targetKcal, day.dayOfYear)
            eatenKcal < 0.75 * targetKcal -> style.logReminder(eatenKcal, targetKcal, day.dayOfYear)
            !coachTip.isNullOrBlank() -> style.tipTitle() to coachTip.replace("**", "")
            else -> style.completeDayFallback(eatenKcal)
        }
        ReminderNote("reminder-$day", at.epochSeconds, title, body)
    }
}
