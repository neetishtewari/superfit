package com.superfit.app.ui.dashboard

import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.LogKind
import com.superfit.app.domain.LogRouter
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.ParsedNutritionResult
import com.superfit.app.domain.ParsedWorkoutResult
import com.superfit.app.domain.RepeatMealCache
import com.superfit.app.domain.SpokenInput
import com.superfit.app.domain.WorkoutParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Logs what was said, the same way on Android and iPhone. From the Food or Activity mic ([LogKind])
 * it goes straight to that parser; otherwise it can be food, exercise or both and is split by
 * [LogRouter] first. An exact repeat of one of the user's own meals is logged straight away. If
 * one part can't be understood, the other is still logged. Each app saves through [saveMeal] and
 * [saveWorkout], so its own counts and follow-ups stay with it.
 */
class SmartLogger(
    private val history: suspend () -> List<NutritionEntryEntity>,
    private val saveMeal: suspend (ParsedNutritionResult) -> Unit,
    private val saveWorkout: suspend (ParsedWorkoutResult) -> Unit,
    private val router: LogRouter = LogRouter(),
    private val nutrition: NutritionParser = NutritionParser(),
    private val workouts: WorkoutParser = WorkoutParser()
) {

    /**
     * Returns what the screen shows afterwards. [onMealSaved] hears the exercise part still to
     * log once the meal is saved, so a retry never logs the meal twice.
     */
    suspend fun log(
        input: String,
        otherVersions: List<String> = emptyList(),
        kind: LogKind = LogKind.ANY,
        onMealSaved: (remainingExercise: String) -> Unit = {}
    ): ParsingState {
        val spoken = SpokenInput(input, otherVersions)
        return try {
            val past = history()
            if (kind != LogKind.WORKOUT) {
                RepeatMealCache.findRepeatMeal(input, past)?.let { repeat ->
                    saveMeal(repeat)
                    return ParsingState.Success(repeat.foodText)
                }
            }

            val routed = kind.presplit(input) ?: router.route(spoken)
            if (routed.isEmpty) return ParsingState.Error(kind.nothingRecognised)

            val (meal, workout) = coroutineScope {
                val mealJob = async {
                    if (routed.food.isBlank()) null else runCatching { nutrition.parseFoodInput(routed.food, past, spoken) }
                }
                val workoutJob = async {
                    if (routed.exercise.isBlank()) null else runCatching { workouts.parseWorkoutInput(routed.exercise) }
                }
                mealJob.await() to workoutJob.await()
            }
            listOfNotNull(meal?.exceptionOrNull(), workout?.exceptionOrNull())
                .firstOrNull { it is CancellationException }
                ?.let { throw it }

            val mealResult = meal?.getOrNull()?.takeIf { it.foodText != "invalid" }
            val workoutResult = workout?.getOrNull()?.takeIf { it.workoutText != "invalid" }
            mealResult?.let {
                saveMeal(it)
                onMealSaved(routed.exercise)
            }
            workoutResult?.let { saveWorkout(it) }
            val foodMissed = routed.food.isNotBlank() && mealResult == null
            val exerciseMissed = routed.exercise.isNotBlank() && workoutResult == null

            when {
                mealResult != null && workoutResult != null -> ParsingState.Success(
                    mealResult.foodText,
                    message = "Logged: ${mealResult.foodText} · Workout: ${workoutResult.workoutText}"
                )
                mealResult != null -> ParsingState.Success(
                    mealResult.foodText,
                    message = if (exerciseMissed) "Logged: ${mealResult.foodText} · Couldn't log the exercise. Try saying it again." else null
                )
                workoutResult != null -> ParsingState.Success(
                    workoutResult.workoutText,
                    message = if (foodMissed) {
                        "Logged workout: ${workoutResult.workoutText} · Couldn't log the food. Try saying it again."
                    } else {
                        "Logged workout: ${workoutResult.workoutText}"
                    }
                )
                else -> {
                    val failure = meal?.exceptionOrNull() ?: workout?.exceptionOrNull()
                    if (failure != null) AppLog.w("SmartLogger", "Smart log failed", failure)
                    ParsingState.Error(failure?.let { AiFailover.sanitizeError(it) } ?: kind.nothingRecognised)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("SmartLogger", "Smart log failed", e)
            ParsingState.Error(AiFailover.sanitizeError(e))
        }
    }
}
