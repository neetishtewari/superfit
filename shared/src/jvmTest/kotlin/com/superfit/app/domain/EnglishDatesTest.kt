package com.superfit.app.domain

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime

class EnglishDatesTest {

    private fun java(date: LocalDate, pattern: String) = date.toJavaLocalDate().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))

    @Test
    fun matchesJavasEnglishPatternsForAWholeYear() {
        var date = LocalDate(2026, 1, 1)
        repeat(366) {
            assertEquals(java(date, "EEE d MMM"), EnglishDates.short(date))
            assertEquals(java(date, "d MMM"), EnglishDates.dayMonth(date))
            assertEquals(java(date, "EEEE"), EnglishDates.weekday(date))
            assertEquals(java(date, "EEE"), EnglishDates.weekdayShort(date))
            date = LocalDate.fromEpochDays(date.toEpochDays() + 1)
        }
    }

    @Test
    fun twelveHourTimeMatchesJava() {
        val format = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        for (h in 0..23) {
            for (m in listOf(0, 5, 59)) {
                val t = LocalTime(h, m)
                assertEquals(t.toJavaLocalTime().format(format), EnglishDates.time(t))
            }
        }
    }
}
