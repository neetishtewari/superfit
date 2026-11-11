package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.CoachInsightStore
import com.superfit.app.data.HabitEntryEntity
import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.Prefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.StepGoalStore
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.StepGoal
import com.superfit.app.domain.StepGoalResult
import com.superfit.app.domain.TodayVsUsual
import com.superfit.app.domain.UsualDay
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
 * Today for the iPhone, built from the same shared pieces as Android: today's meals, targets,
 * streak, weigh-ins and voice-logged workouts from [log], worked out by [TodayState.build]. Health
 * data (steps, sleep, workouts from other apps) joins when the iPhone reads Apple Health.
 */
class TodayModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val parser: NutritionParser = NutritionParser(),
    private val coachNotes: () -> List<CoachNote> = { emptyList() },
    private val coach: CoachingEngine = CoachingEngine(),
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

    /**
     * The same weekday's usual activity over the last 4 weeks, for "vs your usual" on Activity.
     * Re-read when today's steps or workouts change.
     */
    val usualDay: StateFlow<UsualDay?> = combine(
        log.profileFlow,
        log.getActivityFlow(today.toString()),
        log.getWorkoutEntriesForDay(today)
    ) { profile, _, _ ->
        if (profile == null) return@combine null
        TodayState.usualDay(
            voiceWorkouts = log.getAllWorkoutEntries(),
            activity = log.getAllActivityTelemetry(),
            imported = emptyList(),
            today = today,
            zone = zone()
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Today's activity next to [usualDay]; the distance is estimated from steps until Apple Health joins. */
    fun vsUsual(state: DashboardUiState.Success, usual: UsualDay?): TodayVsUsual = TodayState.vsUsual(state, usual, today)

    private val stepGoalTrigger = MutableStateFlow(0)

    /** Today's step goal, worked out from the step history (or the person's own number). */
    val stepGoal: StateFlow<StepGoalResult?> = combine(log.profileFlow, log.allActivityTelemetryFlow, stepGoalTrigger) { profile, activity, _ ->
        profile?.let { TodayCoach.stepGoal(it, activity, prefs, today) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _stepGoalNoteDismissedOn = MutableStateFlow(StepGoalStore.noteDismissedOn(prefs))
    val stepGoalNoteDismissedOn: StateFlow<String?> = _stepGoalNoteDismissedOn.asStateFlow()

    fun dismissStepGoalNote() {
        StepGoalStore.dismissNote(prefs, today)
        _stepGoalNoteDismissedOn.value = today.toString()
    }

    /** The person's own daily step goal, or null to go back to the automatic one. */
    fun setOwnStepGoal(steps: Int?) {
        StepGoalStore.setOwnGoal(prefs, steps?.coerceIn(StepGoal.OWN_MIN, StepGoal.OWN_MAX))
        stepGoalTrigger.value++
    }

    private val _coaching = MutableStateFlow<CoachingInsightState>(
        CoachInsightStore.loadForDay(prefs, today)?.let { CoachingInsightState.Success(it) } ?: CoachingInsightState.Idle
    )
    val coaching: StateFlow<CoachingInsightState> = _coaching.asStateFlow()

    /** Asks the AI coach for today's tip, the same way as Android, and keeps it for the day. */
    fun refreshCoachingInsight() {
        val current = state.value as? DashboardUiState.Success ?: return
        scope.launch {
            _coaching.value = CoachingInsightState.Loading
            _coaching.value = try {
                CoachingInsightState.Success(
                    TodayCoach.dailyTip(
                        state = current,
                        prefs = prefs,
                        nutrition = log.getAllNutritionEntries(),
                        workouts = log.getAllWorkoutEntries(),
                        activity = log.getAllActivityTelemetry(),
                        sleep = log.getAllSleepTelemetry(),
                        weights = log.getAllWeightEntries(),
                        stepGoal = stepGoal.value?.goal,
                        notes = coachNotes(),
                        today = today,
                        engine = coach
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("TodayModel", "Coach tip failed", e)
                CoachingInsightState.Error(AiFailover.sanitizeError(e))
            }
        }
    }

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

    fun deleteWorkout(entry: WorkoutEntryEntity) {
        scope.launch { log.deleteWorkoutEntry(entry) }
    }

    fun logWeight(weightKg: Double, note: String) {
        scope.launch { log.addWeightEntry(weightKg, note) }
    }
}
