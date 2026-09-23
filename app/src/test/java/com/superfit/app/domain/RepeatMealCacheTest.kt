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
}
