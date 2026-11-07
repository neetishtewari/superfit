package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CloudMapsTest {

    @Test
    fun profileComesBackTheSame() {
        val p = UserProfileEntity(
            age = 34, heightCm = 172.5, weightKg = 81.2, isMale = false, activityMultiplier = 1.55,
            goal = "BUILD_MUSCLE", calorieOffset = 250, startingWeightKg = 84.0, targetWeightKg = 78.0,
            startDateTimestamp = 1_700_000_000_000L, fitnessLevel = "BEGINNER"
        )
        assertEquals(p, p.toMap().toUserProfileEntity())
    }

    @Test
    fun mealKeepsItsCloudIdAndOptionalFields() {
        val m = NutritionEntryEntity(
            id = 12, cloudId = "abc", foodText = "2 roti and dal", calories = 420.0, proteinG = 18.5,
            carbsG = 60.0, fatG = 9.0, timestamp = 1_700_000_100_000L, qualityTags = "q=7", items = "[]"
        )
        // The local number is not restored: a restore gives the meal a new one.
        assertEquals(m.copy(id = 0), m.toMap().toNutritionEntry("abc"))
        val plain = m.copy(qualityTags = null, items = null)
        val map = plain.toMap()
        assertNull(map["qualityTags"])
        assertEquals(plain.copy(id = 0), map.toNutritionEntry("abc"))
    }

    @Test
    fun wholeNumbersFromTheCloudReadAsLongStillWork() {
        // Firestore hands back every whole number as Long.
        val cloud: Map<String, Any> = mapOf("date" to "2026-11-07", "steps" to 9_500L, "activeCalories" to 320L)
        assertEquals(ActivityTelemetryEntity("2026-11-07", 9_500, 320.0), cloud.toActivityTelemetry())
    }

    @Test
    fun otherRowsComeBackTheSame() {
        val w = WorkoutEntryEntity(cloudId = "w1", description = "20 push-ups", caloriesBurned = 40.0, workoutType = "Strength", setsCount = 2, repsCount = 20, timestamp = 5L, difficultyRating = "HARD")
        assertEquals(w.copy(id = 0), w.toMap().toWorkoutEntry("w1"))
        val kg = WeightEntryEntity(cloudId = "k1", weightKg = 80.4, timestamp = 6L, note = "morning")
        assertEquals(kg.copy(id = 0), kg.toMap().toWeightEntry("k1"))
        val s = SleepTelemetryEntity("2026-11-07", 25_200L, 5_400L, 80)
        assertEquals(s, s.toMap().toSleepTelemetry())
        val st = StreakStateEntity(currentStreak = 4, longestStreak = 9, masteryStreak = 1, graceDaysRemaining = 0, lastLoggedDate = "2026-11-07", lastGraceUsedDate = "2026-11-01")
        assertEquals(st, st.toMap().toStreakState())
        val h = HabitEntryEntity("2026-11-07", waterMl = 1500, workoutMins = 30, cleanEatsCompleted = true, stepsCompleted = false, sleepCompleted = true)
        assertEquals(h, h.toMap().toHabitEntry())
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val empty = emptyMap<String, Any>()
        assertEquals("LOSE_WEIGHT", empty.toUserProfileEntity().goal)
        assertEquals("Cardio", empty.toWorkoutEntry("x").workoutType)
        assertEquals(1, empty.toStreakState().graceDaysRemaining)
    }
}
