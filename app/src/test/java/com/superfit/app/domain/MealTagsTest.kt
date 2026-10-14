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
    fun `the AI's tags win over the name, and names are a fallback`() {
        val proteinIceCream = entry("protein ice cream", tags = MealTags(2, false, false, 0.0, 1.0, treat = false, foodType = "ice cream").encode())
        assertNull(CoachPatternEngine.treatCategoryOf(proteinIceCream))
        val kulfi = entry("kulfi", tags = MealTags(2, false, true, 0.0, 0.0, treat = true, foodType = "kulfi").encode())
        assertEquals("kulfi", CoachPatternEngine.treatCategoryOf(kulfi))

        val estimated = MealTags.of(entry("dal rice and bhindi sabzi"))
        assertTrue(estimated.estimated)
        assertEquals(1, estimated.processing)
        assertEquals(1.0, estimated.vegFruitServings, 0.0)
        assertFalse(estimated.treat)
        assertTrue(MealTags.of(entry("samosa")).treat)
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
}
