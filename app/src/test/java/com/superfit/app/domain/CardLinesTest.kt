package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardLinesTest {

    private val facts = "Workout: Run, 52 minutes\nDistance: 8.66 km\nPace: 6:00 per km\nAverage heart rate: 148 bpm"

    @Test
    fun keepsALineThatUsesOnlyGivenNumbers() {
        assertEquals("52 minutes at 6:00 a km. Lovely steady work.", CardLines.accept("\"52 minutes at 6:00 a km. **Lovely** steady work.\"", facts, 26))
    }

    @Test
    fun dropsALineWithAMadeUpNumber() {
        assertNull(CardLines.accept("Your 5th run this week at 148 bpm!", facts, 26))
    }

    @Test
    fun dropsALineThatIsTooLongOrEmpty() {
        assertNull(CardLines.accept("word ".repeat(30), facts, 26))
        assertNull(CardLines.accept("  ", facts, 26))
        assertNull(CardLines.accept(null, facts, 26))
    }

    @Test
    fun roastEndsOnSomethingTrue() {
        assertEquals("Still: fastest 5 km in a month.", CardLines.fairLine("Fastest 5 km in a month", 1))
        assertEquals("Still: 3rd workout this week.", CardLines.fairLine(null, 3))
        assertEquals("Still showed up. That counts.", CardLines.fairLine(null, 1))
    }

    @Test
    fun walkCardsCountWalksNotWorkouts() {
        assertEquals("Still: 3rd walk today.", CardLines.fairLine(null, null, walksToday = 3))
        assertEquals("Still showed up. That counts.", CardLines.fairLine(null, null, walksToday = 1))
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(CardLines::ordinal))
    }

    @Test
    fun roastMeUsersGetTheSupportiveCoachLine() {
        assertEquals(CoachStyle.SUPPORTIVE, CardLines.coachStyle(CoachStyle.ROAST))
        assertEquals(CoachStyle.entries.last { it != CoachStyle.ROAST }, CardLines.coachStyle(CoachStyle.entries.last { it != CoachStyle.ROAST }))
    }
}
