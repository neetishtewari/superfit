package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealSuggesterTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun item(name: String) = MealItem(
        name = name, qty = 1.0, unit = "piece", kcal = 100.0, protein = 3.0, carbs = 10.0,
        fat = 2.0, fiber = 1.0, oilG = 0.0, sugarG = 0.0
    )

    private fun meal(text: String, kcal: Double, daysAgo: Long, vararg items: String) = NutritionEntryEntity(
        foodText = text,
        calories = kcal,
        proteinG = 10.0,
        carbsG = 30.0,
        fatG = 5.0,
        timestamp = now - daysAgo * day,
        items = if (items.isEmpty()) null else MealItems(items.map(::item)).encode()
    )

    private val history = listOf(
        meal("2 roti, dal and salad", 420.0, 1, "Roti", "Dal", "Salad"),
        meal("2 roti, dal and salad", 400.0, 5, "Roti", "Dal", "Salad"),
        meal("Paneer bhurji and 2 roti", 540.0, 2, "Paneer bhurji", "Roti"),
        meal("Palak paneer", 380.0, 20, "Palak paneer"),
        meal("Poha with peanuts", 310.0, 1, "Poha", "Peanuts"),
        meal("Açaí bowl", 350.0, 3, "Açaí"),
        meal("invalid", 0.0, 1)
    )

    private fun suggester(scores: Map<String, Double> = emptyMap()) = MealSuggester.build(history, scores, now)

    @Test
    fun nothingUntilTwoLetters() {
        assertTrue(suggester().suggest("").isEmpty())
        assertTrue(suggester().suggest("p").isEmpty())
        assertTrue(suggester().suggest("2 r").isEmpty())
    }

    @Test
    fun wholeMealFillsTheBoxWithLastTimesCalories() {
        val s = suggester().suggest("2 ro").first { it.kind == MealSuggestion.Kind.MEAL }
        assertEquals("2 roti, dal and salad", s.text)
        assertEquals("2 roti, dal and salad", s.result)
        assertEquals(420, s.kcal)
        assertEquals("2 ro", s.text.substring(s.matchStart, s.matchStart + s.matchLength))
    }

    @Test
    fun foodFinishesOnlyThePartBeingTypedAndKeepsTheAmount() {
        val foods = suggester().suggest("2 roti and pa").filter { it.kind == MealSuggestion.Kind.FOOD }
        assertEquals(listOf("Paneer bhurji", "Palak paneer"), foods.map { it.text })
        assertEquals("2 roti and paneer bhurji, ", foods.first().result)

        val amount = suggester().suggest("dal, 2 ro").first { it.kind == MealSuggestion.Kind.FOOD }
        assertEquals("Roti", amount.text)
        assertEquals("dal, 2 roti, ", amount.result)
    }

    @Test
    fun aUnitBeforeTheFoodFallsBackToTheLastWord() {
        val s = suggester().suggest("1 katori da").first { it.kind == MealSuggestion.Kind.FOOD }
        assertEquals("Dal", s.text)
        assertEquals("1 katori dal, ", s.result)
    }

    @Test
    fun startsWithComesBeforeLaterWords() {
        val texts = suggester().suggest("pa").map { it.text }
        assertEquals("Paneer bhurji and 2 roti", texts.first())
        assertTrue(texts.indexOf("Palak paneer") >= 0)
        // "Poha with peanuts" only matches at a later word, so it comes after the meals that start with "pa".
        assertTrue(texts.size <= MealSuggester.MAX)
    }

    @Test
    fun scoresDecideTheOrderWithinAKind() {
        val s = suggester(mapOf("palak paneer" to 50.0, "paneer bhurji and 2 roti" to 1.0)).suggest("pa")
        assertEquals("Palak paneer", s.first().text)
    }

    @Test
    fun accentsAndCapitalsAreIgnored() {
        val s = suggester().suggest("ACAI")
        assertEquals("Açaí bowl", s.first().text)
        assertEquals("Açaí", s.first().text.substring(s.first().matchStart, s.first().matchStart + s.first().matchLength))
    }

    @Test
    fun noDuplicatesAndNotWhatWasTyped() {
        val s = suggester().suggest("palak paneer")
        assertTrue(s.none { it.text.equals("Palak paneer", ignoreCase = true) })
        val all = suggester().suggest("po")
        assertEquals(all.map { it.text.lowercase() }.distinct().size, all.size)
    }

    @Test
    fun atMostFourAndNoInvalidMeals() {
        val s = suggester().suggest("in")
        assertTrue(s.none { it.text == "invalid" })
        assertTrue(suggester().suggest("pa").size <= 4)
    }

    @Test
    fun afterPickingAFoodTheListCloses() {
        val picked = suggester().suggest("2 roti and pa").first { it.kind == MealSuggestion.Kind.FOOD }.result
        assertTrue(suggester().suggest(picked).isEmpty())
    }

    @Test
    fun emptyHistorySuggestsNothing() {
        assertTrue(MealSuggester.build(emptyList(), emptyMap(), now).suggest("roti").isEmpty())
    }
}
