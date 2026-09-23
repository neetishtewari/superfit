package com.superfit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 0, // Single user profile row
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val isMale: Boolean,
    val activityMultiplier: Double = 1.2,
    val goal: String = "LOSE_WEIGHT",
    val calorieOffset: Int = -500,
    val startingWeightKg: Double = 75.0,
    val targetWeightKg: Double = 70.0,
    val startDateTimestamp: Long = System.currentTimeMillis(),
    val fitnessLevel: String = "INTERMEDIATE" // "BEGINNER", "INTERMEDIATE", "ADVANCED"
)

@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightKg: Double,
    val timestamp: Long,
    val note: String = ""
)

@Entity(tableName = "streak_state")
data class StreakStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val masteryStreak: Int = 0,
    val graceDaysRemaining: Int = 1,
    val lastLoggedDate: String = "",
    val lastGraceUsedDate: String = ""
)

@Entity(tableName = "habit_entries")
data class HabitEntryEntity(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val waterMl: Int = 0,
    val workoutMins: Int = 0,
    val cleanEatsCompleted: Boolean = false,
    val stepsCompleted: Boolean = false,
    val sleepCompleted: Boolean = false
)

@Entity(tableName = "activity_telemetry")
data class ActivityTelemetryEntity(
    @PrimaryKey val date: String, // format YYYY-MM-DD
    val steps: Int,
    val activeCalories: Double
)

@Entity(tableName = "sleep_telemetry")
data class SleepTelemetryEntity(
    @PrimaryKey val date: String, // format YYYY-MM-DD
    val sleepDurationSeconds: Long,
    val deepSleepDurationSeconds: Long,
    val readinessScore: Int // 0-100% computed daily score
)

@Entity(tableName = "nutrition_entries")
data class NutritionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val timestamp: Long // epoch millis
)

@Entity(tableName = "workout_entries")
data class WorkoutEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val caloriesBurned: Double,
    val workoutType: String, // "Cardio" or "Strength"
    val setsCount: Int,
    val repsCount: Int,
    val timestamp: Long, // epoch millis
    val difficultyRating: String = "JUST_RIGHT" // "EASY", "JUST_RIGHT", "HARD"
)

data class PredictedFood(
    val foodText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val score: Double
)

