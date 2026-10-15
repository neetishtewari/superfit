package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatMealCacheTest {

    private val sampleHistory = listOf(
        NutritionEntryEntity(
            foodText = "Boiled Egg",
            calories = 78.0,
            proteinG = 6.3,
            carbsG = 0.6,
            fatG = 5.3,
            timestamp = System.currentTimeMillis()
        ),
        NutritionEntryEntity(
            foodText = "Apple",
            calories = 95.0,
            proteinG = 0.5,
            carbsG = 25.0,
            fatG = 0.3,
            timestamp = System.currentTimeMillis()
        ),
        NutritionEntryEntity(
            foodText = "Milk Tea with Stevia",
            calories = 45.0,
            proteinG = 2.0,
            carbsG = 4.0,
            fatG = 2.2,
            timestamp = System.currentTimeMillis()
        ),
        NutritionEntryEntity(
            foodText = "Grilled Chicken Salad",
            calories = 350.0,
            proteinG = 42.0,
            carbsG = 10.0,
            fatG = 12.0,
            timestamp = System.currentTimeMillis()
        )
    )

    @Test
    fun testExactMatchReturnsInstantly() {
        val result = RepeatMealCache.findRepeatMeal("boiled egg", sampleHistory)
        assertNotNull(result)
        assertEquals("Boiled Egg", result!!.foodText)
        assertEquals(78.0, result.calories, 0.1)
        assertEquals(6.3, result.proteinG, 0.1)
    }

    @Test
    fun testMilkTeaWithSteviaExactMatch() {
        val result = RepeatMealCache.findRepeatMeal("Milk tea with stevia", sampleHistory)
        assertNotNull(result)
        assertEquals("Milk Tea with Stevia", result!!.foodText)
        assertEquals(45.0, result.calories, 0.1)
        assertEquals(2.0, result.proteinG, 0.1)
    }

    @Test
    fun testQuantityScaledMatchTwoBoiledEggs() {
        val result = RepeatMealCache.findRepeatMeal("2 boiled eggs", sampleHistory)
        assertNotNull(result)
        // 78 * 2 = 156 cal, 6.3 * 2 = 12.6 protein
        assertEquals(156.0, result!!.calories, 0.5)
        assertEquals(12.6, result.proteinG, 0.2)
    }

    @Test
    fun testWordQuantityScaledMatchThreeApples() {
        val result = RepeatMealCache.findRepeatMeal("three apples", sampleHistory)
        assertNotNull(result)
        // 95 * 3 = 285 cal
        assertEquals(285.0, result!!.calories, 0.5)
        assertEquals(1.5, result.proteinG, 0.2)
    }

    @Test
    fun testNonMatchReturnsNullForAI() {
        val result = RepeatMealCache.findRepeatMeal("Spaghetti Carbonara with Truffles", sampleHistory)
        assertNull(result)
    }

    @Test
    fun testDescriptorStrippingMatchesLargeBoiledEgg() {
        val historyWithDescriptors = listOf(
            NutritionEntryEntity(
                foodText = "1 large hard-boiled egg",
                calories = 78.0,
                proteinG = 6.3,
                carbsG = 0.6,
                fatG = 5.3,
                timestamp = System.currentTimeMillis()
            )
        )
        val result = RepeatMealCache.findRepeatMeal("boiled egg", historyWithDescriptors)
        assertNotNull(result)
        assertEquals(78.0, result!!.calories, 0.1)
    }

    @Test
    fun testCupOfMilkTeaMatchesMilkTeaWithStevia() {
        val history = listOf(
            NutritionEntryEntity(
                foodText = "1 cup of milk tea with stevia",
                calories = 45.0,
                proteinG = 2.0,
                carbsG = 4.0,
                fatG = 2.2,
                timestamp = System.currentTimeMillis()
            )
        )
        val result = RepeatMealCache.findRepeatMeal("milk tea with stevia", history)
        assertNotNull(result)
        assertEquals(45.0, result!!.calories, 0.1)
    }

    @Test
    fun testCommonStaplesFallbackWhenHistoryEmpty() {
        val result = RepeatMealCache.findRepeatMeal("2 boiled eggs", emptyList())
        assertNotNull(result)
        assertEquals(156.0, result!!.calories, 0.5)
        assertEquals(12.6, result.proteinG, 0.2)
    }

    @Test
    fun testEmptyHistoryUnknownFoodReturnsNull() {
        val result = RepeatMealCache.findRepeatMeal("Dragonfruit smoothie bowl with spirulina", emptyList())
        assertNull(result)
    }

    @Test
    fun testTwoRotisUsesStaple() {
        val result = RepeatMealCache.findRepeatMeal("2 rotis", emptyList())
        assertNotNull(result)
        assertEquals(200.0, result!!.calories, 0.5)
    }

    @Test
    fun testTuIsReadAsTwo() {
        val result = RepeatMealCache.findRepeatMeal("tu rotis", emptyList())
        assertNotNull(result)
        assertEquals(200.0, result!!.calories, 0.5)
    }

    @Test
    fun testTandooriRotiDoesNotBorrowPlainRoti() {
        // Speech-to-text heard "two tandoori rotis" as "tu tandoori rotis"; this must go to Gemini
        assertNull(RepeatMealCache.findRepeatMeal("tu tandoori rotis", emptyList()))
    }

    @Test
    fun testPlainRotiDoesNotCopyLargerPastMeal() {
        val history = listOf(
            NutritionEntryEntity(
                foodText = "1 roti with dal and paneer",
                calories = 550.0,
                proteinG = 25.0,
                carbsG = 50.0,
                fatG = 25.0,
                timestamp = System.currentTimeMillis()
            )
        )
        val result = RepeatMealCache.findRepeatMeal("2 rotis", history)
        assertNotNull(result)
        // Uses the plain roti staple (2 x 100), not 2 x the whole past meal
        assertEquals(200.0, result!!.calories, 0.5)
    }

    @Test
    fun `hindi script is kept, so different hindi meals never match each other`() {
        assertEquals("2 अंडे", RepeatMealCache.normalize("२ अंडे!"))
        val history = listOf(
            NutritionEntryEntity(foodText = "2 अंडे", calories = 156.0, proteinG = 12.6, carbsG = 1.2, fatG = 10.6, timestamp = 0L)
        )
        assertNull(RepeatMealCache.findRepeatMeal("2 रोटी", history))
        assertEquals(156.0, RepeatMealCache.findRepeatMeal("2 अंडे", history)!!.calories, 0.1)
    }

    @Test
    fun `a repeat of a past meal keeps its quality tags, scaled to the portion`() {
        val tags = MealTags(2, true, false, 0.0, 2.0, true, "samosa", why = "Deep fried.", swap = MealSwap("baked samosa", 80, "Less oil"))
        val history = listOf(
            NutritionEntryEntity(foodText = "1 samosa", calories = 260.0, proteinG = 4.0, carbsG = 30.0, fatG = 14.0, timestamp = 0L, qualityTags = tags.encode())
        )
        val result = RepeatMealCache.findRepeatMeal("2 samosas", history)!!
        assertEquals(520.0, result.calories, 0.1)
        assertEquals(4.0, result.tags!!.fiberG, 0.0)
        assertEquals(160, result.tags!!.swap!!.kcalSaved)
        assertEquals("Deep fried.", result.tags!!.why)
    }
}
