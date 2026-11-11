package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.CoachInsightStore
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.Prefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.StepGoalStore
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.StepGoal
import com.superfit.app.domain.StepGoalReason
import com.superfit.app.domain.StepGoalResult
import kotlinx.datetime.LocalDate

/**
 * Today's coach tip and step goal, worked out the same way on Android and iPhone. Each app hands
 * in its own history (Android adds workouts from other apps) and the account's settings.
 */
object TodayCoach {

    /** Writes today's coach tip from the history, saves it for the day and remembers its focus. */
    suspend fun dailyTip(
        state: DashboardUiState.Success,
        prefs: Prefs,
        nutrition: List<NutritionEntryEntity>,
        workouts: List<WorkoutEntryEntity>,
        activity: List<ActivityTelemetryEntity>,
        sleep: List<SleepTelemetryEntity>,
        weights: List<WeightEntryEntity>,
        stepGoal: Int?,
        notes: List<CoachNote>,
        today: LocalDate,
        engine: CoachingEngine = CoachingEngine()
    ): CoachInsight {
        val customMacros = prefs.getBoolean("custom_macro_enabled", false)
        val insight = engine.generateDailyInsight(
            profile = state.profile,
            macroTargets = state.macroTargets,
            maintenanceCalories = if (customMacros) null else state.tdee,
            historyNutrition = nutrition,
            historyWorkouts = workouts,
            historyActivity = activity,
            historySleep = sleep,
            historyWeights = weights,
            stepGoal = stepGoal,
            memory = CoachInsightStore.memory(prefs),
            notes = notes,
            today = today
        )
        CoachInsightStore.save(prefs, today, insight)
        return insight
    }

    /**
     * Today's step goal from the step history, saved when it moves. [onAutoChange] hears whether
     * a worked-out goal went up, for usage counts. An own number is kept apart, so switching back
     * to automatic starts from the steps again.
     */
    fun stepGoal(
        profile: UserProfileEntity,
        activity: List<ActivityTelemetryEntity>,
        prefs: Prefs,
        today: LocalDate,
        onAutoChange: (up: Boolean) -> Unit = {}
    ): StepGoalResult {
        val steps = activity.mapNotNull { row -> runCatching { LocalDate.parse(row.date) to row.steps }.getOrNull() }.toMap()
        val saved = StepGoalStore.load(prefs)
        val result = StepGoal.update(steps, profile.age, saved, StepGoalStore.ownGoal(prefs), today)
        if (result.reason != StepGoalReason.OWN && result.saved != saved) {
            StepGoalStore.save(prefs, result.saved)
            if (saved != null && result.goal != saved.goal) onAutoChange(result.goal > saved.goal)
        }
        return result
    }
}
