package com.superfit.app.domain

import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

// Java's own names, the same call the app made before these moved to shared code.
actual fun localWeekdayName(day: DayOfWeek): String = java.time.DayOfWeek.of(day.isoDayNumber).getDisplayName(TextStyle.FULL, Locale.getDefault())

actual fun localMonthName(month: Month): String = java.time.Month.of(month.number).getDisplayName(TextStyle.FULL, Locale.getDefault())
