package com.superfit.app.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Dates, times and numbers written in the phone's own language and style, for share cards.
 * Each app writes them its own way (Java's formatters on Android, the iPhone's formatters there).
 */
expect object LocalFormat {
    /** "Wed, 4 Nov" (EEE, d MMM). */
    fun dayLine(date: LocalDate): String

    /** "4 Nov" (d MMM). */
    fun dayMonth(date: LocalDate): String

    /** "November 2026" (MMMM yyyy). */
    fun monthYear(date: LocalDate): String

    /** "6:05 am" (h:mm a). */
    fun clock(time: LocalTime): String

    /** The time the way the phone shows it, 12 or 24 hour ("6:05 am" or "06:05"). */
    fun shortClock(time: LocalTime): String

    /** "8,432", grouped the way the phone groups numbers. */
    fun integer(n: Long): String

    /** [x] with exactly [digits] decimals in the phone's style ("79.5", or "79,5" in Portuguese), not grouped. Same as String.format("%.1f"). */
    fun decimal(x: Double, digits: Int): String

    fun uppercase(text: String): String

    fun lowercase(text: String): String
}
