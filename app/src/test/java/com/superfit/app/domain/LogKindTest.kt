package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogKindTest {

    @Test
    fun `the food and activity mics skip the split`() {
        assertEquals(RoutedLog(food = "whey with 200 ml milk", exercise = ""), LogKind.MEAL.presplit("whey with 200 ml milk"))
        assertEquals(RoutedLog(food = "", exercise = "ran 5k"), LogKind.WORKOUT.presplit("ran 5k"))
    }

    @Test
    fun `the widget mic still splits food and exercise`() {
        assertNull(LogKind.ANY.presplit("poha and a walk"))
    }
}
