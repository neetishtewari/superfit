package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so a meal's items (JSON) can be read.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TreatsTest {

    @Before
    fun noApprovedFoods() {
        PersonalNutrition.approvedFoods = emptySet()
    }

    private fun item(name: String, kcal: Double) =
        MealItem(name = name, qty = 1.0, unit = "piece", kcal = kcal, protein = 0.0, carbs = 0.0, fat = 0.0, fiber = 0.0, oilG = 0.0, sugarG = 0.0)

    private fun tags(treat: Boolean = false, treatItems: List<String>? = null, type: String = "") =
        MealTags(processing = 1, fried = false, addedSugar = false, vegFruitServings = 0.0, fiberG = 0.0, treat = treat, foodType = type, rating = 7, treatItems = treatItems)

    private fun meal(kcal: Double, tags: MealTags?, vararg items: MealItem, hour: Long = 0) = NutritionEntryEntity(
        foodText = items.joinToString { it.name }.ifEmpty { "meal" },
        calories = kcal,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
        timestamp = hour * 3_600_000L,
        qualityTags = tags?.encode(),
        items = if (items.isEmpty()) null else MealItems(items.toList()).encode()
    )

    @Test
    fun `only the treat part of a meal is treat food`() {
        val thali = meal(900.0, tags(treatItems = listOf("gulab jamun")), item("dal rice", 600.0), item("sabzi", 150.0), item("gulab jamun", 150.0))
        assertEquals(150.0, Treats.kcalOf(thali), 0.5)
        assertTrue(Treats.isRealTreat(thali))

        val withSquare = meal(655.0, tags(treatItems = listOf("dark chocolate")), item("dal rice", 600.0), item("dark chocolate", 55.0))
        assertEquals(55.0, Treats.kcalOf(withSquare), 0.5)
        assertFalse(Treats.isRealTreat(withSquare))
        assertEquals("dark chocolate", CoachPatternEngine.treatCategoryOf(withSquare))
    }

    @Test
    fun `older meals use the whole meal when it was called a treat`() {
        assertEquals(55.0, Treats.kcalOf(meal(55.0, tags(treat = true))), 0.0)
        assertEquals(0.0, Treats.kcalOf(meal(55.0, tags(treat = false))), 0.0)
        assertEquals(0.0, Treats.kcalOf(meal(400.0, null)), 0.0)
    }

    @Test
    fun `a square is not a treat, bites that add up are, a real treat always is`() {
        val square = { h: Long -> listOf(meal(55.0, tags(treat = true, type = "dark chocolate"), hour = h)) }
        assertEquals(0, Treats.occasions(listOf(square(16))).size)
        assertEquals(0, Treats.occasions(listOf(square(11), square(16))).size)
        // Three bites in a day: 165 kcal, one treat.
        assertEquals(1, Treats.occasions(listOf(square(11), square(15), square(19))).size)
        val cake = listOf(meal(400.0, tags(treat = true, type = "cake"), hour = 20))
        assertEquals(1, Treats.occasions(listOf(square(11), cake)).size)
    }

    @Test
    fun `fine for me foods never count`() {
        PersonalNutrition.approvedFoods = setOf("dark chocolate")
        assertEquals(0.0, Treats.kcalOf(meal(300.0, tags(treat = true, type = "dark chocolate"))), 0.0)
    }
}
