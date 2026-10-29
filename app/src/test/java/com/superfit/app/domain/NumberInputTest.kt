package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {

    @Test
    fun `decimals are capped at two places`() {
        assertEquals("63.9", NumberInput.clean("63.9", allowDecimals = true))
        assertEquals("63.95", NumberInput.clean("63.95", allowDecimals = true))
        assertNull(NumberInput.clean("63.951", allowDecimals = true))
    }

    @Test
    fun `comma becomes a dot and letters or a second dot are refused`() {
        assertEquals("63.5", NumberInput.clean("63,5", allowDecimals = true))
        assertNull(NumberInput.clean("6a", allowDecimals = true))
        assertNull(NumberInput.clean("6.3.1", allowDecimals = true))
    }

    @Test
    fun `whole-number boxes take digits only, and empty is allowed`() {
        assertEquals("28", NumberInput.clean("28", allowDecimals = false))
        assertNull(NumberInput.clean("28.5", allowDecimals = false))
        assertEquals("", NumberInput.clean("", allowDecimals = false))
    }
}
