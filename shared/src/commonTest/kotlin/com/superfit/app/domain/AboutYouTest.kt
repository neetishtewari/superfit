package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class AboutYouTest {
    private val today = LocalDate(2026, 11, 12)
    private val me = UserProfileEntity(
        age = 34,
        heightCm = 175.0,
        weightKg = 80.0,
        isMale = true,
        activityMultiplier = 1.375,
        goal = "LOSE_WEIGHT",
        calorieOffset = -500
    )

    @Test
    fun summaryShowsWhatTheAppHasOnFile() {
        assertEquals("Male · 34 · 175 cm (5 ft 9 in) · 1-3 workouts", AboutYou.summary(me, today))
    }

    @Test
    fun heightMustBeRealistic() {
        assertNull(AboutYou.heightProblem("175"))
        assertEquals("Check your height", AboutYou.heightProblem("17"))
        assertEquals("Check your height", AboutYou.heightProblem("300"))
        assertEquals("Enter your height", AboutYou.heightProblem(""))
    }

    @Test
    fun previewShowsTheTargetBeforeAndAfter() {
        // Mifflin-St Jeor: 10 cm shorter is 62.5 kcal less at rest, x1.375 a day.
        val shorter = me.copy(heightCm = 165.0)
        val line = AboutYou.preview(me, shorter, ownTargets = false, today = today, advice = null, bodyFatPct = null)
        assertTrue(line.startsWith("Daily target "), line)
        assertTrue("→" in line, line)
    }

    @Test
    fun previewSaysWhenNothingMoves() {
        val line = AboutYou.preview(me, me, ownTargets = false, today = today, advice = null, bodyFatPct = null)
        assertTrue(line.startsWith("Daily target stays"), line)
        assertTrue("protein stays" in line, line)
    }

    @Test
    fun ownTargetsAreLeftAlone() {
        assertEquals(
            "You set your own calories and macros, so those stay as they are.",
            AboutYou.preview(me, me.copy(heightCm = 160.0), ownTargets = true, today = today)
        )
    }

    @Test
    fun scaleBodyFatExplainsWhyTheTargetStays() {
        val line = AboutYou.preview(me, me.copy(heightCm = 165.0), ownTargets = false, today = today, advice = null, bodyFatPct = 20.0)
        assertTrue(line.endsWith("(set from your scale's body fat reading)"), line)
    }
}
