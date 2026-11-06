package com.superfit.app.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

// The iPhone's own day and month names, in the phone's language.
private fun formatter() = NSDateFormatter().apply { locale = NSLocale.currentLocale }

// The iPhone's list starts on Sunday; ISO numbers run Monday 1 to Sunday 7.
actual fun localWeekdayName(day: DayOfWeek): String = formatter().weekdaySymbols[day.isoDayNumber % 7] as String

actual fun localMonthName(month: Month): String = formatter().monthSymbols[month.number - 1] as String
