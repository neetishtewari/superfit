package com.superfit.app.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {

    private fun at(date: String, hour: Int) =
        LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun countsDifferentDaysNotMealsOrStreaks() {
        val meals = listOf(
            at("2026-10-01", 8),
            at("2026-10-01", 13),
            at("2026-10-03", 9),
            at("2026-10-09", 19),
            at("2026-10-20", 7)
        )
        assertEquals(4, ReviewPrompt.daysWithMeals(meals, ZoneOffset.UTC))
    }

    @Test
    fun asksOnceMealsAreLoggedOnFiveDays() {
        assertFalse(ReviewPrompt.shouldAsk(mealDays = 4, alreadyAsked = false, otherPromptShowing = false))
        assertTrue(ReviewPrompt.shouldAsk(mealDays = 5, alreadyAsked = false, otherPromptShowing = false))
        assertTrue(ReviewPrompt.shouldAsk(mealDays = 40, alreadyAsked = false, otherPromptShowing = false))
    }

    @Test
    fun neverAsksTwice() {
        assertFalse(ReviewPrompt.shouldAsk(mealDays = 10, alreadyAsked = true, otherPromptShowing = false))
    }

    @Test
    fun waitsWhileANewBestIsOffered() {
        assertFalse(ReviewPrompt.shouldAsk(mealDays = 5, alreadyAsked = false, otherPromptShowing = true))
    }
}
