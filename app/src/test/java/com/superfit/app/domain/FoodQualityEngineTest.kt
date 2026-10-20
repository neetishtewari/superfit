package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodQualityEngineTest {

    private val zoneId = ZoneId.of("UTC")
    private val now = System.currentTimeMillis()
    private val oneDayMs = 24 * 3600 * 1000L

    // What the AI returns for each kind of meal. The engine knows foods only through these tags.
    private val cleanTags = MealTags(1, fried = false, addedSugar = false, vegFruitServings = 1.0, fiberG = 5.0, treat = false, foodType = "salad")

    private fun junkTags(type: String, swap: String?, sugar: Boolean = false) = MealTags(
        3, fried = !sugar, addedSugar = sugar, vegFruitServings = 0.0, fiberG = 1.0, treat = true, foodType = type,
        why = "Fast food that is heavy on refined carbs and fat.",
        swap = swap?.let { MealSwap(it, 200, "Lighter and more protein") }
    )

    private fun food(text: String, kcal: Double, protein: Double, tags: MealTags?, timestamp: Long = now) = NutritionEntryEntity(
        foodText = text,
        calories = kcal,
        proteinG = protein,
        carbsG = 0.0,
        fatG = 0.0,
        timestamp = timestamp,
        qualityTags = tags?.encode()
    )

    @Test
    fun testEmptyHistoryReturnsDefaultMetrics() {
        val result = FoodQualityEngine.analyzeDietQuality(emptyList(), zoneId = zoneId)
        assertEquals(0, result.overallScore)
        assertFalse(result.hasEnoughData)
        assertEquals(0, result.distinctDaysLogged)
        assertTrue(result.allRatedItems.isEmpty())
        assertTrue(result.swapSuggestions.isEmpty())
    }

    @Test
    fun `a whole-food meal tagged by the AI scores top marks`() {
        val result = FoodQualityEngine.analyzeDietQuality(listOf(food("Grilled Chicken Salad", 350.0, 40.0, cleanTags)), zoneId = zoneId)
        val item = result.allRatedItems.single()
        assertEquals(10, item.cleanlinessScore)
        assertEquals(FoodCleanlinessTier.CLEANEST, item.tier)
        assertTrue(item.rated)
        assertTrue(result.swapSuggestions.isEmpty())
    }

    @Test
    fun `a fast-food meal gets the AI's swap on day one but is not a habit yet`() {
        val result = FoodQualityEngine.analyzeDietQuality(
            listOf(food("Cheeseburger", 650.0, 25.0, junkTags("burger", "grilled chicken wrap"))),
            zoneId = zoneId
        )
        val item = result.allRatedItems.single()
        assertEquals(2, item.cleanlinessScore)
        assertEquals(FoodCleanlinessTier.HIGH_RISK, item.tier)
        assertFalse(item.isHabitualRisk)
        assertTrue(item.actionRecommendation.contains("Single-day indulgence"))
        assertEquals("Grilled Chicken Wrap", result.swapSuggestions.single().suggestedAlternative)
    }

    @Test
    fun `one cheat day does not drag down a clean week`() {
        val clean = (1..6).flatMap { d ->
            val t = now - d * oneDayMs
            listOf(food("Oatmeal", 300.0, 15.0, cleanTags, t), food("Chicken Breast", 350.0, 50.0, cleanTags, t), food("Green Salad", 150.0, 4.0, cleanTags, t))
        }
        val cheat = listOf(
            food("Pepperoni Pizza", 700.0, 20.0, junkTags("pizza", "paneer tikka on whole wheat roti")),
            food("Coke", 180.0, 0.0, junkTags("soft drink", "nimbu pani without sugar", sugar = true))
        )
        val result = FoodQualityEngine.analyzeDietQuality(clean + cheat, zoneId = zoneId)

        assertEquals(7, result.distinctDaysLogged)
        assertFalse(result.allRatedItems.first { it.foodText.contains("Pizza") }.isHabitualRisk)
        assertTrue("Overall score (${result.overallScore}) should reflect mostly clean eating", result.overallScore >= 85)
        assertEquals(2, result.swapSuggestions.size)
    }

    @Test
    fun `a treat on three different days is a habit`() {
        val entries = listOf(1, 3, 5).map { d -> food("Coke", 180.0, 0.0, junkTags("soft drink", null, sugar = true), now - d * oneDayMs) }
        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        val coke = result.allRatedItems.single()
        assertEquals(3, coke.daysLoggedCount)
        assertTrue(coke.isHabitualRisk)
        assertTrue(coke.actionRecommendation.contains("reduce frequency", ignoreCase = true))
        assertTrue(result.flaggedHabitualFoods.contains(coke))
    }

    @Test
    fun `the same swap is never suggested twice`() {
        val entries = listOf(
            food("Cheeseburger", 650.0, 25.0, junkTags("burger", "chicken wrap")),
            food("Chicken Nuggets", 400.0, 20.0, junkTags("nuggets", "chicken wrap")),
            food("French Fries", 400.0, 4.0, junkTags("fries", "roasted chana"))
        )
        val alternatives = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId).swapSuggestions.map { it.suggestedAlternative }
        assertEquals(listOf("Chicken Wrap", "Roasted Chana").sorted(), alternatives.sorted())
    }

    @Test
    fun `meals the AI hasn't tagged yet are not rated, flagged or swapped`() {
        val entries = listOf(1, 2, 3).flatMap { d ->
            listOf(food("samosa", 260.0, 4.0, null, now - d * oneDayMs), food("apple", 95.0, 0.5, null, now - d * oneDayMs))
        }
        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        for (item in result.allRatedItems) {
            assertFalse(item.rated)
            assertEquals(FoodQualityEngine.UNRATED_SCORE, item.cleanlinessScore)
            assertFalse(item.isHabitualRisk)
        }
        assertTrue(result.swapSuggestions.isEmpty())
        assertTrue(result.topCleanFoods.isEmpty())
        assertFalse(result.hasEnoughData)
    }

    @Test
    fun `older tags without the AI's reason are explained from the tags`() {
        val oldTags = MealTags(3, fried = true, addedSugar = false, vegFruitServings = 0.0, fiberG = 1.0, treat = true, foodType = "pakora")
        val item = FoodQualityEngine.analyzeDietQuality(listOf(food("Pakora", 300.0, 5.0, oldTags)), zoneId = zoneId).allRatedItems.single()
        assertEquals("Ultra-processed or fast food • fried", item.scoreRationale)
    }

    @Test
    fun `the AI's swap and reason are used for any language`() {
        val aiTags = MealTags(
            processing = 3, fried = true, addedSugar = false, vegFruitServings = 0.0, fiberG = 3.0, treat = true,
            foodType = "chole kulche", why = "Fried kulcha and oily chole make this heavy.",
            swap = MealSwap("chole with 2 phulkas", 220, "Same chole, no fried bread")
        )
        val entries = listOf(food("छोले कुलचे", 600.0, 15.0, aiTags))
        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)

        val chole = result.allRatedItems.single()
        assertEquals("Fried kulcha and oily chole make this heavy.", chole.scoreRationale)
        val swap = result.swapSuggestions.single()
        assertEquals("छोले कुलचे", swap.originalFood)
        assertEquals("Chole With 2 Phulkas", swap.suggestedAlternative)
        assertEquals(220, swap.calorieSavings)
    }

    @Test
    fun `hidden oil costs a point like frying does`() {
        val gravy = MealTags(2, false, false, 0.5, 4.0, false, "dal makhani")
        assertEquals(FoodQualityEngine.scoreFromTags(gravy, false) - 1, FoodQualityEngine.scoreFromTags(gravy.copy(hiddenOil = true), false))
    }
}
