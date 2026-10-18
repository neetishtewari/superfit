package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MealItemsTest {

    private val roti = MealItem(
        name = "roti", qty = 2.0, unit = "piece",
        kcal = 120.0, protein = 3.5, carbs = 20.0, fat = 3.0, fiber = 2.5, oilG = 2.0, sugarG = 0.0
    )
    private val dal = MealItem(
        name = "dal", qty = 1.0, unit = "katori",
        kcal = 180.0, protein = 9.0, carbs = 24.0, fat = 5.0, fiber = 6.0, oilG = 4.0, sugarG = 0.0
    )
    private val chai = MealItem(
        name = "chai", qty = 1.0, unit = "cup",
        kcal = 80.0, protein = 2.0, carbs = 12.0, fat = 2.5, fiber = 0.0, oilG = 0.0, sugarG = 8.0
    )

    private fun entry(tags: String? = null, items: String? = null) = NutritionEntryEntity(
        id = 7, foodText = "2 roti and dal", calories = 420.0, proteinG = 16.0, carbsG = 64.0, fatG = 11.0,
        timestamp = 1L, qualityTags = tags, items = items
    )

    @Test
    fun `totals multiply per-unit values by quantity`() {
        val totals = MealItems(listOf(roti)).totals()
        assertEquals(240.0, totals.kcal, 0.01)
        assertEquals(7.0, totals.protein, 0.01)
        assertEquals(40.0, totals.carbs, 0.01)
        assertEquals(6.0, totals.fat, 0.01)
        assertEquals(5.0, totals.fiber, 0.01)
    }

    @Test
    fun `high oil adds half the oil as fat at nine kcal per gram`() {
        val totals = MealItems(listOf(roti), oil = Level.HIGH).totals()
        assertEquals(8.0, totals.fat, 0.01)
        assertEquals(258.0, totals.kcal, 0.01)
    }

    @Test
    fun `low oil removes half the oil`() {
        val totals = MealItems(listOf(roti), oil = Level.LOW).totals()
        assertEquals(4.0, totals.fat, 0.01)
        assertEquals(222.0, totals.kcal, 0.01)
    }

    @Test
    fun `low sugar removes half the sugar at four kcal per gram`() {
        val totals = MealItems(listOf(chai), sugar = Level.LOW).totals()
        assertEquals(8.0, totals.carbs, 0.01)
        assertEquals(64.0, totals.kcal, 0.01)
    }

    @Test
    fun `values never go below zero`() {
        val oily = MealItem("pakora", 1.0, "piece", kcal = 5.0, protein = 0.0, carbs = 0.0, fat = 0.5, fiber = 0.0, oilG = 4.0, sugarG = 0.0)
        val totals = MealItems(listOf(oily), oil = Level.LOW).totals()
        assertTrue(totals.fat >= 0.0)
        assertTrue(totals.kcal >= 0.0)
    }

    @Test
    fun `items survive saving and loading`() {
        val items = MealItems(listOf(roti, chai), oil = Level.HIGH, sugar = Level.LOW)
        assertEquals(items, MealItems.decode(items.encode()))
    }

    @Test
    fun `broken or empty saved items load as null`() {
        assertNull(MealItems.decode(null))
        assertNull(MealItems.decode(""))
        assertNull(MealItems.decode("{"))
        assertNull(MealItems.decode("{\"v\":1,\"items\":[]}"))
    }

    @Test
    fun `an older meal opens as one serving without oil or sugar estimates`() {
        val legacy = MealItems.legacy(entry())
        assertEquals(1, legacy.items.size)
        assertEquals("2 roti and dal", legacy.items[0].name)
        assertEquals(1.0, legacy.items[0].qty, 0.0)
        assertEquals("serving", legacy.items[0].unit)
        assertFalse(legacy.hasOil)
        assertFalse(legacy.hasSugar)
        val totals = legacy.totals()
        assertEquals(420.0, totals.kcal, 0.01)
        assertEquals(16.0, totals.protein, 0.01)
        assertEquals(11.0, totals.fat, 0.01)
    }

    @Test
    fun `oil and sugar switches only apply when the meal has some`() {
        val items = MealItems(listOf(roti, dal))
        assertTrue(items.hasOil)
        assertFalse(items.hasSugar)
        assertTrue(MealItems(listOf(chai)).hasSugar)
    }

    @Test
    fun `gemini items are stored per unit`() {
        val arr = JSONArray(
            """[{"name":"roti","quantity":2,"unit":"piece","calories":240,"protein":7,"carbs":40,"fat":6,"fiber":5,"oilG":4,"sugarG":0}]"""
        )
        val item = MealItems.fromGemini(arr)!!.items.single()
        assertEquals(2.0, item.qty, 0.0)
        assertEquals(120.0, item.kcal, 0.01)
        assertEquals(2.0, item.oilG, 0.01)
    }

    @Test
    fun `gemini items with a zero quantity or no calories are rejected`() {
        assertNull(MealItems.fromGemini(null))
        assertNull(MealItems.fromGemini(JSONArray("[]")))
        assertNull(MealItems.fromGemini(JSONArray("""[{"name":"roti","quantity":0,"unit":"piece","calories":240}]""")))
        assertNull(MealItems.fromGemini(JSONArray("""[{"name":"water","quantity":1,"unit":"glass","calories":0}]""")))
    }

    @Test
    fun `meal name is rebuilt from the items`() {
        assertEquals("3 roti, 1 katori dal", MealItems(listOf(roti.copy(qty = 3.0), dal)).foodText())
        assertEquals("1.5 katori dal", MealItems(listOf(dal.copy(qty = 1.5))).foodText())
        assertEquals("150 g rice", MealItems(listOf(roti.copy(name = "rice", qty = 150.0, unit = "g"))).foodText())
    }

    @Test
    fun `quantities step by 25 for grams and millilitres and by half otherwise`() {
        assertEquals(0.5, roti.step, 0.0)
        assertEquals(25.0, roti.copy(unit = "g").step, 0.0)
        assertEquals(25.0, roti.copy(unit = "ml").step, 0.0)
    }

    @Test
    fun `editing rewrites the meal totals name and tags`() {
        val tags = MealTags(1, fried = false, addedSugar = false, vegFruitServings = 1.0, fiberG = 8.5, treat = false, foodType = "dal")
        val original = MealItems(listOf(roti, dal))
        val start = entry(tags = tags.encode(), items = original.encode()).withItems(original)
        val edited = start.withItems(MealItems(listOf(roti.copy(qty = 4.0), dal)))

        assertEquals("4 roti, 1 katori dal", edited.foodText)
        assertEquals(660.0, edited.calories, 0.01)
        assertEquals(23.0, edited.proteinG, 0.01)
        assertEquals(17.0, edited.fatG, 0.01)
        assertEquals(MealItems(listOf(roti.copy(qty = 4.0), dal)), MealItems.decode(edited.items))
        val newTags = MealTags.decode(edited.qualityTags)!!
        assertEquals(16.0, newTags.fiberG, 0.01)
        // Veg/fruit servings follow the change in calories: 420 -> 660.
        assertEquals(1.6, newTags.vegFruitServings, 0.05)
        assertEquals(7L, edited.id)
    }

    @Test
    fun `a repeat of an edited meal scales its items`() {
        val history = listOf(
            NutritionEntryEntity(foodText = "2 roti", calories = 240.0, proteinG = 7.0, carbsG = 40.0, fatG = 6.0, timestamp = 0L, items = MealItems(listOf(roti), oil = Level.HIGH).encode())
        )
        val result = RepeatMealCache.findRepeatMeal("4 roti", history)!!
        assertEquals(480.0, result.calories, 0.1)
        assertEquals(4.0, result.items!!.items.single().qty, 0.0)
        assertEquals(Level.HIGH, result.items!!.oil)
    }

    @Test
    fun `an exact repeat copies the items`() {
        val items = MealItems(listOf(roti, dal))
        val history = listOf(
            NutritionEntryEntity(foodText = "2 roti, 1 katori dal", calories = 420.0, proteinG = 16.0, carbsG = 64.0, fatG = 11.0, timestamp = 0L, items = items.encode())
        )
        assertEquals(items, RepeatMealCache.findRepeatMeal("2 roti, 1 katori dal", history)!!.items)
    }
}
