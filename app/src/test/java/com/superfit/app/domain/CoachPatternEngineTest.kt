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

    private fun meal(daysAgo: Int, hour: Int, text: String, kcal: Double, protein: Double = 10.0) =
        NutritionEntryEntity(
            foodText = text,
            calories = kcal,
            proteinG = protein,
            carbsG = 0.0,
            fatG = 0.0,
            timestamp = today.minusDays(daysAgo.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli()
        )

    /** Breakfast, lunch and dinner adding up to [kcal] and [protein]. */
    private fun fullDay(daysAgo: Int, protein: Double, kcal: Double = 2000.0, dinner: String = "dal rice and sabzi") = listOf(
        meal(daysAgo, 8, "oats with milk", kcal * 0.25, protein * 0.25),
        meal(daysAgo, 13, "roti and paneer", kcal * 0.35, protein * 0.35),
        meal(daysAgo, 20, dinner, kcal * 0.40, protein * 0.40)
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

    // ---- Treats -----------------------------------------------------------------------------

    @Test
    fun `one burger in a week is fine`() {
        val logs = (1..6).flatMap { d -> fullDay(d, 130.0, dinner = if (d == 3) "chicken burger" else "dal rice and sabzi") }
        val report = analyze(logs)
        assertTrue(report.findings.none { it.area == "treats" })
    }

    @Test
    fun `burgers on three days become a habit`() {
        val logs = (1..8).flatMap { d -> fullDay(d, 130.0, dinner = if (d in setOf(1, 4, 7)) "veg burger" else "dal rice and sabzi") }
        val finding = analyze(logs).findings.first { it.id == "treats_habit" }
        assertTrue(finding.fact, finding.fact.startsWith("Burgers showed up on 3 days"))
    }

    @Test
    fun `more than two treat meals in a week is flagged, and a pizza with a beer is one meal`() {
        val logs = (1..6).flatMap { fullDay(it, 130.0) } + listOf(
            meal(1, 21, "pizza", 600.0), meal(1, 21, "beer", 200.0),
            meal(3, 16, "chocolate cake", 400.0),
            meal(5, 17, "samosa", 300.0)
        )
        val finding = analyze(logs).findings.first { it.id == "treats_high" }
        assertTrue(finding.fact, finding.fact.startsWith("3 treat meals"))
    }

    @Test
    fun `treat matching uses whole words and skips healthier versions`() {
        assertEquals("burger", CoachPatternEngine.treatCategory("Chicken Burger"))
        assertEquals("dessert", CoachPatternEngine.treatCategory("2 gulab jamun"))
        assertNull(CoachPatternEngine.treatCategory("eggplant curry"))
        assertNull(CoachPatternEngine.treatCategory("ginger tea"))
        assertNull(CoachPatternEngine.treatCategory("protein ice cream"))
        assertNull(CoachPatternEngine.treatCategory("diet coke"))
        assertNull(CoachPatternEngine.treatCategory("sweet potato chaat"))
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

    // ---- Fibre ------------------------------------------------------------------------------

    /** A full day where every meal carries the AI's tags, adding up to [fiber] grams of fibre. */
    private fun taggedDay(daysAgo: Int, fiber: Double, vegFruit: Double = 0.5) = fullDay(daysAgo, 130.0).map { m ->
        m.copy(qualityTags = MealTags(1, false, false, vegFruit, fiber / 3, false, "dal").encode())
    }

    @Test
    fun `fibre well under target on most fully logged days is flagged with ways to fix it`() {
        val report = analyze((1..5).flatMap { taggedDay(it, fiber = 12.0) })
        val finding = report.findings.single { it.area == "fibre" }
        assertEquals("fibre_low", finding.id)
        assertTrue(finding.fact.contains("28g target"))
        assertTrue(finding.suggestion.startsWith("Add a bowl of sabzi"))
        assertTrue("fibre" in report.checkedAreas)
    }

    @Test
    fun `fibre on target every day is a win, and untagged meals are never judged`() {
        assertEquals("fibre_good", analyze((1..5).flatMap { taggedDay(it, fiber = 30.0) }).findings.single { it.area == "fibre" }.id)

        val untagged = analyze((1..5).flatMap { fullDay(it, 130.0) })
        assertTrue(untagged.findings.none { it.area == "fibre" })
        assertFalse("fibre" in untagged.checkedAreas)
    }
}
