package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FoodQualityEngineTest {

    private val zoneId = ZoneId.of("UTC")
    private val now = System.currentTimeMillis()
    private val oneDayMs = 24 * 3600 * 1000L

    @Test
    fun testEmptyHistoryReturnsDefaultMetrics() {
        val result = FoodQualityEngine.analyzeDietQuality(emptyList(), zoneId = zoneId)
        assertEquals(85, result.overallScore)
        assertEquals(0, result.distinctDaysLogged)
        assertTrue(result.allRatedItems.isEmpty())
        assertTrue(result.swapSuggestions.isEmpty())
    }

    @Test
    fun testDayOneSingleCleanFoodItemizedImmediateScore() {
        val entries = listOf(
            NutritionEntryEntity(
                foodText = "Grilled Chicken Salad",
                calories = 350.0,
                proteinG = 40.0,
                carbsG = 10.0,
                fatG = 12.0,
                timestamp = now
            )
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        assertEquals(1, result.distinctDaysLogged)
        assertEquals(1, result.allRatedItems.size)

        val item = result.allRatedItems.first()
        assertEquals(10, item.cleanlinessScore)
        assertEquals(FoodCleanlinessTier.CLEANEST, item.tier)
        assertEquals(1, item.weeklyFrequency)
        assertEquals(1, item.daysLoggedCount)
        assertTrue(item.actionRecommendation.contains("satiety") || item.actionRecommendation.contains("protein") || item.actionRecommendation.contains("Clean"))
    }

    @Test
    fun testDayOneProcessedFoodGeneratesImmediateSwap() {
        val entries = listOf(
            NutritionEntryEntity(
                foodText = "Cheeseburger",
                calories = 650.0,
                proteinG = 25.0,
                carbsG = 45.0,
                fatG = 38.0,
                timestamp = now
            )
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        assertEquals(1, result.distinctDaysLogged)
        assertEquals(1, result.allRatedItems.size)

        val item = result.allRatedItems.first()
        assertEquals(3, item.cleanlinessScore)
        assertEquals(FoodCleanlinessTier.HIGH_RISK, item.tier)
        // On Day 1, single entry is NOT treated as a multi-day habit
        assertFalse(item.isHabitualRisk)
        assertTrue(item.actionRecommendation.contains("Single-day indulgence"))

        // Must generate swap even on Day 1 for low quality food!
        assertEquals(1, result.swapSuggestions.size)
        val swap = result.swapSuggestions.first()
        assertTrue(swap.suggestedAlternative.contains("Chicken") || swap.suggestedAlternative.contains("Wrap"))
        assertTrue(swap.calorieSavings > 0)
    }

    @Test
    fun testOutlierDayDoesNotDistortSevenDayCleanEatingScore() {
        // 6 days of clean eating (Chicken, Oats, Salad, Salmon)
        val cleanEntries = mutableListOf<NutritionEntryEntity>()
        for (dayOffset in 1..6) {
            val dayTime = now - (dayOffset * oneDayMs)
            cleanEntries.add(NutritionEntryEntity(foodText = "Oatmeal", calories = 300.0, proteinG = 15.0, carbsG = 50.0, fatG = 5.0, timestamp = dayTime))
            cleanEntries.add(NutritionEntryEntity(foodText = "Chicken Breast", calories = 350.0, proteinG = 50.0, carbsG = 0.0, fatG = 5.0, timestamp = dayTime))
            cleanEntries.add(NutritionEntryEntity(foodText = "Green Salad", calories = 150.0, proteinG = 4.0, carbsG = 15.0, fatG = 7.0, timestamp = dayTime))
        }

        // 1 outlier cheat day: Pizza and Soda logged on day 0
        val outlierEntries = listOf(
            NutritionEntryEntity(foodText = "Pepperoni Pizza", calories = 700.0, proteinG = 20.0, carbsG = 70.0, fatG = 30.0, timestamp = now),
            NutritionEntryEntity(foodText = "Coke Soda", calories = 180.0, proteinG = 0.0, carbsG = 45.0, fatG = 0.0, timestamp = now)
        )

        val allEntries = cleanEntries + outlierEntries
        val result = FoodQualityEngine.analyzeDietQuality(allEntries, zoneId = zoneId)

        // 7 distinct days
        assertEquals(7, result.distinctDaysLogged)

        // Outlier items logged on only 1 distinct day must NOT be flagged as habitual risks
        val pizzaRating = result.allRatedItems.first { it.foodText.contains("Pizza", ignoreCase = true) }
        val sodaRating = result.allRatedItems.first { it.foodText.contains("Soda", ignoreCase = true) }
        assertEquals(1, pizzaRating.daysLoggedCount)
        assertEquals(1, sodaRating.daysLoggedCount)
        assertFalse("Pizza on 1 day must not be habitual risk", pizzaRating.isHabitualRisk)
        assertFalse("Soda on 1 day must not be habitual risk", sodaRating.isHabitualRisk)

        // Proportional weighting: 18 clean meals (score 10) vs 2 outlier meals (scores 5 and 3)
        // Score should remain high (> 85) reflecting the overall 7-day pattern
        assertTrue("Overall score (${result.overallScore}) should reflect predominantly clean eating", result.overallScore >= 85)

        // But swaps must still be offered for the outlier foods!
        assertTrue(result.swapSuggestions.any { it.originalFood.contains("Pizza", ignoreCase = true) || it.originalFood.contains("Soda", ignoreCase = true) })
    }

    @Test
    fun testHabitualRiskIdentifiedWhenFoodLoggedAcrossMultipleDays() {
        val entries = mutableListOf<NutritionEntryEntity>()
        // User logs Soda across 3 distinct days
        for (dayOffset in listOf(1, 3, 5)) {
            val dayTime = now - (dayOffset * oneDayMs)
            entries.add(NutritionEntryEntity(foodText = "Coke Soda", calories = 180.0, proteinG = 0.0, carbsG = 45.0, fatG = 0.0, timestamp = dayTime))
        }

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        val sodaRating = result.allRatedItems.first { it.foodText.contains("Soda", ignoreCase = true) }

        assertEquals(3, sodaRating.daysLoggedCount)
        assertEquals(3, sodaRating.weeklyFrequency)
        assertTrue("Logged across 3 days must be flagged as habitual risk", sodaRating.isHabitualRisk)
        assertTrue("Action must recommend reducing frequency", sodaRating.actionRecommendation.contains("reduce frequency", ignoreCase = true))
        assertTrue(result.flaggedHabitualFoods.contains(sodaRating))
    }

    @Test
    fun testMultipleSwapsAreUniqueAndDiverse() {
        val entries = listOf(
            NutritionEntryEntity(foodText = "Cheeseburger", calories = 650.0, proteinG = 25.0, carbsG = 45.0, fatG = 38.0, timestamp = now),
            NutritionEntryEntity(foodText = "French Fries", calories = 400.0, proteinG = 4.0, carbsG = 50.0, fatG = 20.0, timestamp = now),
            NutritionEntryEntity(foodText = "Coke Soda", calories = 180.0, proteinG = 0.0, carbsG = 45.0, fatG = 0.0, timestamp = now),
            NutritionEntryEntity(foodText = "Pepperoni Pizza", calories = 700.0, proteinG = 20.0, carbsG = 70.0, fatG = 30.0, timestamp = now)
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        assertEquals(4, result.swapSuggestions.size)

        // All suggested alternatives MUST be completely unique
        val alternatives = result.swapSuggestions.map { it.suggestedAlternative }
        assertEquals("Each swap suggestion must be distinct and unique", alternatives.distinct().size, alternatives.size)

        // None of them should be a repeated catch-all
        for (swap in result.swapSuggestions) {
            assertTrue(swap.calorieSavings > 0)
            assertTrue(swap.benefitHighlight.isNotBlank())
        }
    }

    @Test
    fun testHealthyStaplesNotOfferedSwaps() {
        val entries = listOf(
            NutritionEntryEntity(foodText = "Grilled Salmon", calories = 400.0, proteinG = 42.0, carbsG = 0.0, fatG = 22.0, timestamp = now),
            NutritionEntryEntity(foodText = "Brown Rice", calories = 220.0, proteinG = 5.0, carbsG = 46.0, fatG = 2.0, timestamp = now),
            NutritionEntryEntity(foodText = "Paneer Tikka", calories = 280.0, proteinG = 18.0, carbsG = 8.0, fatG = 20.0, timestamp = now),
            NutritionEntryEntity(foodText = "Steamed Broccoli", calories = 55.0, proteinG = 4.0, carbsG = 11.0, fatG = 0.6, timestamp = now)
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        // Clean whole foods should have high scores (9 or 10) and NO swap suggestions
        for (item in result.allRatedItems) {
            assertTrue("${item.foodText} should be clean", item.cleanlinessScore >= 9)
        }
        assertTrue("Healthy staples do not need swaps", result.swapSuggestions.isEmpty())
    }

    @Test
    fun testFriedEggsRatedTopTierAndNeverSwappedWithPotatoWedges() {
        val entries = listOf(
            NutritionEntryEntity(
                foodText = "Fried Eggs",
                calories = 220.0,
                proteinG = 18.0,
                carbsG = 1.0,
                fatG = 16.0,
                timestamp = now
            )
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, userGoal = "LOSE_WEIGHT", zoneId = zoneId)
        val eggItem = result.allRatedItems.first()

        assertEquals(10, eggItem.cleanlinessScore)
        assertEquals(FoodCleanlinessTier.CLEANEST, eggItem.tier)
        assertFalse(eggItem.isHabitualRisk)
        assertTrue("Egg action should recognize high satiety and lean mass preservation", eggItem.actionRecommendation.contains("satiety", ignoreCase = true) || eggItem.actionRecommendation.contains("protein", ignoreCase = true))

        // Eggs must NEVER be recommended to swap with potato wedges!
        assertTrue("Eggs should not have any swap suggestions", result.swapSuggestions.isEmpty())
        assertFalse("Never swap eggs for potato wedges", result.swapSuggestions.any { it.suggestedAlternative.contains("Potato", ignoreCase = true) })
    }

    @Test
    fun testCategoryIntegrityInFitnessSwaps() {
        val entries = listOf(
            // Crunchy fried carbs
            NutritionEntryEntity(foodText = "French Fries", calories = 380.0, proteinG = 4.0, carbsG = 48.0, fatG = 18.0, timestamp = now),
            // Processed meat
            NutritionEntryEntity(foodText = "Crispy Bacon", calories = 260.0, proteinG = 15.0, carbsG = 1.0, fatG = 22.0, timestamp = now)
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, userGoal = "BUILD_MUSCLE", zoneId = zoneId)
        val friesSwap = result.swapSuggestions.first { it.originalFood.contains("Fries", ignoreCase = true) }
        val baconSwap = result.swapSuggestions.first { it.originalFood.contains("Bacon", ignoreCase = true) }

        // Fries swaps with healthier carb (Sweet Potato or Zucchini)
        assertTrue(friesSwap.suggestedAlternative.contains("Sweet Potato", ignoreCase = true) || friesSwap.suggestedAlternative.contains("Zucchini", ignoreCase = true))

        // Bacon swaps with leaner poultry protein (Turkey Bacon or Chicken Sausage)
        assertTrue(baconSwap.suggestedAlternative.contains("Turkey", ignoreCase = true) || baconSwap.suggestedAlternative.contains("Chicken", ignoreCase = true))
    }

    @Test
    fun testAppleAndPearBothRatedTenWithClearRationale() {
        val entries = listOf(
            NutritionEntryEntity(foodText = "Fresh Apple", calories = 95.0, proteinG = 0.5, carbsG = 25.0, fatG = 0.3, timestamp = now),
            NutritionEntryEntity(foodText = "Ripe Pear", calories = 100.0, proteinG = 0.6, carbsG = 27.0, fatG = 0.2, timestamp = now)
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        val appleItem = result.allRatedItems.first { it.foodText.contains("Apple", ignoreCase = true) }
        val pearItem = result.allRatedItems.first { it.foodText.contains("Pear", ignoreCase = true) }

        // Both apple and pear MUST be rated 10/10 CLEANEST whole foods
        assertEquals(10, appleItem.cleanlinessScore)
        assertEquals(10, pearItem.cleanlinessScore)
        assertEquals(FoodCleanlinessTier.CLEANEST, appleItem.tier)
        assertEquals(FoodCleanlinessTier.CLEANEST, pearItem.tier)

        // Rationales must explain WHY
        assertTrue("Apple rationale should mention fiber/pectin/polyphenols", appleItem.scoreRationale.isNotBlank())
        assertTrue("Pear rationale should mention fiber/pectin/antioxidants", pearItem.scoreRationale.isNotBlank())
        assertTrue("Apple rationale details nutrition", appleItem.scoreRationale.contains("fiber", ignoreCase = true) || appleItem.scoreRationale.contains("pectin", ignoreCase = true))
        assertTrue("Pear rationale details nutrition", pearItem.scoreRationale.contains("fiber", ignoreCase = true) || pearItem.scoreRationale.contains("pectin", ignoreCase = true))
    }

    @Test
    fun testScoreRationalesProvideEducationalReasonsAcrossTiers() {
        val entries = listOf(
            NutritionEntryEntity(foodText = "French Fries", calories = 380.0, proteinG = 4.0, carbsG = 48.0, fatG = 18.0, timestamp = now),
            NutritionEntryEntity(foodText = "Coke Soda", calories = 180.0, proteinG = 0.0, carbsG = 45.0, fatG = 0.0, timestamp = now),
            NutritionEntryEntity(foodText = "Fried Eggs", calories = 220.0, proteinG = 18.0, carbsG = 1.0, fatG = 16.0, timestamp = now)
        )

        val result = FoodQualityEngine.analyzeDietQuality(entries, zoneId = zoneId)
        val friesItem = result.allRatedItems.first { it.foodText.contains("Fries", ignoreCase = true) }
        val sodaItem = result.allRatedItems.first { it.foodText.contains("Soda", ignoreCase = true) }
        val eggItem = result.allRatedItems.first { it.foodText.contains("Eggs", ignoreCase = true) }

        // Low tier foods have rationale explaining industrial fats/spikes/processing
        assertTrue(friesItem.cleanlinessScore <= 5)
        assertTrue(friesItem.scoreRationale.isNotBlank())
        assertTrue(sodaItem.cleanlinessScore <= 3)
        assertTrue(sodaItem.scoreRationale.contains("insulin", ignoreCase = true) || sodaItem.scoreRationale.contains("liquid", ignoreCase = true) || sodaItem.scoreRationale.contains("sugar", ignoreCase = true))

        // High tier food explains why it is good
        assertEquals(10, eggItem.cleanlinessScore)
        assertTrue(eggItem.scoreRationale.contains("protein", ignoreCase = true) || eggItem.scoreRationale.contains("amino", ignoreCase = true) || eggItem.scoreRationale.contains("micronutrient", ignoreCase = true))
    }
}

