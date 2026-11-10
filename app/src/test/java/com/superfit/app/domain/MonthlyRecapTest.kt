package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinYearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MonthlyRecapTest {

    private val utc = ZoneOffset.UTC
    private val october = YearMonth.of(2026, 10)
    private val kOctober = october.toKotlinYearMonth()
    private val kUtc = kotlinx.datetime.TimeZone.UTC
    private fun oct(day: Int) = kotlinx.datetime.LocalDate(2026, 10, day)
    private val allSet = RecapSetup(healthConnect = true, mic = true, widget = true)

    private fun day(
        date: LocalDate,
        meals: Int = 3,
        kcal: Double = 1800.0,
        protein: Double = 100.0,
        steps: Int = 8000,
        workouts: List<ActivityFact> = emptyList()
    ) = DayTotals(date.toKotlinLocalDate(), kcal, 1900.0, meals, protein, 110.0, steps, workouts)

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

    private fun month(build: (LocalDate) -> DayTotals?): Map<kotlinx.datetime.LocalDate, DayTotals> =
        (1..31).mapNotNull { build(october.atDay(it)) }.associateBy { it.date }

    private fun recap(
        days: Map<kotlinx.datetime.LocalDate, DayTotals>,
        meals: List<NutritionEntryEntity> = emptyList(),
        strength: List<StrengthSession> = emptyList(),
        setup: RecapSetup = allSet
    ) = MonthlyRecap.build(kOctober, days, meals, strength, emptyList(), journey = null, goalSet = false, setup = setup, zone = kUtc)

    @Test
    fun aMonthWithAlmostNothingLoggedHasNoRecap() {
        val days = month { d -> if (d.dayOfMonth <= 2) day(d, steps = 0) else null }
        assertNull(recap(days))
    }

    @Test
    fun cardsComeInStoryOrderAndEmptyOnesAreLeftOut() {
        val days = month { d -> day(d) }
        val meals = (1..21).map { meal(october.atDay(it), listOf("dal", "poha", "upma")[it % 3], 8) }
        val r = recap(days, meals, setup = allSet.copy(widget = false))!!
        val ids = r.cards.map { it.id }
        assertEquals(listOf("needle", "calories", "macros", "choices", "moving", "strength", "setup"), ids)
        assertTrue(ids.none { it == "goal" || it == "faster" })
    }

    @Test
    fun caloriesCardRanksFoodsByCaloriesNotByCount() {
        val days = month { d -> day(d) }
        val chai = (1..20).map { meal(october.atDay(it), "chai", 7, "Chai").copy(calories = 70.0) }
        val pizza = (1..4).map { meal(october.atDay(it), "pizza", 3, "Pizza").copy(calories = 800.0) }
        val dal = (1..10).map { meal(october.atDay(it), "dal", 8, "Dal").copy(calories = 300.0) }
        val r = recap(days, chai + pizza + dal)!!
        val card = r.cards.filterIsInstance<RecapCard.Calories>().single()
        assertEquals(listOf("Pizza", "Dal", "Chai"), card.sources.top.map { it.name })
        assertEquals("Chai", card.sources.mostLogged?.name)
    }

    @Test
    fun walksAtTheSamePaceWithALowerHeartRateMakeAHeartCard() {
        fun walk(bpm: Long) = ActivityFact("Walk", 60, 5.0, 250.0, 720, isRun = false, avgHeartRate = bpm)
        val days = (1..30).associate { i ->
            val sep = october.atDay(1).minusDays(i.toLong())
            sep.toKotlinLocalDate() to day(sep, workouts = if (i % 5 == 0) listOf(walk(120)) else emptyList())
        } + month { d -> day(d, workouts = if (d.dayOfMonth % 5 == 0) listOf(walk(112)) else emptyList()) }
        val card = recap(days)!!.cards.filterIsInstance<RecapCard.Heart>().single()
        assertEquals(8, card.change.bpmDrop)
        assertEquals("walk", card.change.activity)
    }

    @Test
    fun aSmallTasteIsNeverAChoiceToRethink() {
        val meals = listOf(
            meal(october.atDay(1), "dal", 9, "Dal, roti and salad"),
            meal(october.atDay(2), "cake", 2, "1 tsp birthday cake").copy(calories = 40.0)
        )
        val card = MonthlyRecap.choices(meals)!!
        assertTrue(card.rethink.isEmpty())
    }

    @Test
    fun choicesNameTheFoodWorthARethinkNotTheWholeMeal() {
        val tags = MealTags(
            2, fried = false, addedSugar = false, vegFruitServings = 0.0, fiberG = 0.0, treat = true, foodType = "eggs and beer",
            rating = 4, itemRatings = listOf(ItemRating("eggs", 8), ItemRating("beer", 2))
        )
        fun item(name: String, kcal: Double) = MealItem(name, 1.0, "serving", kcal, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val meal = meal(october.atDay(1), "eggs and beer", 4, "Eggs and beer").copy(
            calories = 500.0,
            qualityTags = tags.encode(),
            items = MealItems(listOf(item("eggs", 200.0), item("beer", 300.0))).encode()
        )
        val card = MonthlyRecap.choices(listOf(meal))!!
        assertEquals(listOf("Eggs"), card.good)
        assertEquals(listOf("Beer"), card.rethink)
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
    fun strengthCardSaysHowOftenHowLongAndWhatWasTrained() {
        val days = month { d -> day(d) }
        val sessions = listOf(
            StrengthSession(oct(2), 40, setOf(BodyArea.UPPER)),
            StrengthSession(oct(9), 40, setOf(BodyArea.UPPER)),
            StrengthSession(oct(16), 40, setOf(BodyArea.UPPER)),
            StrengthSession(oct(23), 40, setOf(BodyArea.UPPER))
        )
        val d = recap(days, strength = sessions)!!.cards.filterIsInstance<RecapCard.Strength>().single().detail!!
        assertEquals(160, d.minutes)
        assertEquals("about once a week", d.perWeekText())
        assertEquals("4 upper body", StrengthSessions.areasText(d))
        assertEquals(BodyArea.LOWER, d.missing)
    }

    @Test
    fun bothHalvesTrainedIsNotFlagged() {
        val d = StrengthSessions.detail(
            listOf(
                StrengthSession(oct(1), null, setOf(BodyArea.UPPER)),
                StrengthSession(oct(4), null, setOf(BodyArea.LOWER)),
                StrengthSession(oct(8), null, emptySet())
            ),
            31
        )!!
        assertNull(d.missing)
        assertEquals(0, d.timedSessions)
        assertEquals(2, d.taggedSessions)
    }

    @Test
    fun strengthTagsAreReadFromTheAiAnswer() {
        val r = StrengthSessions.parse("""{"logs":[{"n":1,"areas":["upper","core"],"minutes":45},{"n":2,"areas":[],"minutes":null}]}""", 2)
        assertEquals(setOf(BodyArea.UPPER, BodyArea.CORE) to 45, r[0])
        assertEquals(emptySet<BodyArea>() to null, r[1])
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
        val kWeighIns = weighIns.map { it.first.toKotlinLocalDate() to it.second }
        val journey = GoalForecast.compute(86.0, 76.0, LocalDate.of(2026, 9, 1).toKotlinLocalDate(), kWeighIns, emptyMap(), -500, null, LocalDate.of(2026, 11, 1).toKotlinLocalDate(), 82.9)
        val card = MonthlyRecap.goal(journey, kWeighIns, kOctober.firstDay, kOctober.lastDay)
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

    @Test
    fun bodyCardOnlyWhenTheScaleSawAChange() {
        val days = month { d -> if (d.dayOfMonth <= 20) day(d) else null }
        fun build(body: BodyChange?) = MonthlyRecap.build(kOctober, days, emptyList(), emptyList(), emptyList(), journey = null, goalSet = false, setup = allSet, zone = kUtc, body = body)
        val moved = BodyChange(26.0, 24.8, 58.0, 58.2, null, null, 80.0, 78.6)
        val flat = BodyChange(26.0, 25.8, 58.0, 58.1, null, null, 80.0, 79.9)
        val card = build(moved)!!.cards.filterIsInstance<RecapCard.Body>().single()
        assertEquals("Body fat down 1.2 points", RecapWriter.plain(card).title)
        assertTrue(build(flat)!!.cards.none { it is RecapCard.Body })
        assertTrue(build(null)!!.cards.none { it is RecapCard.Body })
    }
}
