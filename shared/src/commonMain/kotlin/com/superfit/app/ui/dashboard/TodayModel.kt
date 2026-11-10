package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.HabitEntryEntity
import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.Prefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.NutritionParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The Food side of Today for the iPhone, built from the same shared pieces as Android: today's
 * meals, targets, streak and weigh-ins from [log], worked out by [TodayState.build]. Health data
 * (steps, sleep, workouts from other apps) joins when the iPhone reads Apple Health.
 */
class TodayModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val parser: NutritionParser = NutritionParser(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    private val today = Now.today()

    private val _parsing = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsing: StateFlow<ParsingState> = _parsing.asStateFlow()

    private val _predictedFoods = MutableStateFlow<List<PredictedFood>>(emptyList())
    val predictedFoods: StateFlow<List<PredictedFood>> = _predictedFoods.asStateFlow()

    private var lastMeal: String? = null
    val canRetryLastMeal: Boolean get() = !lastMeal.isNullOrBlank()

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<DashboardUiState> = combine(
        listOf<Flow<Any?>>(
            log.profileFlow,
            log.getActivityFlow(today.toString()),
            log.getSleepFlow(today.toString()),
            log.getNutritionEntriesForDay(today),
            log.getWorkoutEntriesForDay(today),
            log.weightEntriesFlow,
            log.getHabitEntryFlow(today.toString()),
            log.allNutritionEntriesFlow
        )
    ) { values ->
        val profile = values[0] as? UserProfileEntity ?: return@combine DashboardUiState.NotInitialized
        TodayState.build(
            today = today.toString(),
            zone = zone(),
            profile = profile,
            activity = values[1] as? ActivityTelemetryEntity,
            sleep = values[2] as? SleepTelemetryEntity,
            nutrition = values[3] as? List<NutritionEntryEntity> ?: emptyList(),
            workouts = values[4] as? List<WorkoutEntryEntity> ?: emptyList(),
            weightEntries = values[5] as? List<WeightEntryEntity> ?: emptyList(),
            habit = values[6] as? HabitEntryEntity,
            allNutritionHistory = values[7] as? List<NutritionEntryEntity> ?: emptyList(),
            importedWorkouts = emptyList(),
            importedCount = 0,
            workoutPrefs = WorkoutImportPrefs(prefs),
            prefs = prefs
        )
    }.stateIn(scope, SharingStarted.Eagerly, DashboardUiState.Loading)

    init {
        loadPredictedFoods()
    }

    private fun loadPredictedFoods() {
        scope.launch {
            _predictedFoods.value = runCatching { log.getPredictedFoods(Now.hour()) }.getOrDefault(emptyList())
        }
    }

    /** Works out a typed meal with the AI and logs it, the same way as Android. */
    fun logMeal(text: String) {
        if (text.isBlank()) return
        lastMeal = text
        scope.launch {
            _parsing.value = ParsingState.Loading
            _parsing.value = try {
                val result = parser.parseFoodInput(text, log.getAllNutritionEntries())
                if (result.foodText == "invalid") {
                    ParsingState.Error("Could not recognize any food items. Please try saying 'one apple and some greek yogurt'.")
                } else {
                    log.addNutritionEntry(
                        NutritionEntryEntity(
                            foodText = result.foodText,
                            calories = result.calories,
                            proteinG = result.proteinG,
                            carbsG = result.carbsG,
                            fatG = result.fatG,
                            timestamp = Now.millis(),
                            qualityTags = result.tags?.encode(),
                            items = result.items?.encode()
                        )
                    )
                    loadPredictedFoods()
                    ParsingState.Success(result.foodText)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("TodayModel", "Meal parsing failed", e)
                ParsingState.Error(AiFailover.sanitizeError(e))
            }
        }
    }

    fun retryLastMeal() {
        lastMeal?.let { logMeal(it) }
    }

    fun updateMeal(entry: NutritionEntryEntity) {
        scope.launch { log.updateNutritionEntry(entry) }
    }

    fun deleteMeal(entry: NutritionEntryEntity) {
        scope.launch { log.deleteNutritionEntry(entry) }
    }

    fun logWeight(weightKg: Double, note: String) {
        scope.launch { log.addWeightEntry(weightKg, note) }
    }
}
