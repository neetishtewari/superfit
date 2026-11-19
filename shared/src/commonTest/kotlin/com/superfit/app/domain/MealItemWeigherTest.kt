package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MealItemWeigherTest {

    private val dal = MealItem(
        name = "dal", qty = 2.0, unit = "katori",
        kcal = 180.0, protein = 9.0, carbs = 24.0, fat = 5.0, fiber = 6.0, oilG = 4.0, sugarG = 0.0
    )
    private val chai = MealItem(
        name = "chai", qty = 1.0, unit = "cup",
        kcal = 80.0, protein = 2.0, carbs = 12.0, fat = 2.5, fiber = 0.0, oilG = 0.0, sugarG = 8.0
    )
    private val rice = MealItem(
        name = "rice", qty = 150.0, unit = "g",
        kcal = 1.3, protein = 0.03, carbs = 0.28, fat = 0.0, fiber = 0.0, oilG = 0.0, sugarG = 0.0,
        weight = 1.0, metric = "g"
    )

    @Test
    fun `an item the AI never weighed still offers grams`() {
        assertTrue(dal.needsWeight)
        assertFalse(dal.canSwitchUnit)
        assertFalse(rice.needsWeight)
        assertFalse(dal.withWeight(150.0, "g").needsWeight)
    }

    @Test
    fun `weights from the answer make the switch work per unit`() {
        val out = MealItemWeigher.withWeights(
            """{"weights":[{"n":1,"grams":300,"liquid":false},{"n":2,"grams":150,"liquid":true}]}""",
            listOf(dal, chai, rice)
        )
        assertEquals(150.0, out[0].weight)
        assertEquals("g", out[0].metric)
        assertTrue(out[0].canSwitchUnit)
        val grams = out[0].switchUnit()
        assertEquals(300.0, grams.qty)
        assertEquals("g", grams.unit)
        assertEquals(360.0, grams.kcal * grams.qty, 0.001)
        assertEquals("ml", out[1].metric)
        assertEquals(rice, out[2])
    }

    @Test
    fun `an unreadable answer leaves the items alone`() {
        val items = listOf(dal, chai)
        assertEquals(items, MealItemWeigher.withWeights("not json", items))
        assertEquals(items, MealItemWeigher.withWeights("""{"weights":[{"n":1,"grams":0}]}""", items))
        // Fenced answers and a bare list are read too.
        assertEquals(150.0, MealItemWeigher.withWeights("```json\n[{\"n\":1,\"grams\":300}]\n```", items)[0].weight)
    }
}
