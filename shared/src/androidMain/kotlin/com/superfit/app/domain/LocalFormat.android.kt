package com.superfit.app.domain

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime

// The same Java formatters, in the phone's language, that the app used before this moved to shared code.
actual object LocalFormat {
    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, Locale.getDefault())

    actual fun dayLine(date: LocalDate): String = date.toJavaLocalDate().format(pattern("EEE, d MMM"))

    actual fun dayMonth(date: LocalDate): String = date.toJavaLocalDate().format(pattern("d MMM"))

    actual fun monthYear(date: LocalDate): String = date.toJavaLocalDate().format(pattern("MMMM yyyy"))

    actual fun clock(time: LocalTime): String = time.toJavaLocalTime().format(pattern("h:mm a"))

    actual fun integer(n: Long): String = NumberFormat.getIntegerInstance().format(n)

    actual fun uppercase(text: String): String = text.uppercase(Locale.getDefault())

    actual fun lowercase(text: String): String = text.lowercase(Locale.getDefault())
}
