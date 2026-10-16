package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONObject
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
class MealTagsTest {

    private fun entry(text: String, id: Long = 0, tags: String? = null) =
        NutritionEntryEntity(id = id, foodText = text, calories = 500.0, proteinG = 20.0, carbsG = 50.0, fatG = 15.0, timestamp = 0L, qualityTags = tags)

    @Test
    fun `tags survive saving and loading`() {
        val tags = MealTags(2, fried = true, addedSugar = false, vegFruitServings = 1.5, fiberG = 6.0, treat = true, foodType = "pav|bhaji")
        val back = MealTags.decode(tags.encode())!!
        assertEquals(tags.copy(foodType = "pav bhaji"), back)
        assertNull(MealTags.decode(null))
        assertNull(MealTags.decode("garbage"))
    }

    @Test
    fun `reads the AI's meal answer`() {
        val json = JSONObject("""{"foodText":"veg burger","calories":450,"processing":3,"fried":false,"addedSugar":false,"vegFruitServings":0.5,"fiber":4,"treat":true,"foodType":"Burger"}""")
        val tags = MealTags.fromJson(json)!!
        assertEquals(3, tags.processing)
        assertTrue(tags.treat)
        assertEquals("burger", tags.foodType)
        assertNull(MealTags.fromJson(JSONObject("""{"foodText":"x","calories":1}""")))
    }

    @Test
    fun `meals without AI tags claim nothing about their food`() {
        val proteinIceCream = entry("protein ice cream", tags = MealTags(2, false, false, 0.0, 1.0, treat = false, foodType = "ice cream").encode())
        assertNull(CoachPatternEngine.treatCategoryOf(proteinIceCream))
        val kulfi = entry("kulfi", tags = MealTags(2, false, true, 0.0, 0.0, treat = true, foodType = "kulfi").encode())
        assertEquals("kulfi", CoachPatternEngine.treatCategoryOf(kulfi))

        // No word lists: an untagged samosa or salad is just "not tagged yet".
        for (text in listOf("samosa", "dal rice and bhindi sabzi")) {
            val tags = MealTags.of(entry(text))
            assertTrue(tags.estimated)
            assertFalse(tags.treat)
            assertEquals(0.0, tags.fiberG, 0.0)
            assertEquals(0.0, tags.vegFruitServings, 0.0)
        }
        assertNull(CoachPatternEngine.treatCategoryOf(entry("samosa")))
    }

    @Test
    fun `batch tagging matches answers to the right meals`() {
        val meals = listOf(entry("poha", id = 11), entry("pizza", id = 12))
        val response = """{"meals":[{"n":2,"processing":3,"fried":false,"addedSugar":false,"vegFruitServings":0,"fiber":2,"treat":true,"foodType":"pizza"},
            {"n":1,"processing":1,"fried":false,"addedSugar":false,"vegFruitServings":0.5,"fiber":2,"treat":false,"foodType":"poha"}]}"""
        val tags = MealTagger.parse(response, meals)
        assertEquals("pizza", tags[12L]!!.foodType)
        assertEquals(1, tags[11L]!!.processing)
        assertTrue(MealTagger.parse("not json", meals).isEmpty())
    }

    @Test
    fun `the AI's reason, swap and hidden oil are read and saved`() {
        val json = JSONObject("""{"foodText":"misal pav","calories":550,"processing":2,"fried":false,"addedSugar":false,"vegFruitServings":0.5,"fiber":9,"treat":false,"foodType":"misal pav",
            "hiddenOil":true,"why":"Sprouts give protein and fibre, but the tarri is oily.","swap":{"alternative":"usal with 1 pav and onion","kcalSaved":150,"benefit":"Same taste, far less oil"}}""")
        val tags = MealTags.fromJson(json)!!
        assertTrue(tags.hiddenOil)
        assertTrue(tags.hasAdvice)
        assertEquals(MealSwap("usal with 1 pav and onion", 150, "Same taste, far less oil"), tags.swap)
        assertEquals(tags, MealTags.decode(tags.encode()))

        val healthy = MealTags.fromJson(JSONObject("""{"processing":1,"why":"Whole food.","swap":null}"""))!!
        assertNull(healthy.swap)
        assertFalse(healthy.hiddenOil)
    }

    @Test
    fun `tags saved before reasons and swaps existed still load`() {
        val old = "v1|p=3|fr=1|sg=0|vf=0.0|fb=2.0|tr=1|ty=samosa"
        val tags = MealTags.decode(old)!!
        assertEquals(3, tags.processing)
        assertFalse(tags.hasAdvice)
        assertNull(tags.swap)
        assertFalse(tags.hiddenOil)
        // Without the new fields, the saved text stays exactly what older app versions write
        assertEquals(old, tags.encode())
    }

    @Test
    fun `a bigger portion scales fibre, veg and the calories a swap saves`() {
        val tags = MealTags(2, false, false, 1.0, 4.0, false, "poha", swap = MealSwap("vegetable upma", 50, "More fibre"))
        val doubled = tags.scaled(2.0)
        assertEquals(8.0, doubled.fiberG, 0.0)
        assertEquals(2.0, doubled.vegFruitServings, 0.0)
        assertEquals(100, doubled.swap!!.kcalSaved)
    }
}
