package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CalorieSourcesTest {

    @Before
    fun noApprovedFoods() {
        PersonalNutrition.approvedFoods = emptySet()
    }

    private fun item(name: String, kcal: Double, qty: Double = 1.0) =
        MealItem(name = name, qty = qty, unit = "piece", kcal = kcal, protein = 0.0, carbs = 0.0, fat = 0.0, fiber = 0.0, oilG = 0.0, sugarG = 0.0)

    private fun tags(verdict: String = "good", treat: Boolean = false, treatItems: List<String>? = null, swap: MealSwap? = null) =
        MealTags(processing = 1, fried = false, addedSugar = false, vegFruitServings = 0.0, fiberG = 0.0, treat = treat, foodType = "", rating = 7, verdict = verdict, treatItems = treatItems, swap = swap)

    private fun meal(kcal: Double, vararg items: MealItem, tags: MealTags? = tags(), text: String = items.joinToString { it.name }) = NutritionEntryEntity(
        foodText = text,
        calories = kcal,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
        timestamp = 1L,
        qualityTags = tags?.encode(),
        items = if (items.isEmpty()) null else MealItems(items.toList()).encode()
    )

    @Test
    fun pizzaTwiceAWeekOutranksChaiTwiceADay() {
        val chai = (1..14).map { meal(70.0, item("chai", 70.0)) }
        val pizza = (1..2).map { meal(800.0, item("pizza", 800.0), tags = tags("treat", treat = true)) }
        val c = CalorieSourcesEngine.build(chai + pizza)!!
        assertEquals(listOf("Pizza", "Chai"), c.top.map { it.name })
        assertEquals(1600, c.top[0].kcal)
        assertEquals(2, c.top[0].times)
        assertEquals(14, c.top[1].times)
        assertEquals("Chai", c.mostLogged?.name)
    }

    @Test
    fun aMealsCaloriesAreSharedByItsItemsAndAddUpToWhatWasLogged() {
        // Items say 100 + 300 but the meal was logged at 600 (more oil): shares stay 1:3.
        val parts = CalorieSourcesEngine.parts(meal(600.0, item("roti", 50.0, qty = 2.0), item("dal", 300.0)))
        assertEquals(150.0, parts.first { it.key == "roti" }.kcal, 0.01)
        assertEquals(450.0, parts.first { it.key == "dal" }.kcal, 0.01)
    }

    @Test
    fun onlyTheMarkedItemOfAThaliCountsAsTreatCalories() {
        val thali = meal(700.0, item("roti", 200.0), item("dal", 300.0), item("gulab jamun", 200.0), tags = tags("good", treatItems = listOf("gulab jamun")))
        val parts = CalorieSourcesEngine.parts(thali)
        assertEquals(listOf("gulab jamun"), parts.filter { it.treat }.map { it.key })
    }

    @Test
    fun anOlderTreatMealCountsOnlyWhenItIsASingleFood() {
        val single = CalorieSourcesEngine.parts(meal(400.0, item("samosa", 400.0), tags = tags("treat", treat = true)))
        val mixed = CalorieSourcesEngine.parts(meal(700.0, item("roti", 200.0), item("gulab jamun", 500.0), tags = tags("treat", treat = true)))
        assertTrue(single.single().treat)
        assertTrue(mixed.none { it.treat })
    }

    @Test
    fun aTeaspoonOfCakeIsPortionControlNotTreatCalories() {
        val taste = meal(40.0, item("birthday cake", 40.0), tags = tags("treat", treat = true, treatItems = listOf("birthday cake")))
        assertFalse(CalorieSourcesEngine.parts(taste).single().treat)
        val c = CalorieSourcesEngine.build(listOf(taste) + (1..5).map { meal(300.0, item("dal", 300.0)) })!!
        assertNull(c.biggestTreat)
    }

    @Test
    fun foodsMarkedFineForMeNeverCountAsTreats() {
        PersonalNutrition.approvedFoods = setOf("dark chocolate")
        val parts = CalorieSourcesEngine.parts(meal(150.0, item("dark chocolate", 150.0), tags = tags("treat", treat = true, treatItems = listOf("dark chocolate"))))
        assertFalse(parts.single().treat)
    }

    @Test
    fun aTreatInTheTopFiveGetsItsRankAndTheEverydayFoodItOutweighs() {
        val chai = (1..14).map { meal(70.0, item("chai", 70.0)) }
        val pizza = (1..2).map { meal(800.0, item("pizza", 800.0), tags = tags("treat", treat = true, swap = MealSwap("thin-crust veg pizza", 300, "less cheese"))) }
        val c = CalorieSourcesEngine.build(chai + pizza)!!
        assertEquals(1, c.treatRank)
        assertEquals("Chai", c.outweighs?.name)
        assertEquals("thin-crust veg pizza", c.swap?.alternative)
        val finding = InsightsEngine.treatFinding(c)!!
        assertEquals("treat_top", finding.id)
        assertEquals("Pizza is your #1 calorie food", finding.title)
    }

    @Test
    fun aSmallTreatOutsideTheTopFiveIsGoodNews() {
        val everyday = listOf("roti", "dal", "rice", "paneer", "eggs").flatMap { name -> (1..5).map { meal(300.0, item(name, 300.0)) } }
        val sweet = meal(150.0, item("gulab jamun", 150.0), tags = tags("treat", treat = true))
        val c = CalorieSourcesEngine.build(everyday + sweet)!!
        assertNull(c.treatRank)
        assertEquals("Gulab jamun", c.biggestTreat?.name)
        assertEquals("treat_small", InsightsEngine.treatFinding(c)?.id)
    }

    @Test
    fun noTreatsIsOnlyClaimedWhenMostMealsWereRated() {
        val rated = (1..5).map { meal(300.0, item("dal", 300.0)) }
        val unrated = (1..5).map { meal(300.0, item("dal", 300.0), tags = null) }
        assertEquals("treat_none", InsightsEngine.treatFinding(CalorieSourcesEngine.build(rated)!!)?.id)
        assertNull(InsightsEngine.treatFinding(CalorieSourcesEngine.build(unrated)!!))
    }

    @Test
    fun howOftenSaysTwiceADayOnlyWhenItIs() {
        assertEquals("about twice a day", CalorieSourcesEngine.howOften(14, 7))
        assertEquals("about daily", CalorieSourcesEngine.howOften(7, 7))
        assertNull(CalorieSourcesEngine.howOften(2, 7))
        assertNotNull(CalorieSourcesEngine.howOften(62, 31))
    }
}
