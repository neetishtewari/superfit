package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class MealTagBackfillTest {

    private val day = 24L * 3600 * 1000
    private val now = 100L * day
    private fun meal(id: Long, daysAgo: Long) = NutritionEntryEntity(id = id, foodText = "dal rice", calories = 450.0, proteinG = 15.0, carbsG = 70.0, fatG = 10.0, timestamp = now - daysAgo * day)

    @Test
    fun untaggedMealsFromTheLastFourWeeksAreDueNewestFirst() {
        val due = MealTagBackfill.due(listOf(meal(1, 20), meal(2, 3), meal(3, 40)), now)

        assertEquals(listOf(2L, 1L), due.map { it.id })
    }

    @Test
    fun aMealNotYetSavedIsLeftAlone() {
        assertEquals(emptyList(), MealTagBackfill.due(listOf(meal(0, 1)), now))
    }

    @Test
    fun atMostSixtyMealsAtATime() {
        val meals = (1L..80L).map { meal(it, it % 20) }

        assertEquals(60, MealTagBackfill.due(meals, now).size)
    }
}
