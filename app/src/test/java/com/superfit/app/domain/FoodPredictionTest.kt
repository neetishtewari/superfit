package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FoodPredictionTest {

    @Test
    fun testEmptyEntries() {
        val result = FoodPredictor.predict(emptyList(), currentHour = 8)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testCaseInsensitiveConsolidationAndAverageMacros() {
        val now = System.currentTimeMillis()
        val entries = listOf(
            NutritionEntryEntity(foodText = "banana", calories = 100.0, proteinG = 1.0, carbsG = 25.0, fatG = 0.0, timestamp = now),
            NutritionEntryEntity(foodText = "Banana", calories = 120.0, proteinG = 2.0, carbsG = 27.0, fatG = 0.2, timestamp = now),
            NutritionEntryEntity(foodText = "banana ", calories = 110.0, proteinG = 1.5, carbsG = 26.0, fatG = 0.1, timestamp = now)
        )

        val result = FoodPredictor.predict(entries, currentHour = 12, nowMs = now)
        assertEquals(1, result.size)
        
        val item = result[0]
        // Most common casing should be "banana" or "Banana" or "banana " depending on frequency.
        // In this case, each has frequency 1, so it takes the first or most common, which is "banana".
        assertTrue(item.foodText.trim().equals("banana", ignoreCase = true))
        
        // Average macros:
        // calories: (100 + 120 + 110)/3 = 110
        // protein: (1 + 2 + 1.5)/3 = 1.5
        // carbs: (25 + 27 + 26)/3 = 26
        // fat: (0 + 0.2 + 0.1)/3 = 0.1
        assertEquals(110.0, item.calories, 0.01)
        assertEquals(1.5, item.proteinG, 0.01)
        assertEquals(26.0, item.carbsG, 0.01)
        assertEquals(0.1, item.fatG, 0.01)
    }

    @Test
    fun testFrequencyScoring() {
        val nowMs = System.currentTimeMillis()
        // Log "Apple" once, log "Yogurt" twice
        val entries = listOf(
            NutritionEntryEntity(foodText = "Apple", calories = 80.0, proteinG = 0.0, carbsG = 20.0, fatG = 0.0, timestamp = nowMs),
            NutritionEntryEntity(foodText = "Yogurt", calories = 150.0, proteinG = 15.0, carbsG = 5.0, fatG = 2.0, timestamp = nowMs),
            NutritionEntryEntity(foodText = "Yogurt", calories = 150.0, proteinG = 15.0, carbsG = 5.0, fatG = 2.0, timestamp = nowMs)
        )

        val result = FoodPredictor.predict(entries, currentHour = 12, nowMs = nowMs)
        assertEquals(2, result.size)
        // Yogurt should be first because it has frequency 2 vs Apple's 1
        assertEquals("Yogurt", result[0].foodText)
        assertEquals("Apple", result[1].foodText)
    }

    @Test
    fun testRecencyScoring() {
        val nowMs = System.currentTimeMillis()
        val oneDayAgo = nowMs - (24 * 60 * 60 * 1000L)
        val tenDaysAgo = nowMs - (10 * 24 * 60 * 60 * 1000L)

        // Both logged once, both at the exact same hour of the day
        // Apple logged 10 days ago, Toast logged 1 day ago
        val appleCal = Calendar.getInstance().apply { timeInMillis = tenDaysAgo }
        val toastCal = Calendar.getInstance().apply { timeInMillis = oneDayAgo }
        
        val currentHour = appleCal.get(Calendar.HOUR_OF_DAY) // Align current hour to their logged hour

        val entries = listOf(
            NutritionEntryEntity(foodText = "Apple", calories = 80.0, proteinG = 0.0, carbsG = 20.0, fatG = 0.0, timestamp = tenDaysAgo),
            NutritionEntryEntity(foodText = "Toast", calories = 120.0, proteinG = 3.0, carbsG = 22.0, fatG = 1.0, timestamp = oneDayAgo)
        )

        val result = FoodPredictor.predict(entries, currentHour = currentHour, nowMs = nowMs)
        assertEquals(2, result.size)
        // Toast is more recent, should have a higher score
        assertEquals("Toast", result[0].foodText)
        assertEquals("Apple", result[1].foodText)
    }

    @Test
    fun testTimeOfDayScoring() {
        val nowMs = System.currentTimeMillis()
        // We log "Eggs" at 8:00 AM (8h) and "Steak" at 8:00 PM (20h).
        val cal8Am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }
        val cal8Pm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }

        val entries = listOf(
            NutritionEntryEntity(foodText = "Eggs", calories = 140.0, proteinG = 12.0, carbsG = 1.0, fatG = 10.0, timestamp = cal8Am.timeInMillis),
            NutritionEntryEntity(foodText = "Steak", calories = 400.0, proteinG = 30.0, carbsG = 0.0, fatG = 25.0, timestamp = cal8Pm.timeInMillis)
        )

        // Scenario A: Current time is 9:00 AM (Hour = 9)
        // Eggs (logged at 8 AM) is closer to 9 AM (diff = 1 hour) than Steak (logged at 8 PM, diff = 11 hours)
        val resultMorning = FoodPredictor.predict(entries, currentHour = 9, nowMs = nowMs)
        assertEquals("Eggs", resultMorning[0].foodText)
        assertEquals("Steak", resultMorning[1].foodText)

        // Scenario B: Current time is 9:00 PM (Hour = 21)
        // Steak (logged at 8 PM) is closer to 9 PM (diff = 1 hour) than Eggs (logged at 8 AM, diff = 11 hours)
        val resultEvening = FoodPredictor.predict(entries, currentHour = 21, nowMs = nowMs)
        assertEquals("Steak", resultEvening[0].foodText)
        assertEquals("Eggs", resultEvening[1].foodText)
    }
}
