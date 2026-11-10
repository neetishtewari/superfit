package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.HabitEntryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.Prefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.StepGoalStore
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.domain.DailyTarget
import com.superfit.app.domain.FoodQualityEngine
import com.superfit.app.domain.MacroTargets
import com.superfit.app.domain.PersonalNutrition
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.StepGoal
import com.superfit.app.domain.StreakEngine
import com.superfit.app.domain.WeightTrendEngine
import com.superfit.app.domain.WorkoutDisplay
import com.superfit.app.domain.WorkoutView
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Works out everything the Today screen shows from what is saved on the phone, the same on
 * Android and iPhone. Each app reads the pieces (database rows, settings, today's workouts from
 * other apps) and hands them in.
 */
object TodayState {

    /**
     * A day's targets with that day's workouts added back for people who connected them.
     * Imported workouts only count while the import is on.
     */
    fun target(
        profile: UserProfileEntity,
        workoutPrefs: WorkoutImportPrefs,
        imported: List<WorkoutView>,
        voiceLogged: List<WorkoutEntryEntity>
    ): DailyTarget = PhysiologyEngine.dailyTarget(
        profile = profile,
        workoutsConnected = workoutPrefs.enabled,
        addBack = workoutPrefs.addBack,
        exerciseKcal = WorkoutDisplay.dayExerciseKcal(
            if (workoutPrefs.enabled) imported else emptyList(),
            voiceLogged.sumOf { it.caloriesBurned }
        )
    )

    /** Different days with at least one meal logged. */
    @OptIn(ExperimentalTime::class)
    fun daysWithMeals(mealTimestamps: List<Long>, zone: TimeZone): Int =
        mealTimestamps.map { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }.distinct().size

    /**
     * [today] is the date as "2026-11-10". [importedWorkouts] are today's workouts from other apps,
     * already without doubles or hidden ones; they are ignored while the import is off.
     * [importedCount] counts every app's copy.
     */
    fun build(
        today: String,
        zone: TimeZone,
        profile: UserProfileEntity,
        activity: ActivityTelemetryEntity?,
        sleep: SleepTelemetryEntity?,
        nutrition: List<NutritionEntryEntity>,
        workouts: List<WorkoutEntryEntity>,
        weightEntries: List<WeightEntryEntity>,
        habit: HabitEntryEntity?,
        allNutritionHistory: List<NutritionEntryEntity>,
        importedWorkouts: List<WorkoutView>,
        importedCount: Int,
        workoutPrefs: WorkoutImportPrefs,
        prefs: Prefs
    ): DashboardUiState.Success {
        val bmr = PhysiologyEngine.calculateBmr(profile)
        val manualCal = workouts.sumOf { it.caloriesBurned }

        // Workouts from other apps. Their calories are already inside the health app's
        // "burned today", so they only move the target, never the burned number.
        val imported = if (workoutPrefs.enabled) importedWorkouts else emptyList()
        val dailyTarget = target(profile, workoutPrefs, imported, workouts)
        // What the body burns today with all of today's workouts, for the coach's deficit sums.
        val tdee = dailyTarget.maintenance
        val readiness = sleep?.readinessScore ?: 50 // Default 50% if no sleep session found

        // Macro targets from live TDEE and goal (or the user's own numbers)
        val customMacroEnabled = prefs.getBoolean("custom_macro_enabled", false)
        val macroTargets = if (customMacroEnabled) {
            MacroTargets(
                calories = prefs.getInt("custom_calories", 2000).toDouble(),
                proteinG = prefs.getInt("custom_protein_g", 130).toDouble(),
                carbsG = prefs.getInt("custom_carbs_g", 200).toDouble(),
                fatG = prefs.getInt("custom_fat_g", 70).toDouble()
            )
        } else {
            dailyTarget.targets
        }
        val workoutTargetKcal = if (customMacroEnabled) 0.0 else dailyTarget.workoutKcal

        val weightMetrics = WeightTrendEngine.calculateMetrics(profile, weightEntries)

        // Streak is derived from the full meal log (one missed day forgiven, two end it)
        val mealTimes = allNutritionHistory.map { it.timestamp }
        val streakState = StreakEngine.fromMealTimestamps(mealTimes)

        val currentSteps = activity?.steps ?: 0
        val currentSleepSecs = sleep?.sleepDurationSeconds ?: 0L
        val todayStepGoal = StepGoalStore.ownGoal(prefs) ?: StepGoalStore.load(prefs)?.goal ?: StepGoal.starter(profile.age)

        val habitEntry = (habit ?: HabitEntryEntity(date = today)).copy(
            stepsCompleted = currentSteps >= todayStepGoal,
            sleepCompleted = currentSleepSecs >= (7 * 3600),
            workoutMins = workouts.size * 25
        )

        // What the AI should weigh when it rates this person's meals
        PersonalNutrition.goal = profile.goal
        PersonalNutrition.calorieTarget = macroTargets.calories
        PersonalNutrition.proteinTargetG = macroTargets.proteinG

        val dietQualityMetrics = FoodQualityEngine.analyzeDietQuality(
            nutritionHistory = allNutritionHistory,
            userGoal = profile.goal,
            proteinTargetG = macroTargets.proteinG,
            calorieTarget = macroTargets.calories
        )

        return DashboardUiState.Success(
            profile = profile,
            activity = activity ?: ActivityTelemetryEntity(today, 0, 0.0),
            sleep = sleep,
            nutritionList = nutrition,
            workoutList = workouts,
            weightEntries = weightEntries,
            weightMetrics = weightMetrics,
            streakState = streakState,
            mealDaysLogged = daysWithMeals(mealTimes, zone),
            habitEntry = habitEntry,
            dietQualityMetrics = dietQualityMetrics,
            bmr = bmr,
            tdee = tdee,
            readinessScore = readiness,
            macroTargets = macroTargets,
            caloriesEaten = nutrition.sumOf { it.calories },
            proteinEaten = nutrition.sumOf { it.proteinG },
            carbsEaten = nutrition.sumOf { it.carbsG },
            fatEaten = nutrition.sumOf { it.fatG },
            manualWorkoutCalories = manualCal,
            totalMealsLogged = allNutritionHistory.size,
            importedWorkouts = imported,
            importedWorkoutCount = if (workoutPrefs.enabled) importedCount else 0,
            workoutTargetKcal = workoutTargetKcal,
            workoutTargetSource = WorkoutDisplay.boostSource(imported, workouts.size)
        )
    }
}
