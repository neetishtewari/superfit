package com.superfit.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works in parseResponse.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoachingEngineTest {

    private val today = LocalDate.of(2026, 10, 14)

    private fun finding(id: String, priority: Int, kind: FindingKind = FindingKind.CONCERN) = CoachFinding(
        id = id,
        area = CoachingEngine.areaOf(id),
        kind = kind,
        priority = priority,
        fact = "fact for $id",
        meaning = "meaning for $id",
        suggestion = "tip for $id"
    )

    private fun report(findings: List<CoachFinding>, checked: Set<String> = emptySet()) = CoachReport(
        findings = findings.sortedByDescending { it.priority },
        checkedAreas = checked,
        days = emptyList(),
        typicalDayCalories = 2000,
        completeDaysLastWeek = 5,
        loggedDaysLastWeek = 6,
        today = TodaySnapshot(800, 40, 90, listOf(MealSlot.BREAKFAST), emptyList(), 13),
        targetMeanings = listOf("Your 130g target is about 1.6g per kg of body weight.")
    )

    private fun said(daysAgo: Long, id: String, kind: FindingKind = FindingKind.CONCERN) =
        CoachMemoryEntry(today.minusDays(daysAgo), id, kind, "headline $id")

    @Test
    fun `the most important finding leads`() {
        val r = report(listOf(finding("veg_low", 60), finding("protein_low", 92)))
        assertEquals("protein_low", CoachingEngine.chooseFocus(r, emptyList(), today).id)
    }

    @Test
    fun `a subject gets a rest after the coach talks about it`() {
        val r = report(listOf(finding("protein_low", 92), finding("veg_low", 70)))
        assertEquals("veg_low", CoachingEngine.chooseFocus(r, listOf(said(1, "protein_low")), today).id)
        // Any finding in the same area rests, not just the same one.
        val sameArea = report(listOf(finding("protein_short_weekends", 92), finding("veg_low", 70)))
        assertEquals("veg_low", CoachingEngine.chooseFocus(sameArea, listOf(said(3, "protein_low")), today).id)
        // After the rest it can come back.
        assertEquals("protein_low", CoachingEngine.chooseFocus(r, listOf(said(5, "protein_low")), today).id)
    }

    @Test
    fun `an old cake isn't raised again, a new one is`() {
        val r = report(listOf(finding("treats_high", 72), finding("veg_low", 40)))
        val memory = listOf(said(2, "treats_high"), said(1, "habit_walk_after_meals", FindingKind.HABIT))
        // The cake was 3 days ago, before the coach mentioned treats.
        assertEquals("veg_low", CoachingEngine.chooseFocus(r, memory, today, lastTreatDay = today.minusDays(3)).id)
        // A treat logged since then brings the subject back.
        assertEquals("treats_high", CoachingEngine.chooseFocus(r, memory, today, lastTreatDay = today).id)
    }

    @Test
    fun `quiet days get an everyday habit tip`() {
        val r = report(listOf(finding("protein_low", 92), finding("veg_low", 50)))
        val focus = CoachingEngine.chooseFocus(r, listOf(said(1, "protein_low")), today)
        assertEquals(FindingKind.HABIT, focus.kind)
        // The next one is a different habit, and an urgent pattern still wins over a habit.
        val next = CoachingEngine.chooseFocus(r, listOf(said(1, focus.id, FindingKind.HABIT), said(6, "protein_low")), today)
        assertEquals("protein_low", next.id)
        val after = CoachingEngine.habitFor(listOf(said(1, focus.id, FindingKind.HABIT)), today)
        assertTrue(after != null && after.id != focus.id)
    }

    @Test
    fun `habit tips never ask anyone to eat less`() {
        CoachingEngine.HABITS.forEach { h ->
            val text = "${h.fact} ${h.meaning} ${h.suggestion}".lowercase()
            listOf("eat less", "skip", "smaller portion", "cut calories", "fasting").forEach { assertTrue(h.id, !text.contains(it)) }
        }
    }

    @Test
    fun `a concern that cleared up becomes a follow-up win`() {
        val r = report(listOf(finding("protein_good", 50, FindingKind.WIN), finding("steps_down", 50)), checked = setOf("protein", "steps"))
        val focus = CoachingEngine.chooseFocus(r, listOf(said(3, "protein_low")), today)
        assertEquals("followup_protein", focus.id)
        assertEquals(FindingKind.WIN, focus.kind)
        assertTrue(focus.fact, focus.fact.startsWith("3 days ago I flagged your protein. fact for protein_good"))
    }

    @Test
    fun `a follow-up is only given once`() {
        val r = report(listOf(finding("steps_down", 50)), checked = setOf("protein", "steps"))
        val memory = listOf(said(1, "followup_protein", FindingKind.WIN), said(2, "habit_water", FindingKind.HABIT), said(3, "protein_low"))
        assertEquals("steps_down", CoachingEngine.chooseFocus(r, memory, today).id)
    }

    @Test
    fun `no follow-up while the area can't be judged`() {
        val r = report(listOf(finding("steps_down", 50)), checked = setOf("steps"))
        assertEquals("steps_down", CoachingEngine.chooseFocus(r, listOf(said(2, "habit_water", FindingKind.HABIT), said(3, "protein_low")), today).id)
    }

    @Test
    fun `with nothing to go on the coach helps the user get started`() {
        assertEquals("getting_started", CoachingEngine.chooseFocus(report(emptyList()), emptyList(), today).id)
    }

    @Test
    fun `reads the AI reply, with or without code fences`() {
        val focus = finding("protein_low", 92)
        val plain = CoachingEngine.parseResponse("""{"headline":"**Protein is short** again.","action":"Add eggs at dinner."}""", focus, listOf("why"))
        assertEquals("**Protein is short** again.", plain.headline)
        assertEquals("Add eggs at dinner.", plain.action)
        assertEquals("protein_low", plain.focusId)

        val fenced = CoachingEngine.parseResponse("```json\n{\"headline\":\"Hi\",\"action\":\"\"}\n```", focus, emptyList())
        assertEquals("Hi", fenced.headline)
        assertEquals("tip for protein_low", fenced.action)
    }

    @Test
    fun `falls back to the plain summary when the reply is unusable`() {
        val focus = finding("protein_low", 92)
        val insight = CoachingEngine.parseResponse("{not json", focus, listOf("fact", "meaning"))
        assertEquals("fact for protein_low", insight.headline)
        assertEquals("tip for protein_low", insight.action)
        assertEquals(listOf("meaning"), insight.why)
    }

    @Test
    fun `memory survives a round trip and keeps one entry per day`() {
        val first = CoachMemory.remember(emptyList(), CoachMemoryEntry(today, "veg_low", FindingKind.CONCERN, "Eat\tmore\nveg"))
        val replaced = CoachMemory.remember(first, CoachMemoryEntry(today, "protein_low", FindingKind.CONCERN, "Protein"))
        val withOlder = CoachMemory.remember(replaced, CoachMemoryEntry(today.minusDays(1), "sleep_low", FindingKind.CONCERN, "Sleep"))

        val decoded = CoachMemory.decode(CoachMemory.encode(withOlder))
        assertEquals(listOf("protein_low", "sleep_low"), decoded.map { it.focusId })
        assertEquals(today, decoded.first().date)
        assertEquals(FindingKind.CONCERN, decoded.first().focusKind)
        assertEquals("Eat more veg", CoachMemory.decode(CoachMemory.encode(first)).single().headline)
    }
}
