package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `quantities step by half for household units and by size for grams and millilitres`() {
        assertEquals(0.5, roti.step, 0.0)
        assertEquals(25.0, roti.copy(qty = 300.0, unit = "g").step, 0.0)
        assertEquals(25.0, roti.copy(qty = 300.0, unit = "ml").step, 0.0)
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
        assertEquals(MealItems(listOf(roti.copy(qty = 4.0), dal), checked = true), MealItems.decode(edited.items))
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

    // A glass the AI couldn't tell apart: buttermilk (its guess), milk or lassi.
    private val drinkJson = """[{"name":"buttermilk","quantity":1,"unit":"glass","calories":60,"protein":3,"carbs":5,"fat":2,"fiber":0,"oilG":0,"sugarG":0,
        "alternatives":[{"name":"milk","calories":150,"protein":8,"carbs":12,"fat":8,"fiber":0,"oilG":0,"sugarG":0},
        {"name":"Buttermilk","calories":60},{"name":"","calories":10},
        {"name":"lassi","calories":220,"protein":7,"carbs":30,"fat":7,"sugarG":20},
        {"name":"kefir","calories":110},{"name":"whey","calories":120}]}]"""

    @Test
    fun `the ai's look-alikes become choices with its guess first`() {
        val item = MealItems.fromGemini(JSONArray(drinkJson))!!.items.single()
        assertTrue(item.isUnsure)
        // Duplicates and blank names are dropped, and at most three options follow the guess.
        assertEquals(listOf("buttermilk", "milk", "lassi", "kefir"), item.choices.map { it.name })
        assertEquals(150.0, item.choices[1].kcal, 0.01)
        assertEquals(20.0, item.choices[2].sugarG, 0.01)
    }

    @Test
    fun `an item the ai was sure about has no choices`() {
        val item = MealItems.fromGemini(JSONArray("""[{"name":"roti","quantity":2,"unit":"piece","calories":240}]"""))!!.items.single()
        assertFalse(item.isUnsure)
        assertTrue(item.choices.isEmpty())
    }

    @Test
    fun `choosing another option swaps the numbers and keeps the amount`() {
        val item = MealItems.fromGemini(JSONArray(drinkJson))!!.items.single().copy(qty = 2.0)
        val milk = item.choose(1)
        assertEquals("milk", milk.name)
        assertEquals(2.0, milk.qty, 0.0)
        assertEquals("glass", milk.unit)
        assertEquals(300.0, MealItems(listOf(milk)).totals().kcal, 0.01)
        // The choices stay, so the user can switch back.
        assertEquals("buttermilk", milk.choose(0).name)
        assertEquals(milk, milk.choose(9))
    }

    @Test
    fun `choices, checked and what was said survive saving`() {
        val items = MealItems.fromGemini(JSONArray(drinkJson))!!.copy(said = "a glass of chaas")
        val decoded = MealItems.decode(items.encode())!!
        assertEquals(items, decoded)
        assertEquals(MealItems(listOf(roti), checked = true), MealItems.decode(MealItems(listOf(roti), checked = true).encode()))
    }

    @Test
    fun `unsure items need checking until the user has looked at the meal`() {
        val items = MealItems.fromGemini(JSONArray(drinkJson))!!
        assertEquals(1, items.copy(items = items.items + roti).itemsToCheck)
        assertEquals(0, items.copy(checked = true).itemsToCheck)
        assertEquals(0, MealItems(listOf(roti, dal)).itemsToCheck)
    }

    @Test
    fun `replacing an item changes only that item`() {
        val meal = MealItems(listOf(roti, dal), oil = Level.HIGH)
        val chana = dal.copy(name = "chana", kcal = 210.0, choices = listOf(dal, roti))
        val replaced = meal.replace(1, chana)
        assertEquals(roti, replaced.items[0])
        assertEquals("chana", replaced.items[1].name)
        assertTrue(replaced.items[1].choices.isEmpty())
        assertEquals(Level.HIGH, replaced.oil)
        assertEquals(meal, meal.replace(5, chana))
    }

    @Test
    fun `a different food drops the old tags so the meal is judged again`() {
        val tags = MealTags(1, fried = false, addedSugar = false, vegFruitServings = 1.0, fiberG = 8.5, treat = false, foodType = "dal")
        val original = MealItems(listOf(roti, dal))
        val start = entry(tags = tags.encode(), items = original.encode())

        val renamed = start.withItems(original.replace(1, dal.copy(name = "chicken curry")))
        assertNull(renamed.qualityTags)
        assertEquals("2 roti, 1 katori chicken curry", renamed.foodText)
        assertTrue(MealItems.decode(renamed.items)!!.checked)

        // Removing a food or changing an amount keeps the tags.
        assertNotNull(start.withItems(MealItems(listOf(roti))).qualityTags)
        assertNotNull(start.withItems(MealItems(listOf(roti.copy(qty = 3.0), dal))).qualityTags)
    }

    @Test
    fun `a corrected meal comes back when the user says the same words again`() {
        val corrected = MealItems(listOf(roti, dal.copy(name = "chana")), checked = true, said = "2 roti and chaas")
        val history = listOf(
            NutritionEntryEntity(foodText = "2 roti, 1 katori chana", calories = 420.0, proteinG = 16.0, carbsG = 64.0, fatG = 11.0, timestamp = 0L, items = corrected.encode())
        )
        val result = RepeatMealCache.findRepeatMeal("2 Roti and chaas!", history)!!
        assertEquals("2 roti, 1 katori chana", result.foodText)
        assertEquals(corrected, result.items)
    }

    @Test
    fun `typed meals remember what was said and photos do not`() {
        val json = org.json.JSONObject().put("foodText", "chaas").put("items", JSONArray(drinkJson))
        assertEquals("a glass of chaas", ParsedNutritionResult.fromJson(json, " a glass of chaas ").items!!.said)
        assertNull(ParsedNutritionResult.fromJson(json, "photo").items!!.said)
    }

    @Test
    fun `a replacement keeps the old amount unless the ai gives one`() {
        val momos = MealItem("veg momos", 6.0, "piece", 35.0, 1.0, 5.0, 1.0, 0.5, 0.5, 0.0)
        val kept = MealItemReplacer.parse("""{"name":"chicken momos","calories":270,"protein":18}""", momos)!!.single()
        assertEquals("chicken momos", kept.name)
        assertEquals(6.0, kept.qty, 0.0)
        assertEquals("piece", kept.unit)
        assertEquals(45.0, kept.kcal, 0.01)
        assertEquals(3.0, kept.protein, 0.01)

        val given = MealItemReplacer.parse("```json\n{\"name\":\"chicken momos\",\"quantity\":8,\"unit\":\"piece\",\"calories\":360}\n```", momos)!!.single()
        assertEquals(8.0, given.qty, 0.0)
        assertEquals(45.0, given.kcal, 0.01)

        assertNull(MealItemReplacer.parse("not json", momos))
        assertNull(MealItemReplacer.parse("""{"calories":100}""", momos))
    }

    @Test
    fun `a replacement is read like any meal answer`() {
        val poori = MealItem("poori", 1.0, "piece", 100.0, 2.0, 12.0, 5.0, 0.5, 4.0, 0.0)
        val answer = """{"foodText":"1 oats cheela with chutney","calories":190,"items":[
            {"name":"oats cheela","quantity":1,"unit":"piece","calories":150,"protein":6,"oilG":5,
             "alternatives":[{"name":"besan cheela","calories":170}]},
            {"name":"green chutney","quantity":1,"unit":"tbsp","calories":40}]}"""
        val foods = MealItemReplacer.parse(answer, poori)!!
        assertEquals(listOf("oats cheela", "green chutney"), foods.map { it.name })
        assertEquals(150.0, foods[0].kcal, 0.01)
        assertTrue(foods[0].choices.isEmpty())
    }

    @Test
    fun `a replacement is found inside a list or a wrapper`() {
        val poori = MealItem("poori", 1.0, "piece", 100.0, 2.0, 12.0, 5.0, 0.5, 4.0, 0.0)
        val inList = MealItemReplacer.parse("""[{"name":"oats cheela","quantity":1,"unit":"piece","calories":150}]""", poori)!!.single()
        assertEquals("oats cheela", inList.name)
        assertEquals(150.0, inList.kcal, 0.01)

        val wrapped = MealItemReplacer.parse("""{"item":{"name":"oats cheela","quantity":null,"unit":"","calories":300}}""", poori)!!.single()
        assertEquals(1.0, wrapped.qty, 0.0)
        assertEquals("piece", wrapped.unit)
        assertEquals(300.0, wrapped.kcal, 0.01)
    }

    @Test
    fun `one item can become several`() {
        val meal = MealItems(listOf(roti, dal, chai))
        val replaced = meal.replace(1, listOf(dal.copy(name = "chana"), dal.copy(name = "raita")))
        assertEquals(listOf("roti", "chana", "raita", "chai"), replaced.items.map { it.name })
        assertEquals(meal, meal.replace(1, emptyList<MealItem>()))
    }

    private val weighedDal = dal.copy(weight = 150.0, metric = "g")
    private val weighedChai = chai.copy(weight = 150.0, metric = "ml")

    @Test
    fun `AI weight and liquid become per-unit weight and g or ml`() {
        val arr = JSONArray(
            """[{"name":"aloo paratha","quantity":2,"unit":"piece","calories":500,"grams":240,"liquid":false},
               {"name":"chai","quantity":1,"unit":"cup","calories":90,"grams":150,"liquid":true},
               {"name":"milk","quantity":200,"unit":"ml","calories":130},
               {"name":"rusk","quantity":2,"unit":"piece","calories":100}]"""
        )
        val items = MealItems.fromGemini(arr)!!.items
        assertEquals(120.0, items[0].weight!!, 0.01)
        assertEquals("g", items[0].metric)
        assertEquals("ml", items[1].metric)
        assertEquals(1.0, items[2].weight!!, 0.01)
        assertEquals("ml", items[2].metric)
        assertNull(items[3].weight)
        assertNull(items[3].metric)
    }

    @Test
    fun `labels show the weight beside household amounts`() {
        assertEquals("1 katori dal (150 g)", weighedDal.label())
        assertEquals("1 cup chai (150 ml)", weighedChai.label())
        assertEquals("1 katori dal", dal.label())
        assertEquals("about 150 g", weighedDal.weightText())
        assertNull(dal.weightText())
    }

    @Test
    fun `switching to grams and back keeps the calories`() {
        val meal = MealItems(listOf(weighedDal))
        val grams = weighedDal.switchUnit()
        assertEquals("g", grams.unit)
        assertEquals(150.0, grams.qty, 0.01)
        assertEquals(meal.totals(), MealItems(listOf(grams)).totals())
        assertEquals("150 g dal", grams.label())
        val back = grams.switchUnit()
        assertEquals("katori", back.unit)
        assertEquals(1.0, back.qty, 0.01)
        assertEquals(meal.totals(), MealItems(listOf(back)).totals())
    }

    @Test
    fun `a typed amount in grams changes calories by weight`() {
        val grams = weighedDal.switchUnit().copy(qty = 75.0)
        assertEquals(90.0, MealItems(listOf(grams)).totals().kcal, 0.01)
    }

    @Test
    fun `items without a weight can't switch`() {
        assertFalse(dal.canSwitchUnit)
        assertEquals(dal, dal.switchUnit())
        assertFalse(dal.copy(qty = 200.0, unit = "g", weight = 1.0, metric = "g").canSwitchUnit)
        assertTrue(weighedDal.canSwitchUnit)
    }

    @Test
    fun `gram steps grow with the amount`() {
        val g = weighedDal.switchUnit()
        assertEquals(5.0, g.copy(qty = 10.0).step, 0.0)
        assertEquals(10.0, g.copy(qty = 150.0).step, 0.0)
        assertEquals(25.0, g.copy(qty = 300.0).step, 0.0)
        assertEquals(0.5, weighedDal.step, 0.0)
    }

    @Test
    fun `weights and switched units survive saving`() {
        val meal = MealItems(listOf(weighedDal.switchUnit(), weighedChai, roti))
        val decoded = MealItems.decode(meal.encode())!!
        assertEquals(meal, decoded)
        assertEquals("katori", decoded.items[0].switchUnit().unit)
    }
}
