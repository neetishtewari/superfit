package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MonthlyRecapFormatTest {

    @Test
    fun paceIsMinutesAndTwoDigitSeconds() {
        assertEquals("6:42", MonthlyRecap.pace(402))
        assertEquals("5:05", MonthlyRecap.pace(305))
        assertEquals("10:00", MonthlyRecap.pace(600))
    }

    @Test
    fun weightIsWholeWhenCloseElseOneDecimal() {
        assertEquals("3", RecapWriter.fmt(3.02))
        assertEquals("1.4", RecapWriter.fmt(1.4))
    }
}
