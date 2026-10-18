package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PackagedFoodTest {

    private val full = """
        {"status":1,"product":{"product_name":"Aloo Bhujia","serving_quantity":"30",
         "nutriments":{"energy-kcal_100g":550,"proteins_100g":10,"carbohydrates_100g":45,"fat_100g":36,
                       "fiber_100g":5,"energy-kcal_serving":165}}}
    """.trimIndent()

    @Test
    fun `full Open Food Facts product`() {
        val food = PackagedFoodParser.fromOpenFoodFacts(full)!!
        assertEquals("Aloo Bhujia", food.name)
        assertEquals(550.0, food.per100g.kcal, 0.001)
        assertEquals(30.0, food.servingGrams!!, 0.001)
        assertEquals(165.0, food.perServing!!.kcal, 0.001)
        assertEquals(PackAmount.Servings(1.0), food.defaultAmount())
    }

    @Test
    fun `serving size without per-serving numbers is worked out from per 100 g`() {
        val json = full.replace(""","energy-kcal_serving":165""", "")
        val food = PackagedFoodParser.fromOpenFoodFacts(json)!!
        assertEquals(165.0, food.perServing!!.kcal, 0.001)
        assertEquals(3.0, food.perServing!!.protein, 0.001)
    }

    @Test
    fun `no serving size means grams`() {
        val json = """{"status":1,"product":{"product_name":"Oats","nutriments":{"energy-kcal_100g":"389"}}}"""
        val food = PackagedFoodParser.fromOpenFoodFacts(json)!!
        assertNull(food.servingGrams)
        assertNull(food.perServing)
        assertEquals(389.0, food.per100g.kcal, 0.001)
        assertEquals(PackAmount.Grams(100.0), food.defaultAmount())
    }

    @Test
    fun `kilojoules only are converted`() {
        val json = """{"status":1,"product":{"nutriments":{"energy_100g":2301}}}"""
        val food = PackagedFoodParser.fromOpenFoodFacts(json)!!
        assertEquals(550.0, food.per100g.kcal, 0.5)
        assertNull(food.name)
    }

    @Test
    fun `missing calories or unknown product is not found`() {
        assertNull(PackagedFoodParser.fromOpenFoodFacts("""{"status":1,"product":{"nutriments":{"proteins_100g":3}}}"""))
        assertNull(PackagedFoodParser.fromOpenFoodFacts("""{"status":0,"status_verbose":"product not found"}"""))
        assertNull(PackagedFoodParser.fromOpenFoodFacts("not json"))
    }

    @Test
    fun `label JSON is read`() {
        val json = """```json
            {"name":"Digestive biscuits","per100g":{"kcal":480,"protein":7,"carbs":65,"fat":21,"fiber":4},
             "servingGrams":25,"perServing":null}
        ```"""
        val food = PackagedFoodParser.fromLabelJson(json)!!
        assertEquals("Digestive biscuits", food.name)
        assertEquals(480.0, food.per100g.kcal, 0.001)
        assertEquals(25.0, food.servingGrams!!, 0.001)
        assertEquals(120.0, food.perServing!!.kcal, 0.001)
    }

    @Test
    fun `unreadable label is null`() {
        assertNull(PackagedFoodParser.fromLabelJson("""{"name":"invalid","per100g":{"kcal":0}}"""))
        assertNull(PackagedFoodParser.fromLabelJson("""{"name":null,"per100g":{"kcal":0,"protein":1}}"""))
        assertNull(PackagedFoodParser.fromLabelJson("""{"name":null}"""))
    }

    @Test
    fun `servings become one serving item`() {
        val meal = PackagedFoodParser.fromOpenFoodFacts(full)!!.toMealItems(PackAmount.Servings(2.0))
        assertEquals(1, meal.items.size)
        assertEquals("serving", meal.items[0].unit)
        assertEquals(2.0, meal.items[0].qty, 0.001)
        assertEquals(330.0, meal.totals().kcal, 0.001)
        assertFalse(meal.hasOil)
        assertFalse(meal.hasSugar)
    }

    @Test
    fun `grams become one gram item`() {
        val meal = PackagedFoodParser.fromOpenFoodFacts(full)!!.toMealItems(PackAmount.Grams(50.0))
        assertEquals("g", meal.items[0].unit)
        assertEquals(50.0, meal.items[0].qty, 0.001)
        assertEquals(275.0, meal.totals().kcal, 0.001)
        assertEquals("Aloo Bhujia", meal.items[0].name)
    }

    @Test
    fun `amounts step and never reach zero`() {
        assertEquals(PackAmount.Servings(0.5), PackAmount.Servings(0.5).stepDown())
        assertEquals(PackAmount.Servings(0.5), PackAmount.Servings(1.0).stepDown())
        assertEquals(PackAmount.Servings(1.0), PackAmount.Servings(0.5).stepUp())
        assertEquals(PackAmount.Servings(2.0), PackAmount.Servings(1.0).stepUp())
        assertEquals(PackAmount.Grams(25.0), PackAmount.Grams(25.0).stepDown())
        assertEquals(PackAmount.Grams(125.0), PackAmount.Grams(100.0).stepUp())
    }
}
