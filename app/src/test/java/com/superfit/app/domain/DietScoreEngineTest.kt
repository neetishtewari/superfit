package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietScoreEngineTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val zone = ZoneOffset.UTC

    private val homeCooked = MealTags(1, fried = false, addedSugar = false, vegFruitServings = 1.0, fiberG = 10.0, treat = false, foodType = "dal")
    private val burger = MealTags(3, fried = false, addedSugar = false, vegFruitServings = 0.0, fiberG = 2.0, treat = true, foodType = "burger")

    private fun meal(daysAgo: Int, hour: Int, kcal: Double, protein: Double, tags: MealTags?, text: String = "meal") = NutritionEntryEntity(
        foodText = text,
        calories = kcal,
        proteinG = protein,
        carbsG = 0.0,
        fatG = 0.0,
        timestamp = today.minusDays(daysAgo.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli(),
        qualityTags = tags?.encode()
    )

    private fun goodDay(daysAgo: Int) = listOf(
        meal(daysAgo, 8, 500.0, 35.0, homeCooked),
        meal(daysAgo, 13, 700.0, 45.0, homeCooked),
        meal(daysAgo, 20, 800.0, 45.0, homeCooked)
    )

    private fun score(entries: List<NutritionEntryEntity>, weeksAgo: Int = 0) =
        DietScoreEngine.score(entries, today, proteinTargetG = 120.0, calorieTarget = 2000.0, zone = zone, weeksAgo = weeksAgo)

    @Test
    fun `needs three logged days before giving a score`() {
        assertNull(score(goodDay(1) + goodDay(2)))
        assertNotNull(score(goodDay(1) + goodDay(2) + goodDay(3)))
    }

    @Test
    fun `a well-balanced week scores full marks`() {
        val s = score((1..6).flatMap { goodDay(it) })!!
        assertEquals(100, s.score)
        assertNull(DietScoreEngine.biggestGain(s))
    }

    @Test
    fun `a forgotten meal costs nothing`() {
        // Same good week, but breakfast was never logged on three of the days.
        val entries = (1..6).flatMap { d -> goodDay(d).let { if (d <= 3) it.drop(1) else it } }
        assertEquals(100, score(entries)!!.score)
    }

    @Test
    fun `two treats a week are free, each extra one costs five points`() {
        val week = (1..6).flatMap { goodDay(it) }
        fun withBurgers(n: Int) = week + (1..n).map { meal(it, 17, 600.0, 25.0, burger, "burger") }
        assertEquals(20, score(withBurgers(2))!!.parts.first { it.key == "treats" }.points)
        assertEquals(10, score(withBurgers(4))!!.parts.first { it.key == "treats" }.points)
    }

    @Test
    fun `low protein on full days is where the points go, and the tip says so`() {
        val entries = (1..6).flatMap { d -> goodDay(d).map { it.copy(proteinG = 10.0) } }
        val s = score(entries)!!
        assertEquals(0, s.parts.first { it.key == "protein" }.points)
        assertTrue(DietScoreEngine.biggestGain(s)!!.startsWith("Protein: hit your 120g target on 0 of 6"))
    }

    @Test
    fun `meals the AI hasn't tagged yet are left out of food quality`() {
        val untagged = (1..4).flatMap { d ->
            listOf(meal(d, 13, 900.0, 40.0, null, "chicken burger and fries"), meal(d, 20, 900.0, 40.0, null, "pizza"))
        }
        // Nothing tagged yet: no score rather than a guess from the names.
        assertNull(score(untagged))

        val s = score(untagged + (1..4).flatMap { goodDay(it) })!!
        assertEquals(0.4, s.estimatedShare, 0.001)
        assertEquals(0, s.treatMeals)
        assertEquals(20, s.parts.first { it.key == "whole" }.points)
    }

    @Test
    fun `last week is scored on its own seven days`() {
        val entries = (7..12).flatMap { goodDay(it) }
        assertNull(score(entries))
        assertEquals(100, score(entries, weeksAgo = 1)!!.score)
    }
}
