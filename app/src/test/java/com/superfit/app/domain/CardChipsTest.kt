package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardChipsTest {

    @Test
    fun coachAndRoastNeverSitTogether() {
        val coach = CardChip.toggle(setOf(CardChip.STANDOUT), CardChip.COACH)
        assertEquals(setOf(CardChip.STANDOUT, CardChip.COACH), coach)
        assertEquals(setOf(CardChip.STANDOUT, CardChip.ROAST), CardChip.toggle(coach, CardChip.ROAST))
        assertEquals(setOf(CardChip.STANDOUT), CardChip.toggle(coach, CardChip.COACH))
    }

    @Test
    fun savedChipsRoundTripAndDropUnknownKeys() {
        val chips = setOf(CardChip.JOURNEY, CardChip.STANDOUT)
        assertEquals("standout,journey", CardChip.encode(chips))
        assertEquals(chips, CardChip.parse("standout, journey,sparkles"))
        assertTrue(CardChip.parse(null).isEmpty())
        assertEquals(setOf(CardChip.COACH), CardChip.parse("coach,roast"))
    }

    @Test
    fun roastMeUsersSeeRoastBeforeCoach() {
        assertEquals(listOf(CardChip.HEART, CardChip.STANDOUT, CardChip.ROAST, CardChip.COACH, CardChip.JOURNEY), CardChip.order(CoachStyle.ROAST))
        assertEquals(CardChip.COACH, CardChip.order(CoachStyle.SUPPORTIVE)[2])
    }

    @Test
    fun journeyLineShowsWeightOnlyWhenAllowed() {
        assertEquals("Day 34 of my journey", ShareJourney.line(34, 4.0, 10.0, lose = true, reached = false, showWeight = false))
        assertEquals("Day 34 of my journey · 4 of 10 kg down", ShareJourney.line(34, 4.0, 10.0, lose = true, reached = false, showWeight = true))
        assertEquals("Day 9 of my journey · 1.5 of 6 kg up", ShareJourney.line(9, 1.46, 6.0, lose = false, reached = false, showWeight = true))
        assertEquals("Day 90 of my journey · goal reached", ShareJourney.line(90, 10.0, 10.0, lose = true, reached = true, showWeight = true))
    }

    @Test
    fun strengthCardAddsUpSpokenReps() {
        val d = StrengthCardDetail.build(
            said = "20 push-ups and 50 dumbbell curls, 30 squats, 40 crunches",
            storedReps = 0,
            minutes = 25,
            todayAreas = setOf(BodyArea.UPPER, BodyArea.LOWER),
            weekAreas = setOf(BodyArea.UPPER),
            week = List(7) { WeekMark.NONE },
            dayIndex = 2
        )
        assertEquals(140, d.reps)
        assertEquals(3, d.exercises.size)
        assertEquals(1, d.moreExercises)
        assertEquals(setOf(BodyArea.UPPER, BodyArea.LOWER), d.today)
        // Upper body was trained today too, so it isn't listed again as "this week".
        assertTrue(d.thisWeek.isEmpty())
    }

    @Test
    fun fullBodyCountsAsEveryArea() {
        val d = StrengthCardDetail.build("gym session", storedReps = 60, minutes = null, todayAreas = setOf(BodyArea.FULL), weekAreas = emptySet(), week = emptyList(), dayIndex = 0)
        assertEquals(60, d.reps)
        assertEquals(StrengthCardDetail.SHOWN.toSet(), d.today)
    }

    @Test
    fun strengthCardLeadsWithRepsAndListsExercisesInsteadOfTheWords() {
        val voice = com.superfit.app.data.WorkoutEntryEntity(
            description = "20 push-ups and 50 curls",
            caloriesBurned = 120.0,
            workoutType = "Strength",
            setsCount = 0,
            repsCount = 0,
            timestamp = 1_762_000_000_000
        )
        val detail = StrengthCardDetail.build(voice.description, 0, 20, setOf(BodyArea.UPPER), emptySet(), List(7) { WeekMark.NONE }, 0)
        val card = ShareCards.withStrength(ShareCards.activity(voice), detail)
        assertEquals("STRENGTH", card.heading)
        assertEquals("70", card.bigValue)
        assertEquals("reps", card.bigUnit)
        assertEquals(null, card.subtitle)
        assertEquals(listOf("TIME", "BURNED"), card.stats.map { it.label })
    }
}
