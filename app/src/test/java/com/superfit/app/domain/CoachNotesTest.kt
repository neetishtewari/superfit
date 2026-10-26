package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoachNotesTest {

    private val today = LocalDate.of(2026, 10, 26)
    private val zone = ZoneOffset.UTC

    private fun note(id: String, text: String, kind: NoteKind = NoteKind.LASTING, daysAgo: Long = 1, lookFor: List<String> = emptyList(), log: PlanLog? = null) =
        CoachNote(id, text, kind, today.minusDays(daysAgo), lookFor = lookFor, planLog = log)

    private fun meal(daysAgo: Long, text: String) = NutritionEntryEntity(
        foodText = text,
        calories = 400.0,
        proteinG = 20.0,
        carbsG = 40.0,
        fatG = 10.0,
        timestamp = today.minusDays(daysAgo).atTime(9, 0).toInstant(zone).toEpochMilli()
    )

    private fun workout(daysAgo: Long, text: String) = WorkoutEntryEntity(
        description = text,
        caloriesBurned = 200.0,
        workoutType = "Cardio",
        setsCount = 0,
        repsCount = 0,
        timestamp = today.minusDays(daysAgo).atTime(19, 0).toInstant(zone).toEpochMilli()
    )

    @Test
    fun notesComeBackTheSame() {
        val notes = listOf(
            note("a", "Vegetarian on Tuesdays"),
            note("b", "Travelling this week", NoteKind.SHORT).copy(byUser = true),
            note("c", "Add eggs to breakfast", NoteKind.PLAN, lookFor = listOf("egg"), log = PlanLog.MEAL)
        )
        assertEquals(notes, CoachNotes.decode(CoachNotes.encode(notes)))
    }

    @Test
    fun shortNotesAndPlansFade() {
        assertTrue(note("a", "Knee pain", daysAgo = 300).isActive(today))
        assertTrue(note("b", "Has a cold", NoteKind.SHORT, daysAgo = 6).isActive(today))
        assertFalse(note("b", "Has a cold", NoteKind.SHORT, daysAgo = 7).isActive(today))
        assertTrue(note("c", "Walk after dinner", NoteKind.PLAN, daysAgo = 13).isActive(today))
        assertFalse(note("c", "Walk after dinner", NoteKind.PLAN, daysAgo = 14).isActive(today))
    }

    @Test
    fun parsesNewNotesAndOutdatedOnes() {
        val existing = listOf(note("knee", "Left knee hurts after running"), note("veg", "Vegetarian on Tuesdays"))
        val response = """
            {"remember": [
              {"text": "Cousin's wedding on 15 Nov, wants to be 2 kg lighter", "kind": "lasting"},
              {"text": "add eggs to breakfast.", "kind": "plan", "look_for": ["Egg", "eg"], "log": "meal"},
              {"text": "vegetarian on tuesdays", "kind": "lasting"},
              {"text": "One too many", "kind": "short"}
            ],
            "outdated": ["knee", "made-up-id"]}
        """.trimIndent()
        val update = CoachNotes.parseUpdate(response, existing, today, now = 1000L)
        assertEquals(listOf("Cousin's wedding on 15 Nov, wants to be 2 kg lighter", "Add eggs to breakfast"), update.add.map { it.text })
        val plan = update.add[1]
        assertEquals(NoteKind.PLAN, plan.kind)
        assertEquals(listOf("egg"), plan.lookFor)
        assertEquals(PlanLog.MEAL, plan.planLog)
        assertEquals(listOf("knee"), update.outdatedIds)

        val after = CoachNotes.apply(existing, update)
        assertEquals(listOf("veg", update.add[0].id, update.add[1].id), after.map { it.id })
    }

    @Test
    fun brokenOrEmptyAnswersChangeNothing() {
        assertTrue(CoachNotes.parseUpdate("not json", emptyList(), today, 1L).isEmpty)
        assertTrue(CoachNotes.parseUpdate("""{"remember": [], "outdated": []}""", emptyList(), today, 1L).isEmpty)
    }

    @Test
    fun planWithoutWordsToLookForIsJustANote() {
        val update = CoachNotes.parseUpdate("""{"remember": [{"text": "Eat better", "kind": "plan", "log": "meal"}]}""", emptyList(), today, 1L)
        assertNull(update.add.single().planLog)
        assertNull(CoachNotes.checkPlan(update.add.single(), emptyList(), emptyList(), today, zone))
    }

    @Test
    fun planCheckCountsOnlyLoggedDaysAfterThePlan() {
        val plan = note("p", "Add eggs to breakfast", NoteKind.PLAN, daysAgo = 4, lookFor = listOf("egg"), log = PlanLog.MEAL)
        val meals = listOf(
            meal(4, "2 eggs and toast"), // the day the plan was made: not counted
            meal(3, "Poha and tea"),
            meal(2, "Boiled eggs, chai"),
            meal(0, "Egg bhurji with roti")
        )
        assertEquals(PlanCheck(daysLogged = 3, daysDone = 2), CoachNotes.checkPlan(plan, meals, emptyList(), today, zone))
    }

    @Test
    fun planFindingsCelebrateOrRemindGently() {
        val walk = note("w", "Walk after dinner", NoteKind.PLAN, daysAgo = 3, lookFor = listOf("walk"), log = PlanLog.WORKOUT)
        val done = CoachNotes.planFindings(listOf(walk), emptyList(), listOf(workout(1, "30 min walk")), today, zone).single()
        assertEquals(FindingKind.WIN, done.kind)
        assertEquals("plan_w", done.id)
        assertTrue(done.fact.contains("1 of the 1 day"))

        val eggs = note("e", "Add eggs to breakfast", NoteKind.PLAN, daysAgo = 3, lookFor = listOf("egg"), log = PlanLog.MEAL)
        val notYet = CoachNotes.planFindings(listOf(eggs), listOf(meal(2, "Poha"), meal(1, "Idli")), emptyList(), today, zone).single()
        assertEquals(FindingKind.CONCERN, notYet.kind)

        // One logged day without it, or days with no logs at all, say nothing yet.
        assertTrue(CoachNotes.planFindings(listOf(eggs), listOf(meal(1, "Idli")), emptyList(), today, zone).isEmpty())
        assertTrue(CoachNotes.planFindings(listOf(eggs), emptyList(), emptyList(), today, zone).isEmpty())
    }

    @Test
    fun aDonePlanCanBecomeTheDailyFocus() {
        val walk = note("w", "Walk after dinner", NoteKind.PLAN, daysAgo = 3, lookFor = listOf("walk"), log = PlanLog.WORKOUT)
        val plans = CoachNotes.planFindings(listOf(walk), emptyList(), listOf(workout(1, "evening walk")), today, zone)
        val report = CoachReport(
            findings = emptyList(),
            checkedAreas = emptySet(),
            days = emptyList(),
            typicalDayCalories = 2000,
            completeDaysLastWeek = 5,
            loggedDaysLastWeek = 6,
            today = TodaySnapshot(800, 40, 90, listOf(MealSlot.BREAKFAST), emptyList(), 13),
            targetMeanings = listOf("meaning")
        )
        assertEquals("plan_w", CoachingEngine.chooseFocus(report, emptyList(), today, plans).id)
    }

    @Test
    fun promptListsOnlyActiveNotesWithIds() {
        val lines = CoachNotes.promptLines(
            listOf(note("a", "Vegetarian on Tuesdays"), note("b", "Has a cold", NoteKind.SHORT, daysAgo = 10)),
            today
        )
        assertTrue(lines.contains("[a] Vegetarian on Tuesdays"))
        assertFalse(lines.contains("cold"))
    }
}
