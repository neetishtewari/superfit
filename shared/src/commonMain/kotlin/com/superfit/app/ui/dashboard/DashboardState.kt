package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.HabitEntryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.StreakStateEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.MacroTargets
import com.superfit.app.domain.PersonalBest
import com.superfit.app.domain.WorkoutView

// What the Today screen shows, the same on Android and iPhone. Each app's own code fills these in.

sealed interface ParsingState {
    object Idle : ParsingState
    object Loading : ParsingState

    /** [message] replaces the usual "Logged: …" banner, e.g. when a workout was logged too. */
    data class Success(val foodText: String, val message: String? = null) : ParsingState
    data class Error(val message: String) : ParsingState
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    object NotInitialized : DashboardUiState
    data class Success(
        val profile: UserProfileEntity,
        val activity: ActivityTelemetryEntity,
        val sleep: SleepTelemetryEntity?,
        val nutritionList: List<NutritionEntryEntity>,
        val workoutList: List<WorkoutEntryEntity>,
        val weightEntries: List<WeightEntryEntity> = emptyList(),
        val weightMetrics: com.superfit.app.domain.WeightGoalMetrics,
        val streakState: StreakStateEntity = StreakStateEntity(),
        /** Different days with at least one meal logged, ever; used to time the rating prompt. */
        val mealDaysLogged: Int = 0,
        val habitEntry: HabitEntryEntity = HabitEntryEntity(date = ""),
        val dietQualityMetrics: com.superfit.app.domain.DietQualityMetrics = com.superfit.app.domain.DietQualityMetrics(85, "BALANCED", emptyList(), emptyList(), emptyList()),
        val bmr: Double,
        val tdee: Double,
        val readinessScore: Int,
        val macroTargets: MacroTargets,
        val caloriesEaten: Double,
        val proteinEaten: Double,
        val carbsEaten: Double,
        val fatEaten: Double,
        val manualWorkoutCalories: Double,
        val totalMealsLogged: Int = 0,
        // Today's workouts from other apps, without doubles or hidden ones.
        val importedWorkouts: List<WorkoutView> = emptyList(),
        // Every app's copy counted, before doubles are merged; 0 while the import is off.
        val importedWorkoutCount: Int = 0,
        // How much of today's calorie target comes from today's workouts, and from which.
        val workoutTargetKcal: Double = 0.0,
        val workoutTargetSource: String = ""
    ) : DashboardUiState
}

/** Every personal best, and the new one (if any) to offer for sharing on Home. */
data class BestsUiState(
    val all: List<PersonalBest> = emptyList(),
    val toOffer: PersonalBest? = null
)

/** "Same workout as your Samsung Health run? Keep just one?" after a voice-logged workout. */
data class DuplicateWorkoutPrompt(val voiceEntryId: Long, val voiceDescription: String, val imported: WorkoutView)

sealed interface CoachingInsightState {
    object Idle : CoachingInsightState
    object Loading : CoachingInsightState
    data class Success(val insight: CoachInsight) : CoachingInsightState
    data class Error(val message: String) : CoachingInsightState
}
