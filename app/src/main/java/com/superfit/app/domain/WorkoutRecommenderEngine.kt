package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutEntryEntity

data class ExerciseItem(
    val id: String,
    val name: String,
    val sets: Int,
    val repsOrDuration: String
)

data class TrainerWorkoutRecommendation(
    val splitTitle: String,
    val levelLabel: String,
    val estimatedCalories: Int,
    val exercises: List<ExerciseItem>,
    val trainerNotes: String
)

object WorkoutRecommenderEngine {

    fun generateRecommendation(
        profile: UserProfileEntity,
        workoutsLoggedToday: List<WorkoutEntryEntity>,
        historyWorkouts: List<WorkoutEntryEntity> = emptyList()
    ): TrainerWorkoutRecommendation {
        if (workoutsLoggedToday.isNotEmpty()) {
            val totalCals = workoutsLoggedToday.sumOf { it.caloriesBurned }.toInt()
            return TrainerWorkoutRecommendation(
                splitTitle = "Workout Complete Today!",
                levelLabel = "Rest & Recover",
                estimatedCalories = totalCals,
                exercises = emptyList(),
                trainerNotes = "Great work! You've logged $totalCals kcal today. Rest up for tomorrow's session."
            )
        }

        val userLevel = when {
            profile.fitnessLevel.equals("ADVANCED", ignoreCase = true) || profile.activityMultiplier >= 1.55 -> "ADVANCED"
            profile.fitnessLevel.equals("BEGINNER", ignoreCase = true) || profile.activityMultiplier <= 1.2 -> "BEGINNER"
            else -> "INTERMEDIATE"
        }

        val levelLabel = when (userLevel) {
            "BEGINNER" -> "Beginner Foundation"
            "ADVANCED" -> "Advanced Athlete Program"
            else -> "Intermediate Progression"
        }

        val sortedHistory = historyWorkouts.sortedByDescending { it.timestamp }
        val lastWorkout = sortedHistory.firstOrNull()
        val lastDesc = lastWorkout?.description?.lowercase() ?: ""
        val lastDifficulty = lastWorkout?.difficultyRating ?: "JUST_RIGHT"

        // RPE Modifier Note
        val rpeNote = when (lastDifficulty) {
            "EASY" -> " 🚀 Stepped up volume & reps because your last session felt easy!"
            "HARD" -> " 🩹 Moderated volume slightly to ensure full recovery from your previous hard workout."
            else -> " 📈 Continuing steady progressive overload based on your feedback."
        }

        // Rep Multiplier based on RPE
        val repMultiplier = when (lastDifficulty) {
            "EASY" -> 1.25
            "HARD" -> 0.85
            else -> 1.05
        }

        return when {
            lastDesc.contains("upper") || lastDesc.contains("push") || lastDesc.contains("arm") -> {
                // Next split: Lower Body & Core
                val exercises = when (userLevel) {
                    "BEGINNER" -> listOf(
                        ExerciseItem("1", "Bodyweight Squats", 3, "${(10 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Walking Lunges", 3, "${(8 * repMultiplier).toInt()} reps / leg"),
                        ExerciseItem("3", "Plank Hold", 3, "${(30 * repMultiplier).toInt()} secs")
                    )
                    "ADVANCED" -> listOf(
                        ExerciseItem("1", "Barbell / Heavy Dumbbell Squats", 4, "${(15 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Bulgarian Split Squats", 4, "${(12 * repMultiplier).toInt()} reps / leg"),
                        ExerciseItem("3", "Romanian Deadlifts", 4, "${(12 * repMultiplier).toInt()} reps"),
                        ExerciseItem("4", "Hanging Leg Raises / Heavy Planks", 4, "${(45 * repMultiplier).toInt()} secs")
                    )
                    else -> listOf(
                        ExerciseItem("1", "Goblet / Bodyweight Squats", 4, "${(12 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Walking Lunges", 3, "${(10 * repMultiplier).toInt()} reps / leg"),
                        ExerciseItem("3", "Glute Bridges", 3, "${(15 * repMultiplier).toInt()} reps"),
                        ExerciseItem("4", "Plank Hold", 3, "${(40 * repMultiplier).toInt()} secs")
                    )
                }

                TrainerWorkoutRecommendation(
                    splitTitle = "Lower Body & Core Focus",
                    levelLabel = levelLabel,
                    estimatedCalories = if (userLevel == "BEGINNER") 180 else 280,
                    exercises = exercises,
                    trainerNotes = "Upper body is resting. Focus on lower body power & core stability.$rpeNote"
                )
            }

            lastDesc.contains("lower") || lastDesc.contains("leg") || lastDesc.contains("squat") -> {
                // Next split: Full Body & High-Burn Cardio
                val exercises = when (userLevel) {
                    "BEGINNER" -> listOf(
                        ExerciseItem("1", "Jumping Jacks", 3, "${(25 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Mountain Climbers", 3, "${(20 * repMultiplier).toInt()} reps"),
                        ExerciseItem("3", "Incline Push-ups", 3, "${(10 * repMultiplier).toInt()} reps")
                    )
                    "ADVANCED" -> listOf(
                        ExerciseItem("1", "Jumping Jacks / High Knees", 4, "${(60 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Mountain Climbers", 4, "${(45 * repMultiplier).toInt()} reps"),
                        ExerciseItem("3", "Full Burpees with Push-up", 4, "${(15 * repMultiplier).toInt()} reps"),
                        ExerciseItem("4", "Kettlebell / Dumbbell Swings", 4, "${(20 * repMultiplier).toInt()} reps")
                    )
                    else -> listOf(
                        ExerciseItem("1", "Jumping Jacks", 4, "${(40 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Mountain Climbers", 3, "${(30 * repMultiplier).toInt()} reps"),
                        ExerciseItem("3", "Burpees / Sprawls", 3, "${(12 * repMultiplier).toInt()} reps"),
                        ExerciseItem("4", "High Knees", 3, "${(30 * repMultiplier).toInt()} secs")
                    )
                }

                TrainerWorkoutRecommendation(
                    splitTitle = "Full Body Conditioning & Cardio",
                    levelLabel = levelLabel,
                    estimatedCalories = if (userLevel == "BEGINNER") 200 else 330,
                    exercises = exercises,
                    trainerNotes = "Legs recovering. Today elevates heart rate with high-calorie conditioning.$rpeNote"
                )
            }

            else -> {
                // Default / First split: Upper Body & Arms
                val exercises = when (userLevel) {
                    "BEGINNER" -> listOf(
                        ExerciseItem("1", "Knee / Incline Push-ups", 3, "${(10 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Doorframe / Light Rows", 3, "${(12 * repMultiplier).toInt()} reps"),
                        ExerciseItem("3", "Chair Dips", 3, "${(10 * repMultiplier).toInt()} reps")
                    )
                    "ADVANCED" -> listOf(
                        ExerciseItem("1", "Standard / Weighted Push-ups", 4, "${(20 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Dumbbell / Barbell Rows", 4, "${(15 * repMultiplier).toInt()} reps"),
                        ExerciseItem("3", "Overhead Shoulder Press", 4, "${(12 * repMultiplier).toInt()} reps"),
                        ExerciseItem("4", "Tricep Dips & Bicep Curls", 4, "${(15 * repMultiplier).toInt()} reps")
                    )
                    else -> listOf(
                        ExerciseItem("1", "Standard Push-ups", 4, "${(14 * repMultiplier).toInt()} reps"),
                        ExerciseItem("2", "Dumbbell / Towel Rows", 4, "${(14 * repMultiplier).toInt()} reps"),
                        ExerciseItem("3", "Pike Push-ups (Shoulders)", 3, "${(10 * repMultiplier).toInt()} reps"),
                        ExerciseItem("4", "Chair Dips", 3, "${(12 * repMultiplier).toInt()} reps")
                    )
                }

                TrainerWorkoutRecommendation(
                    splitTitle = "Upper Body Strength & Arms",
                    levelLabel = levelLabel,
                    estimatedCalories = if (userLevel == "BEGINNER") 170 else 260,
                    exercises = exercises,
                    trainerNotes = "Targeting chest, back, and shoulders based on your onboarding level.$rpeNote"
                )
            }
        }
    }
}
