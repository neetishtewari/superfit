package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietScoreEngineTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val zone = ZoneOffset.UTC

    private val homeCooked = MealTags(
        1, fried = false, addedSugar = false, vegFruitServings = 2.0, fiberG = 10.0, treat = false, foodType = "dal",
        rating = 9, vegKinds = 2
    )
    private val burger = MealTags(3, fried = false, addedSugar = false, vegFruitServings = 0.0, fiberG = 2.0, treat = true, foodType = "burger", rating = 3)

    /** A meal with [carbShare] of its calories from carbs, its protein as given, and fat making up the rest. */
    private fun meal(
        daysAgo: Int,
        hour: Int,
        kcal: Double,
        protein: Double,
        tags: MealTags?,
        text: String = "meal",
        carbShare: Double = 0.45
    ): NutritionEntryEntity {
        val carbs = kcal * carbShare / 4
        val fat = ((kcal - protein * 4 - carbs * 4) / 9).coerceAtLeast(0.0)
        return NutritionEntryEntity(
            foodText = text,
            calories = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            timestamp = today.minusDays(daysAgo.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli(),
            qualityTags = tags?.encode()
        )
    }

    private fun goodDay(daysAgo: Int) = listOf(
        meal(daysAgo, 8, 500.0, 35.0, homeCooked),
        meal(daysAgo, 13, 700.0, 45.0, homeCooked),
        meal(daysAgo, 20, 800.0, 45.0, homeCooked)
    )

    private fun score(entries: List<NutritionEntryEntity>, weeksAgo: Int = 0, goal: String = "LOSE_WEIGHT") =
        DietScoreEngine.score(entries, today, proteinTargetG = 120.0, calorieTarget = 2000.0, zone = zone, weeksAgo = weeksAgo, goal = goal)

    private fun DietScore.part(key: String) = parts.first { it.key == key }

    @After
    fun clearPersonalNutrition() {
        PersonalNutrition.approvedFoods = emptySet()
    }

    @Test
    fun `needs three logged days before giving a score`() {
        assertNull(score(goodDay(1) + goodDay(2)))
        assertNotNull(score(goodDay(1) + goodDay(2) + goodDay(3)))
    }

    @Test
    fun `a well-balanced week scores full marks for every goal`() {
        listOf("LOSE_WEIGHT", "MAINTAIN", "GAIN_MUSCLE").forEach { goal ->
            val s = score((1..6).flatMap { goodDay(it) }, goal = goal)!!
            assertEquals(goal, 100, s.score)
            assertNull(DietScoreEngine.biggestGain(s))
        }
    }

    @Test
    fun `the parts add up to 100 for every goal`() {
        listOf("LOSE_WEIGHT", "MAINTAIN", "GAIN_MUSCLE").forEach { goal ->
            val w = DietScoreWeights.forGoal(goal)
            assertEquals(goal, 100, w.food + w.balance + w.protein + w.veg + w.treats + w.fibre)
        }
        // Protein counts most for building muscle, balance and portions most for losing weight.
        assertTrue(DietScoreWeights.forGoal("GAIN_MUSCLE").protein > DietScoreWeights.forGoal("LOSE_WEIGHT").protein)
        assertTrue(DietScoreWeights.forGoal("LOSE_WEIGHT").balance > DietScoreWeights.forGoal("GAIN_MUSCLE").balance)
    }

    @Test
    fun `a forgotten meal costs nothing, even when everything is logged at night`() {
        // Same good week, but breakfast was never logged on three of the days, and on the others
        // all three meals were logged together at 9 pm.
        val entries = (1..6).flatMap { d ->
            if (d <= 3) goodDay(d).drop(1) else goodDay(d).map { it.copy(timestamp = today.minusDays(d.toLong()).atTime(21, 0).toInstant(zone).toEpochMilli()) }
        }
        assertEquals(100, score(entries)!!.score)
    }

    @Test
    fun `two treats a week are free, each extra one costs three points`() {
        val week = (1..6).flatMap { goodDay(it) }
        fun withBurgers(n: Int) = week + (1..n).map { meal(it, 17, 600.0, 25.0, burger, "burger") }
        assertEquals(10, score(withBurgers(2))!!.part("treats").points)
        assertEquals(4, score(withBurgers(4))!!.part("treats").points)
    }

    @Test
    fun `a food marked fine for me is never a treat`() {
        val chocolate = MealTags(2, fried = false, addedSugar = true, vegFruitServings = 0.0, fiberG = 2.0, treat = true, foodType = "dark chocolate", rating = 4)
        val week = (1..6).flatMap { goodDay(it) } + (1..5).map { meal(it, 16, 120.0, 2.0, chocolate, "2 squares dark chocolate") }
        assertEquals(5, score(week)!!.treatMeals)

        PersonalNutrition.approvedFoods = setOf("dark chocolate")
        val s = score(week)!!
        assertEquals(0, s.treatMeals)
        assertEquals(10, s.part("treats").points)
        assertEquals(7, DietScoreEngine.mealRating(week.last(), chocolate))
    }

    @Test
    fun `foods to avoid for now cost points with no free allowance, unless marked fine`() {
        val cola = MealTags(3, fried = false, addedSugar = true, vegFruitServings = 0.0, fiberG = 0.0, treat = true, foodType = "cola", rating = 2, verdict = "avoid")
        val week = (1..6).flatMap { goodDay(it) } + meal(2, 16, 150.0, 0.0, cola, "cola")
        val treats = score(week)!!.part("treats")
        assertEquals(7, treats.points)
        assertTrue(treats.detail.contains("Best avoided for now: cola"))

        PersonalNutrition.approvedFoods = setOf("cola")
        assertEquals(10, score(week)!!.part("treats").points)
    }

    @Test
    fun `foods get plain words instead of numbers`() {
        assertEquals(FoodVerdict.AVOID, FoodVerdict.of(homeCooked.copy(verdict = "avoid"), 2, approved = false))
        assertEquals(FoodVerdict.GOOD, FoodVerdict.of(homeCooked.copy(verdict = "avoid"), 2, approved = true))
        // Older ratings without the AI's verdict are put into words from the number.
        assertEquals(FoodVerdict.GREAT, FoodVerdict.of(homeCooked, 9, approved = false))
        assertEquals(FoodVerdict.GOOD, FoodVerdict.of(homeCooked, 7, approved = false))
        assertEquals(FoodVerdict.PORTION, FoodVerdict.of(homeCooked, 5, approved = false))
        assertEquals(FoodVerdict.TREAT, FoodVerdict.of(burger, 3, approved = false))
        assertEquals("avoid", MealTags.decode(homeCooked.copy(verdict = "avoid").encode())!!.verdict)
    }

    @Test
    fun `carb-heavy days lose balance points and the tip says so`() {
        // Lots of roti or rice: 75% of calories from carbs every day.
        val entries = (1..6).flatMap { d -> goodDay(d).map { it.copy(carbsG = it.calories * 0.75 / 4, fatG = (it.calories * 0.25 - it.proteinG * 4).coerceAtLeast(0.0) / 9) } }
        val balance = score(entries)!!.part("balance")
        assertTrue(balance.points < balance.maxPoints)
        assertTrue(balance.tip.contains("carbs"))
    }

    @Test
    fun `low protein on full days is where the points go, and the tip says so`() {
        val entries = (1..6).flatMap { d -> goodDay(d).map { it.copy(proteinG = 10.0) } }
        val s = score(entries)!!
        assertEquals(5, s.part("protein").points)
        assertTrue(DietScoreEngine.biggestGain(s)!!.startsWith("Protein: reached 25% of your 120g goal"))
    }

    @Test
    fun `lots of fruit counts only up to two servings a day`() {
        val fruitOnly = MealTags(1, fried = false, addedSugar = false, vegFruitServings = 6.0, fiberG = 30.0, treat = false, foodType = "fruit", rating = 8, fruitServings = 6.0, vegKinds = 1)
        val entries = (1..6).flatMap { d -> listOf(meal(d, 12, 2000.0, 125.0, fruitOnly)) }
        val veg = score(entries)!!.part("veg")
        assertTrue(veg.points < veg.maxPoints)
        assertTrue(veg.detail.startsWith("About 2.0 servings a day"))
    }

    @Test
    fun `meals the AI hasn't tagged yet are left out of food quality`() {
        val untagged = (1..4).flatMap { d ->
            listOf(meal(d, 13, 900.0, 40.0, null, "chicken burger and fries"), meal(d, 20, 900.0, 40.0, null, "pizza"))
        }
        // Nothing tagged yet: no score rather than a guess from the names.
        assertNull(score(untagged))

        val s = score(untagged + (1..4).flatMap { goodDay(it) })!!
        assertEquals(0.4, s.estimatedShare, 0.001)
        assertEquals(0, s.treatMeals)
        assertEquals(s.part("food").maxPoints, s.part("food").points)
    }

    @Test
    fun `last week is scored on its own seven days`() {
        val entries = (7..12).flatMap { goodDay(it) }
        assertNull(score(entries))
        assertEquals(100, score(entries, weeksAgo = 1)!!.score)
    }

    @Test
    fun `the nutritionist rating and plate details survive saving`() {
        val tags = homeCooked.copy(fruitServings = 1.0, adviceVersion = "ab12cd")
        val back = MealTags.decode(tags.encode())!!
        assertEquals(9, back.rating)
        assertEquals(1.0, back.fruitServings, 0.01)
        assertEquals(2, back.vegKinds)
        assertEquals("ab12cd", back.adviceVersion)
    }
}
