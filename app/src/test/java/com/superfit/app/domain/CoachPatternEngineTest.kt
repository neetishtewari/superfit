package com.superfit.app.domain

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WeightEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class CoachPatternEngineTest {

    // A Wednesday.
    private val today = LocalDate.of(2026, 10, 14)
    private val zone = ZoneOffset.UTC
    private val targets = MacroTargets(calories = 2000.0, proteinG = 130.0, carbsG = 200.0, fatG = 60.0)

    private fun meal(daysAgo: Int, hour: Int, text: String, kcal: Double, protein: Double = 10.0, tags: MealTags? = null) =
        NutritionEntryEntity(
            foodText = text,
            calories = kcal,
            proteinG = protein,
            carbsG = 0.0,
            fatG = 0.0,
            timestamp = today.minusDays(daysAgo.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli(),
            qualityTags = tags?.encode()
        )

    /** What the AI tags a treat as; the coach knows treats only from these tags. */
    private fun treat(type: String) = MealTags(3, false, false, 0.0, 1.0, treat = true, foodType = type)

    /** Breakfast, lunch and dinner adding up to [kcal] and [protein]. */
    private fun fullDay(daysAgo: Int, protein: Double, kcal: Double = 2000.0, dinner: String = "dal rice and sabzi", dinnerTags: MealTags? = null) = listOf(
        meal(daysAgo, 8, "oats with milk", kcal * 0.25, protein * 0.25),
        meal(daysAgo, 13, "roti and paneer", kcal * 0.35, protein * 0.35),
        meal(daysAgo, 20, dinner, kcal * 0.40, protein * 0.40, dinnerTags)
    )

    private fun analyze(
        nutrition: List<NutritionEntryEntity>,
        activity: List<ActivityTelemetryEntity> = emptyList(),
        goal: String = "LOSE_WEIGHT",
        weights: List<WeightEntryEntity> = emptyList()
    ) = CoachPatternEngine.analyze(
        today = today,
        now = LocalTime.of(18, 0),
        weightKg = 80.0,
        goal = goal,
        targets = targets,
        maintenanceCalories = 2500.0,
        nutrition = nutrition,
        workouts = emptyList(),
        activity = activity,
        sleep = emptyList(),
        weights = weights,
        targetWeightKg = 75.0,
        zone = zone
    )

    private fun weighIns(vararg pairs: Pair<Int, Double>) = pairs.map { (daysAgo, kg) ->
        WeightEntryEntity(weightKg = kg, timestamp = today.minusDays(daysAgo.toLong()).atTime(7, 0).toInstant(zone).toEpochMilli())
    }

    private fun CoachReport.ids() = findings.map { it.id }

    // ---- Day completeness -------------------------------------------------------------------

    @Test
    fun `a day with a forgotten meal is partial, a light day with every meal logged is complete`() {
        val forgotBreakfast = listOf(meal(1, 13, "rice and dal", 700.0), meal(1, 20, "roti sabzi", 800.0))
        assertEquals(DayLogStatus.PARTIAL, CoachPatternEngine.buildDayLog(today.minusDays(1), forgotBreakfast, 2000.0, 2000.0, zone).status)

        val lightButComplete = fullDay(1, protein = 90.0, kcal = 1300.0)
        assertEquals(DayLogStatus.COMPLETE, CoachPatternEngine.buildDayLog(today.minusDays(1), lightButComplete, 2000.0, 2000.0, zone).status)

        assertEquals(DayLogStatus.BLANK, CoachPatternEngine.buildDayLog(today.minusDays(1), emptyList(), 2000.0, 2000.0, zone).status)
    }

    @Test
    fun `typical day ignores low days from forgotten meals`() {
        val days = listOf(2000.0, 2100.0, 1900.0, 600.0, 700.0, 2050.0).map { kcal -> listOf(meal(1, 13, "x", kcal)) }
        val typical = CoachPatternEngine.typicalDayCalories(days, 2000.0)
        assertTrue("typical was $typical", typical >= 1900.0)
    }

    // ---- Protein ----------------------------------------------------------------------------

    @Test
    fun `one low protein day is not flagged`() {
        val logs = (1..6).flatMap { d -> fullDay(d, protein = if (d == 2) 50.0 else 130.0) }
        val report = analyze(logs)
        assertFalse(report.ids().contains("protein_low"))
        assertTrue(report.ids().contains("protein_good"))
    }

    @Test
    fun `protein short on most recent full days is flagged with what it means`() {
        val logs = (1..6).flatMap { d -> fullDay(d, protein = if (d <= 4) 60.0 else 130.0) }
        val finding = analyze(logs).findings.first { it.id == "protein_low" }
        assertEquals(FindingKind.CONCERN, finding.kind)
        assertTrue(finding.fact, finding.fact.contains("4 of your last 5"))
        assertTrue(finding.meaning, finding.meaning.contains("1.6g per kg"))
    }

    @Test
    fun `days with only one meal logged never count as low protein`() {
        val logs = (1..6).map { d -> meal(d, 13, "rice and dal", 700.0, protein = 20.0) }
        val report = analyze(logs)
        assertFalse(report.ids().contains("protein_low"))
        assertFalse(report.ids().contains("calories_very_low"))
        assertTrue(report.ids().contains("logging_few_complete"))
    }

    @Test
    fun `light-protein meals are spotted even when days are only partly logged`() {
        val logs = (1..6).flatMap { d ->
            listOf(meal(d, 13, "rice and rajma", 600.0, protein = 12.0), meal(d, 20, "aloo paratha", 500.0, protein = 8.0))
        }
        assertTrue(analyze(logs).ids().contains("protein_meals_light"))
    }

    // ---- Fibre ------------------------------------------------------------------------------

    /** A full day where the AI tagged each meal, adding up to [fibre] grams. */
    private fun taggedDay(daysAgo: Int, fibre: Double) = fullDay(daysAgo, protein = 130.0).map {
        it.copy(qualityTags = MealTags(1, false, false, 1.0, fibre / 3, false, "dal").encode())
    }

    @Test
    fun `fibre target scales with calories within sensible limits`() {
        assertEquals(28.0, CoachPatternEngine.fibreTargetG(2000.0), 0.01)
        assertEquals(25.0, CoachPatternEngine.fibreTargetG(1200.0), 0.01)
        assertEquals(38.0, CoachPatternEngine.fibreTargetG(3500.0), 0.01)
    }

    @Test
    fun `one low fibre day is not flagged`() {
        val logs = (1..6).flatMap { d -> taggedDay(d, if (d == 2) 8.0 else 30.0) }
        val report = analyze(logs)
        assertFalse(report.ids().contains("fibre_low"))
    }

    @Test
    fun `fibre short on most recent full days is flagged with what to eat`() {
        val logs = (1..6).flatMap { d -> taggedDay(d, if (d <= 4) 10.0 else 30.0) }
        val finding = analyze(logs).findings.first { it.id == "fibre_low" }
        assertEquals(FindingKind.CONCERN, finding.kind)
        assertTrue(finding.fact, finding.fact.contains("4 of your last 5"))
        assertTrue(finding.suggestion, finding.suggestion.contains("dal"))
    }

    @Test
    fun `fibre on target every day is a win`() {
        val logs = (1..5).flatMap { d -> taggedDay(d, 30.0) }
        assertTrue(analyze(logs).ids().contains("fibre_good"))
    }

    @Test
    fun `untagged meals never count as low fibre`() {
        // Old meals without AI tags only have a rough guess from the name, so they're not judged.
        val logs = (1..6).flatMap { d -> fullDay(d, protein = 130.0, dinner = "white rice and curry") }
        assertTrue(analyze(logs).findings.none { it.area == "fibre" })
    }

    // ---- Treats -----------------------------------------------------------------------------

    @Test
    fun `one burger in a week is fine`() {
        val logs = (1..6).flatMap { d -> if (d == 3) fullDay(d, 130.0, dinner = "chicken burger", dinnerTags = treat("burger")) else fullDay(d, 130.0) }
        val report = analyze(logs)
        assertTrue(report.findings.none { it.area == "treats" })
    }

    @Test
    fun `burgers on three days become a habit`() {
        val logs = (1..8).flatMap { d -> if (d in setOf(1, 4, 7)) fullDay(d, 130.0, dinner = "veg burger", dinnerTags = treat("burger")) else fullDay(d, 130.0) }
        val finding = analyze(logs).findings.first { it.id == "treats_habit" }
        assertTrue(finding.fact, finding.fact.startsWith("Burger showed up on 3 days"))
    }

    @Test
    fun `more than two treat meals in a week is flagged, and a pizza with a beer is one meal`() {
        val logs = (1..6).flatMap { fullDay(it, 130.0) } + listOf(
            meal(1, 21, "pizza", 600.0, tags = treat("pizza")), meal(1, 21, "beer", 200.0, tags = treat("beer")),
            meal(3, 16, "chocolate cake", 400.0, tags = treat("cake")),
            meal(5, 17, "samosa", 300.0, tags = treat("samosa"))
        )
        val finding = analyze(logs).findings.first { it.id == "treats_high" }
        assertTrue(finding.fact, finding.fact.startsWith("3 treat meals"))
    }

    @Test
    fun `treats come only from the AI's tags, never from the meal's name`() {
        assertNull(CoachPatternEngine.treatCategoryOf(meal(1, 20, "chicken burger", 600.0)))
        assertEquals("kulfi", CoachPatternEngine.treatCategoryOf(meal(1, 20, "कुल्फी", 200.0, tags = treat("kulfi"))))
        assertEquals("treat", CoachPatternEngine.treatCategoryOf(meal(1, 20, "something sweet", 200.0, tags = treat(""))))
        val proteinIceCream = MealTags(2, false, false, 0.0, 1.0, treat = false, foodType = "ice cream")
        assertNull(CoachPatternEngine.treatCategoryOf(meal(1, 20, "protein ice cream", 200.0, tags = proteinIceCream)))
    }

    @Test
    fun `untagged meals are never judged on vegetables`() {
        val logs = (1..6).flatMap { d -> fullDay(d, protein = 130.0, dinner = "white rice and curry") }
        assertTrue(analyze(logs).findings.none { it.area == "veg" })
    }

    // ---- Calories, logging, steps -----------------------------------------------------------

    @Test
    fun `one big day is fine when the week averages out`() {
        val kcals = listOf(1800.0, 1850.0, 2700.0, 1800.0, 1850.0, 1800.0)
        val logs = kcals.flatMapIndexed { i, k -> fullDay(i + 1, 130.0, kcal = k) }
        val finding = analyze(logs).findings.first { it.id == "calories_on_track" }
        assertTrue(finding.fact, finding.fact.contains("2,700 kcal day"))
    }

    @Test
    fun `blank days are called out as gaps, not as eating too little`() {
        val logs = listOf(1, 2, 5, 6).flatMap { fullDay(it, 130.0) }
        val report = analyze(logs)
        assertTrue(report.ids().contains("logging_gaps"))
        assertFalse(report.ids().contains("calories_very_low"))
        assertEquals(4, report.completeDaysLastWeek)
    }

    @Test
    fun `a clear drop in steps against your own normal is flagged`() {
        val activity = (1..30).map { d ->
            ActivityTelemetryEntity(today.minusDays(d.toLong()).toString(), if (d <= 7) 4000 else 9000, 0.0)
        }
        val finding = analyze(emptyList(), activity).findings.firstOrNull { it.id == "steps_down" }
        assertNotNull(finding)
        assertTrue(finding!!.fact, finding.fact.contains("your usual 9,000"))
    }

    @Test
    fun `no logs at all gives no findings`() {
        val report = analyze(emptyList())
        assertTrue(report.findings.isEmpty())
        assertEquals(0, report.loggedDaysLastWeek)
    }

    // ---- Weight -----------------------------------------------------------------------------

    @Test
    fun `scale not moving while logged calories look on target points at unlogged food`() {
        val logs = (1..6).flatMap { fullDay(it, 130.0) }
        val weights = weighIns(21 to 80.2, 14 to 79.8, 7 to 80.3, 1 to 80.0)
        val finding = analyze(logs, weights = weights).findings.first { it.id == "weight_stalled" }
        assertTrue(finding.meaning, finding.meaning.contains("isn't getting logged"))
    }

    @Test
    fun `steady loss is a win and says how far there is to go`() {
        val weights = weighIns(28 to 82.0, 21 to 81.5, 14 to 81.0, 7 to 80.5, 0 to 80.0)
        val finding = analyze(emptyList(), weights = weights).findings.first { it.id == "weight_on_track" }
        assertEquals(FindingKind.WIN, finding.kind)
        assertTrue(finding.fact, finding.fact.contains("0.5 kg a week"))
        assertTrue(finding.fact, finding.fact.contains("5.0 kg to go"))
    }

    @Test
    fun `losing too fast is flagged`() {
        val weights = weighIns(21 to 85.0, 14 to 83.4, 7 to 81.6, 0 to 80.0)
        assertTrue(analyze(emptyList(), weights = weights).ids().contains("weight_fast"))
    }

    @Test
    fun `too few weigh-ins give no trend, and an old one asks for a new reading`() {
        assertTrue(analyze(emptyList(), weights = weighIns(10 to 81.0, 3 to 79.5)).findings.none { it.area == "weight" })
        assertEquals(listOf("weight_stale"), analyze(emptyList(), weights = weighIns(20 to 80.0)).ids())
    }
}
