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
    val calorieOffset: Int = -500
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
    val timestamp: Long // epoch millis
)

data class PredictedFood(
    val foodText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val score: Double
)

