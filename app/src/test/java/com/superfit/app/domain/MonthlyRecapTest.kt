package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MonthlyRecapTest {

    private val utc = ZoneOffset.UTC
    private val october = YearMonth.of(2026, 10)
    private val allSet = RecapSetup(healthConnect = true, mic = true, widget = true)

    private fun day(
        date: LocalDate,
        meals: Int = 3,
        kcal: Double = 1800.0,
        protein: Double = 100.0,
        steps: Int = 8000,
        workouts: List<ActivityFact> = emptyList()
    ) = DayTotals(date, kcal, 1900.0, meals, protein, 110.0, steps, workouts)

    private fun run(km: Double, pace: Long) = ActivityFact("Run", (km * pace / 60).toLong(), km, 400.0, pace, isRun = true)

    private fun meal(date: LocalDate, food: String, rating: Int, text: String = food) = NutritionEntryEntity(
        foodText = text,
        calories = 500.0,
        proteinG = 25.0,
        carbsG = 60.0,
        fatG = 15.0,
        timestamp = date.atTime(13, 0).toInstant(utc).toEpochMilli(),
        qualityTags = MealTags(processing = 1, fried = false, addedSugar = false, vegFruitServings = 1.0, fiberG = 8.0, treat = false, foodType = food, rating = rating).encode()
    )

    private fun month(build: (LocalDate) -> DayTotals?): Map<LocalDate, DayTotals> =
        (1..31).mapNotNull { build(october.atDay(it)) }.associateBy { it.date }

    private fun recap(
        days: Map<LocalDate, DayTotals>,
        meals: List<NutritionEntryEntity> = emptyList(),
        strength: List<LocalDate> = emptyList(),
        setup: RecapSetup = allSet
    ) = MonthlyRecap.build(october, days, meals, strength, emptyList(), journey = null, goalSet = false, setup = setup, zone = utc)

    @Test
    fun aMonthWithAlmostNothingLoggedHasNoRecap() {
        val days = month { d -> if (d.dayOfMonth <= 2) day(d, steps = 0) else null }
        assertNull(recap(days))
    }

    @Test
    fun cardsComeInStoryOrderAndEmptyOnesAreLeftOut() {
        val days = month { d -> day(d) }
        val meals = (1..20).map { meal(october.atDay(it), if (it % 2 == 0) "dal" else "poha", 8) }
        val r = recap(days, meals, setup = allSet.copy(widget = false))!!
        val ids = r.cards.map { it.id }
        assertEquals(listOf("needle", "repeat", "macros", "choices", "moving", "strength", "setup"), ids)
        assertTrue(ids.none { it == "goal" || it == "faster" })
    }

    @Test
    fun onRepeatIsTheMostLoggedFoodWithItsUsualVerdict() {
        val meals = (1..12).map { meal(october.atDay(it), "dal", 8) } + (1..4).map { meal(october.atDay(it), "samosa", 3) }
        val card = MonthlyRecap.onRepeat(meals)!!
        assertEquals("Dal", card.food)
        assertEquals(12, card.times)
        assertEquals(FoodVerdict.GREAT, card.verdict)
    }

    @Test
    fun choicesNameTheBestAndLowestRatedMeals() {
        val meals = listOf(
            meal(october.atDay(1), "dal", 9, "Dal, roti and salad"),
            meal(october.atDay(2), "dal", 9, "dal, roti and salad"),
            meal(october.atDay(3), "samosa", 2, "Two samosas")
        )
        val card = MonthlyRecap.choices(meals)!!
        assertEquals(listOf("Dal, roti and salad"), card.good)
        assertEquals(listOf("Two samosas"), card.rethink)
    }

    @Test
    fun macrosSplitWeekdaysFromWeekendsAndIgnoreBlankDays() {
        // October 2026: the 3rd and 4th are a weekend.
        val days = month { d ->
            val weekend = d.dayOfWeek.value >= 6
            if (d.dayOfMonth == 31) day(d, meals = 0, protein = 0.0) else day(d, protein = if (weekend) 55.0 else 110.0)
        }
        val logged = days.values.filter { it.logged }
        val card = MonthlyRecap.macros(logged, emptyList())!!
        assertEquals(100, card.weekdayProteinPct)
        assertEquals(50, card.weekendProteinPct)
    }

    @Test
    fun fasterOnlyWhenPaceImprovedOnEnoughRuns() {
        val prev = (1..30).map { day(october.atDay(1).minusDays(it.toLong()), workouts = if (it % 7 == 0) listOf(run(5.0, 420)) else emptyList()) }
        val now = (1..31).map { day(october.atDay(it), workouts = if (it % 7 == 0) listOf(run(5.0, 402)) else emptyList()) }
        val card = MonthlyRecap.faster(now, prev)!!
        assertEquals("run", card.activity)
        assertEquals(402L, card.paceNow)
        assertEquals(420L, card.pacePrev)
        assertNull(MonthlyRecap.faster(now, now))
    }

    @Test
    fun strengthCardNudgesWhenNothingWasLogged() {
        val days = month { d -> day(d, meals = 0, workouts = listOf(run(5.0, 400))) }
        val strength = recap(days)!!.cards.filterIsInstance<RecapCard.Strength>().single()
        assertEquals(0, strength.sessions)
        assertEquals(155.0, strength.km, 0.01)
    }

    @Test
    fun setupListsOnlyWhatIsMissingAndIsSkippedWhenAllSet() {
        assertNull(MonthlyRecap.setup(allSet, loggedDays = 25, daysInMonth = 31))
        val card = MonthlyRecap.setup(allSet.copy(mic = false), loggedDays = 9, daysInMonth = 31)!!
        assertEquals(listOf(SetupItem.MIC, SetupItem.LOG_DAILY), card.items)
    }

    @Test
    fun goalCardShowsThisMonthsChangeOutOfTheWholeGoal() {
        val weighIns = listOf(LocalDate.of(2026, 9, 1) to 86.0, LocalDate.of(2026, 9, 30) to 84.3, LocalDate.of(2026, 10, 12) to 83.7, LocalDate.of(2026, 10, 30) to 82.9)
        val journey = GoalForecast.compute(86.0, 76.0, LocalDate.of(2026, 9, 1), weighIns, emptyMap(), -500, null, LocalDate.of(2026, 11, 1), 82.9)
        val card = MonthlyRecap.goal(journey, weighIns, october.atDay(1), october.atEndOfMonth())
        assertEquals(1.4, card.monthKg!!, 0.001)
        assertEquals(3.1, card.kgDone, 0.001)
        assertEquals(10.0, card.kgTotal, 0.001)
        assertNotNull(card.eta)
        assertTrue(card.lose)
    }

    @Test
    fun theAiWordsFallBackToPlainWordingCardByCard() {
        val days = month { d -> day(d) }
        val r = recap(days)!!
        val words = RecapWriter.parse("""{"cards": {"needle": {"title": "Your secret weapon", "line": "Protein on point."}}}""", r)
        assertEquals("Your secret weapon", words["needle"]!!.title)
        assertEquals(RecapWriter.plain(r.cards.first { it.id == "moving" }), words["moving"])
        assertEquals(r.cards.size, RecapWriter.parse("not json", r).size)
    }

    @Test
    fun thePromptNeverAsksAboutTheSetupCardOrUsesTheWordRoast() {
        val r = recap(month { d -> day(d) }, setup = allSet.copy(widget = false))!!
        val prompt = RecapWriter.prompt(r)
        assertTrue(!prompt.contains("setup"))
        assertTrue(RecapWriter.SYSTEM_INSTRUCTION.contains("Never use the word \"roast\""))
    }
}
