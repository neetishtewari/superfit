package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoastCardTest {

    private val protein = CardStat("PROTEIN", "64", "g", "of 120 g goal")
    private val diet = CardStat("DIET QUALITY", "71", "/100")
    private val steps = CardStat("STEPS", "8,432")
    private val sleep = CardStat("SLEEP", "10h 20m", note = "last night")
    private val pool = listOf(protein, diet, steps, sleep)

    @Test
    fun aSleepRoastShowsSleepNotProtein() {
        val tiles = RoastCard.matching("Ten hours in bed and still hit snooze. Set one alarm tonight.", pool)
        assertEquals(listOf(sleep), tiles)
    }

    @Test
    fun numbersComeInTheOrderTheRoastBringsThemUp() {
        val tiles = RoastCard.matching("Your sofa got more steps than you. Protein took the day off too.", pool)
        assertEquals(listOf(steps, protein), tiles)
    }

    @Test
    fun aQuotedNumberCountsAsATopic() {
        assertEquals(listOf(steps), RoastCard.matching("8432 of anything is not a personality.", pool))
    }

    @Test
    fun atMostTwoAndNoneWhenNothingMatches() {
        assertEquals(2, RoastCard.matching("Slept in, skipped the walk, protein missing, snacks on repeat.", pool).size)
        assertTrue(RoastCard.matching("Bold choice. Brave, even.", pool).isEmpty())
        assertTrue(RoastCard.matching(null, pool).isEmpty())
    }

    @Test
    fun roastAndNumbersNeverBothOff() {
        val noNumbers = DayChip.toggle(DayChip.DEFAULTS, DayChip.NUMBERS)
        assertEquals(setOf(DayChip.ROAST), noNumbers)
        assertEquals(setOf(DayChip.ROAST), DayChip.toggle(noNumbers, DayChip.ROAST))
        assertEquals(setOf(DayChip.NUMBERS), DayChip.toggle(DayChip.DEFAULTS, DayChip.ROAST))
    }

    @Test
    fun aRoastCardWithTheRoastOffIsTheDayCard() {
        val day = ShareCard.Daily("Tue 4 Nov", 3, listOf(protein, diet), "Ten hours in bed.", roast = true, pool = pool)
        assertEquals(listOf(sleep), day.roastTiles)
        val off = day.copy(chips = setOf(DayChip.NUMBERS))
        assertNull(off.shownRoast)
        assertEquals(false, off.asDay.roast)
        assertNull(off.asDay.coachLine)
        assertTrue(day.copy(chips = setOf(DayChip.ROAST)).roastTiles.isEmpty())
    }

    @Test
    fun sleepJoinsThePoolButNotTheDayCard() {
        val card = ShareCards.daily(java.time.LocalDate.of(2026, 11, 4), 3, 2, 64.0, 120.0, 71, 8432, null, null, sleepSeconds = 37_200)
        assertTrue(card.tiles.none { it.label == "SLEEP" })
        assertEquals("10h 20m", card.pool.first { it.label == "SLEEP" }.value)
    }

    @Test
    fun aLongRoastIsTrimmedForAPhotoUntilTheShortOneIsWritten() {
        val long = "You logged three breakfasts and called it brunch. " + "The rest is a long story about biscuits and regret that goes on and on. ".repeat(3)
        val short = ShareCards.photoRoast(long)
        assertTrue(short.length <= RoastCard.PHOTO_MAX_CHARS)
        assertTrue(short.startsWith("You logged three breakfasts"))
    }
}
