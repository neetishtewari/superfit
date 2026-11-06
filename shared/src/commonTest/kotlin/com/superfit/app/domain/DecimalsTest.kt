package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalsTest {

    @Test
    fun matchesJavasOneDecimalFormat() {
        assertEquals("63.9", Decimals.one(63.9))
        assertEquals("64.0", Decimals.one(63.95))
        assertEquals("10.0", Decimals.one(9.96))
        assertEquals("0.0", Decimals.one(0.04))
        assertEquals("0.3", Decimals.one(0.25))
        assertEquals("2.0", Decimals.one(2.0))
        assertEquals("1.5", Decimals.one(1.5))
        assertEquals("-0.0", Decimals.one(-0.04))
        assertEquals("-1.3", Decimals.one(-1.25))
        assertEquals("120.0", Decimals.one(120.0))
        assertEquals("12345678.9", Decimals.one(12345678.9))
    }

    @Test
    fun otherDecimalCounts() {
        assertEquals("3", Decimals.fixed(2.5, 0))
        assertEquals("2", Decimals.fixed(2.49, 0))
        assertEquals("0.13", Decimals.fixed(0.125, 2))
        assertEquals("1.50", Decimals.fixed(1.5, 2))
    }

    @Test
    fun groupsThousandsWithCommas() {
        assertEquals("0", Decimals.grouped(0))
        assertEquals("999", Decimals.grouped(999))
        assertEquals("1,000", Decimals.grouped(1000))
        assertEquals("23,410", Decimals.grouped(23410))
        assertEquals("1,234,567", Decimals.grouped(1234567))
        assertEquals("-12,000", Decimals.grouped(-12000))
    }
}
