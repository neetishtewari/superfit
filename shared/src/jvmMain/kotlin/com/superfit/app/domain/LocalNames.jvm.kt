package com.superfit.app.domain

import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

// On the JVM, kotlinx-datetime's DayOfWeek and Month are Java's own, so this is the old call.
actual fun localWeekdayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

actual fun localMonthName(month: Month): String = month.getDisplayName(TextStyle.FULL, Locale.getDefault())
