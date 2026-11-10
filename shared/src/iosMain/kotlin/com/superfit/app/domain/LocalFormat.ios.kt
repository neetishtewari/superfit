package com.superfit.app.domain

import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

// The same patterns as Android, written by the iPhone's formatters in the phone's language.
@OptIn(ExperimentalTime::class)
actual object LocalFormat {
    private fun format(date: NSDate, pattern: String): String = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateFormat = pattern
    }.stringFromDate(date)

    private fun nsDate(date: LocalDate): NSDate =
        NSDate.dateWithTimeIntervalSince1970(date.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds.toDouble())

    actual fun dayLine(date: LocalDate): String = format(nsDate(date), "EEE, d MMM")

    actual fun dayMonth(date: LocalDate): String = format(nsDate(date), "d MMM")

    actual fun monthYear(date: LocalDate): String = format(nsDate(date), "MMMM yyyy")

    actual fun clock(time: LocalTime): String {
        val instant = LocalDateTime(LocalDate(2000, 1, 1), time).toInstant(TimeZone.currentSystemDefault())
        return format(NSDate.dateWithTimeIntervalSince1970(instant.epochSeconds.toDouble()), "h:mm a")
    }

    actual fun integer(n: Long): String = NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterDecimalStyle
        maximumFractionDigits = 0u
    }.stringFromNumber(NSNumber(longLong = n)) ?: n.toString()

    actual fun decimal(x: Double, digits: Int): String = NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = false
        roundingMode = NSNumberFormatterRoundHalfUp
        minimumFractionDigits = digits.toULong()
        maximumFractionDigits = digits.toULong()
    }.stringFromNumber(NSNumber(double = x)) ?: Decimals.fixed(x, digits)

    // Kotlin's own case rules; the same as the phone's for every language except Turkish dotted i.
    actual fun uppercase(text: String): String = text.uppercase()

    actual fun lowercase(text: String): String = text.lowercase()
}
