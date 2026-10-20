package com.superfit.app.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NutritionParserTest {

    private val withItems = """
        {"foodText":"2 rotis with dal","calories":400,"protein":15,"carbs":60,"fat":10,
         "items":[
           {"name":"roti","quantity":2,"unit":"piece","calories":240,"protein":7,"carbs":40,"fat":6,"fiber":5,"oilG":4,"sugarG":0},
           {"name":"dal","quantity":1,"unit":"katori","calories":180,"protein":9,"carbs":24,"fat":5,"fiber":6,"oilG":4,"sugarG":0}
         ]}
    """.trimIndent()

    @Test
    fun `meal totals come from the items when the items are usable`() {
        val result = ParsedNutritionResult.fromJson(JSONObject(withItems), "2 roti dal")
        assertEquals(420.0, result.calories, 0.01)
        assertEquals(16.0, result.proteinG, 0.01)
        assertEquals(64.0, result.carbsG, 0.01)
        assertEquals(11.0, result.fatG, 0.01)
        assertEquals(2, result.items!!.items.size)
        assertEquals("2 rotis with dal", result.foodText)
    }

    @Test
    fun `without usable items the meal is saved as before`() {
        val noItems = ParsedNutritionResult.fromJson(
            JSONObject("""{"foodText":"poha","calories":250,"protein":5,"carbs":45,"fat":6}"""),
            "poha"
        )
        assertEquals(250.0, noItems.calories, 0.01)
        assertNull(noItems.items)

        val badItems = ParsedNutritionResult.fromJson(
            JSONObject("""{"foodText":"poha","calories":250,"protein":5,"carbs":45,"fat":6,"items":[{"name":"poha","quantity":0,"calories":250}]}"""),
            "poha"
        )
        assertEquals(250.0, badItems.calories, 0.01)
        assertNull(badItems.items)
    }

    @Test
    fun `gibberish keeps no items`() {
        val result = ParsedNutritionResult.fromJson(
            JSONObject("""{"foodText":"invalid","calories":0,"protein":0,"carbs":0,"fat":0,"items":[]}"""),
            "hmm"
        )
        assertEquals("invalid", result.foodText)
        assertNull(result.items)
    }
}
