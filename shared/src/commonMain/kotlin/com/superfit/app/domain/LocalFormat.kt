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

    /** "8,432", grouped the way the phone groups numbers. */
    fun integer(n: Long): String

    fun uppercase(text: String): String

    fun lowercase(text: String): String
}
