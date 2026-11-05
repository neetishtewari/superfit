package com.superfit.app.domain

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

class LocalNamesTest {
    private val before = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(before)

    @Test
    fun namesFollowThePhoneLanguage() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Saturday", localWeekdayName(DayOfWeek.SATURDAY))
        assertEquals("October", localMonthName(Month.OCTOBER))
        Locale.setDefault(Locale.GERMAN)
        assertEquals("Samstag", localWeekdayName(DayOfWeek.SATURDAY))
        assertEquals("Oktober", localMonthName(Month.OCTOBER))
    }
}
