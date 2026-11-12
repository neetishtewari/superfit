package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachChatContext
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachPatternEngine
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.Decimals
import com.superfit.app.domain.MealTags
import com.superfit.app.domain.PersonalNutrition
import com.superfit.app.domain.ScaleReading
import com.superfit.app.domain.currentAge
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Everything the coach chat's instructions are written from, as each app reads it. */
data class CoachChatFacts(
    val state: DashboardUiState.Success,
    val today: LocalDate,
    val now: LocalTime,
    val nowMs: Long,
    /** The person's own smart-scale readings (not a partner's). */
    val scaleReadings: List<ScaleReading>,
    /** Today's steps and workouts next to the usual for this weekday, as one line. */
    val vsUsualLine: String,
    val allNutrition: List<NutritionEntryEntity>,
    /** Voice-logged workouts plus the ones from other apps, in the shape the coach reads. */
    val workouts: List<WorkoutEntryEntity>,
    val activity: List<ActivityTelemetryEntity>,
    val sleep: List<SleepTelemetryEntity>,
    val weights: List<WeightEntryEntity>,
    val stepGoal: Int?,
    /** The person set their own calorie and macro targets (so there's no maintenance figure). */
    val customMacros: Boolean,
    /** The goal weight, only when the person set one. */
    val targetWeightKg: Double?,
    val goalDate: LocalDate?,
    val notes: List<CoachNote>,
    val todayTip: CoachInsight?,
    /** The conversation before this question, without error lines. */
    val conversation: List<ChatEntry>
)

/** The coach chat's instructions, the same on Android and iPhone: live numbers, the person's own logs and notes, and the rules. */
object CoachChatPrompt {

    fun instruction(facts: CoachChatFacts, style: CoachStyle = CoachStyle.current): String {
        val state = facts.state
        val profile = state.profile
        val macroTargets = state.macroTargets
        val sleep = state.sleep
        val nutrition = state.nutritionList

        val sleepText = if (sleep != null) {
            "Sleep Duration: ${Decimals.one(sleep.sleepDurationSeconds / 3600.0)}h, Readiness Score: ${sleep.readinessScore}%"
        } else {
            "No sleep data today."
        }

        val nutritionText = "Calories Eaten: ${state.caloriesEaten.toInt()} / ${macroTargets.calories.toInt()} kcal. " +
            "Protein: ${state.proteinEaten.toInt()} / ${macroTargets.proteinG.toInt()}g. " +
            "Carbs: ${state.carbsEaten.toInt()} / ${macroTargets.carbsG.toInt()}g. " +
            "Fat: ${state.fatEaten.toInt()} / ${macroTargets.fatG.toInt()}g. " +
            "Fibre (estimated): ${nutrition.sumOf { MealTags.of(it).fiberG }.toInt()} / ${CoachPatternEngine.fibreTargetG(macroTargets.calories).toInt()}g."

        val activityText = "Steps: ${state.activity.steps}, Active Calories Burned: ${state.activity.activeCalories.toInt()} kcal."

        val body = BodyReadings.summary(facts.scaleReadings, profile.weightKg, profile.heightCm, facts.today)
        val bodyText = if (body.hasScaleData) {
            listOfNotNull(
                body.bodyFatPct?.let { "body fat ${BodyReadings.fmt(it)}%" + (body.bodyFatChange?.let { c -> " (${if (c > 0) "+" else ""}${BodyReadings.fmt(c)} pts over 4 weeks)" } ?: "") },
                body.leanKg?.let { "lean mass ${BodyReadings.fmt(it)} kg" },
                body.insight
            ).joinToString(", ")
        } else {
            "no smart scale data"
        }

        val report = CoachPatternEngine.analyze(
            today = facts.today,
            now = facts.now,
            weightKg = profile.weightKg,
            goal = profile.goal,
            targets = macroTargets,
            maintenanceCalories = if (facts.customMacros) null else state.tdee,
            nutrition = facts.allNutrition,
            workouts = facts.workouts,
            activity = facts.activity,
            sleep = facts.sleep,
            weights = facts.weights,
            targetWeightKg = profile.targetWeightKg,
            age = profile.currentAge(),
            stepGoal = facts.stepGoal
        )

        val live = """
            You are Superfit AI Coach, a friendly, knowledgeable personal trainer and nutrition coach who knows this user well.
            Answer the user's questions about their fitness, food, sleep and progress.
            Their numbers today:
            - Profile: Age ${profile.currentAge()}, Weight ${profile.weightKg} kg, Height ${profile.heightCm} cm
            - Body (smart scale): $bodyText
            - Goal: ${profile.goal} (Calorie Offset: ${profile.calorieOffset} kcal)
            - Their own nutritionist's advice (follow it, never contradict it): ${PersonalNutrition.advice ?: "none given"}
            - Targets: Protein ${macroTargets.proteinG.toInt()}g, Carbs ${macroTargets.carbsG.toInt()}g, Fat ${macroTargets.fatG.toInt()}g, Calories ${macroTargets.calories.toInt()} kcal
            - Today's nutrition: $nutritionText
            - Today's activity: $activityText
            - ${facts.vsUsualLine}
            - Today's sleep: $sleepText
        """.trimIndent()

        val personal = CoachChatContext.personal(
            today = facts.today,
            now = facts.now,
            todayMeals = nutrition,
            report = report,
            workouts = facts.workouts,
            targetWeightKg = facts.targetWeightKg,
            goalDate = facts.goalDate,
            commonFoods = CoachingEngine.commonFoods(facts.allNutrition, facts.today),
            notes = facts.notes,
            todayTip = facts.todayTip,
            earlierTopics = CoachChat.earlierTopics(facts.conversation, facts.nowMs)
        )

        return listOf(live, personal, CoachingEngine.chatContext(report).trim(), CoachChatContext.RULES + style.instruction())
            .joinToString("\n\n")
    }
}
