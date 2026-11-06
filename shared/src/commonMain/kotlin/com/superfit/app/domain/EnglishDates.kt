package com.superfit.app.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Dates and times written in English whatever the phone's language, for AI prompts and the
 * English text around them. Matches Java's patterns with Locale.ENGLISH.
 */
object EnglishDates {

    private fun title(name: String) = name.lowercase().replaceFirstChar { it.uppercase() }

    /** "Saturday" (EEEE) */
    fun weekday(date: LocalDate): String = title(date.dayOfWeek.name)

    /** "Sat" (EEE) */
    fun weekdayShort(date: LocalDate): String = weekday(date).take(3)

    /** "10 Oct" (d MMM) */
    fun dayMonth(date: LocalDate): String = "${date.day} ${title(date.month.name).take(3)}"

    /** "Sat 10 Oct" (EEE d MMM) */
    fun short(date: LocalDate): String = "${weekdayShort(date)} ${dayMonth(date)}"

    /** "1:05 PM" (h:mm a) */
    fun time(t: LocalTime): String {
        val h = t.hour % 12
        return "${if (h == 0) 12 else h}:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "AM" else "PM"}"
    }
}
