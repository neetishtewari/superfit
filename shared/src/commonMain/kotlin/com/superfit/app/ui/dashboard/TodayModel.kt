package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.CoachInsightStore
import com.superfit.app.data.CoachStyleStore
import com.superfit.app.data.HabitEntryEntity
import com.superfit.app.data.HealthSource
import com.superfit.app.data.HealthSync
import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionAdviceStore
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.Prefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.StepGoalStore
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.domain.AdviceTargets
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.FitnessGoal
import com.superfit.app.domain.LogKind
import com.superfit.app.domain.MealTagBackfill
import com.superfit.app.domain.MealTagger
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.NutritionistNote
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
 * streak, weigh-ins and voice-logged workouts from [log], worked out by [TodayState.build]. Steps,
 * active calories, distance and sleep come from the phone's health app ([health], Apple Health)
 * once the person connects it; workouts from other apps join in a later step.
 */
class TodayModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val parser: NutritionParser = NutritionParser(),
    private val coachNotes: () -> List<CoachNote> = { emptyList() },
    private val coach: CoachingEngine = CoachingEngine(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val health: HealthSource? = null
) {
    private val today = Now.today()

    // The person's coach style and own nutritionist's advice, before anything is worked out with them.
    init {
        CoachStyleStore.load(prefs)
        NutritionAdviceStore.load(prefs)
    }

    private val _coachStyle = MutableStateFlow(CoachStyle.current)
    val coachStyle: StateFlow<CoachStyle> = _coachStyle.asStateFlow()

    private val _parsing = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsing: StateFlow<ParsingState> = _parsing.asStateFlow()

    private val _predictedFoods = MutableStateFlow<List<PredictedFood>>(emptyList())
    val predictedFoods: StateFlow<List<PredictedFood>> = _predictedFoods.asStateFlow()

    private var lastMeal: String? = null
    val canRetryLastMeal: Boolean get() = !lastMeal.isNullOrBlank()

    // Settings saved outside the database (own calorie targets) ask Today to work things out again.
    private val settingsChanged = MutableStateFlow(0)

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
            log.allNutritionEntriesFlow,
            settingsChanged
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

    /** Today's activity next to [usualDay], with the health app's distance when it has one (else estimated from steps). */
    fun vsUsual(state: DashboardUiState.Success, usual: UsualDay?, distanceKm: Double? = null): TodayVsUsual =
        TodayState.vsUsual(state, usual, today, distanceKm)

    /** Whether this phone has a health app Superfit can read (false on iPads and without one). */
    val healthAvailable: Boolean = health?.isAvailable() == true

    private val _healthAsked = MutableStateFlow(healthAvailable && prefs.getBoolean(HealthSync.KEY_ASKED, false))

    /** True once the person answered the health app's access sheet; then Today reads it on every open. */
    val healthAsked: StateFlow<Boolean> = _healthAsked.asStateFlow()

    private val _distanceKm = MutableStateFlow<Double?>(null)

    /** Today's walked and run distance from the health app, or null to estimate it from steps. */
    val distanceKm: StateFlow<Double?> = _distanceKm.asStateFlow()

    /** Shows the phone's own access sheet, then reads today's steps and sleep straight away. */
    fun connectHealth() {
        val source = health?.takeIf { healthAvailable } ?: return
        scope.launch {
            val answered = try {
                source.requestAccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("TodayModel", "Asking for health access failed", e)
                false
            }
            if (!answered) return@launch
            prefs.edit { putBoolean(HealthSync.KEY_ASKED, true) }
            _healthAsked.value = true
            readHealth(source)
        }
    }

    /** Reads today's and yesterday's steps and sleep again; the app calls this whenever it comes to the front. */
    fun syncHealth() {
        val source = health?.takeIf { _healthAsked.value } ?: return
        scope.launch { readHealth(source) }
    }

    private suspend fun readHealth(source: HealthSource) {
        HealthSync.sync(source, log.healthStore, Now.today())
        _distanceKm.value = try {
            source.readDistanceMeters(Now.today())?.takeIf { it > 0 }?.div(1000)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

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

    /** After the person saves their own calorie and macro targets in Settings. */
    fun refreshTargets() {
        settingsChanged.value++
    }

    /** A new goal (lose, keep or gain) and its calorie change, as Settings on Android saves it. */
    fun updateGoal(goal: FitnessGoal) {
        scope.launch {
            log.getProfile()?.let { log.saveProfile(it.copy(goal = goal.key, calorieOffset = goal.calorieOffset)) }
        }
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
        tagOlderMeals()
        syncHealth()
    }

    /** Saves the AI coach style and rewrites today's tip in it, if there is one, as on Android. */
    fun setCoachStyle(style: CoachStyle) {
        // Saved even when unchanged, so a later one-time chooser counts it as an answer.
        CoachStyleStore.save(prefs, style)
        if (style == _coachStyle.value) return
        _coachStyle.value = style
        if (_coaching.value is CoachingInsightState.Success) refreshCoachingInsight()
    }

    /** Saves the nutritionist's advice, has recent meals rated again with it, and reads any daily targets in it. */
    suspend fun saveNutritionistAdvice(text: String): AdviceTargets? {
        NutritionAdviceStore.saveAdvice(prefs, text)
        prefs.edit { remove(BACKFILL_KEY) }
        tagOlderMeals()
        refreshTargets()
        if (text.isBlank()) return null
        return try {
            NutritionistNote.readTargets(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("TodayModel", "Couldn't read targets from the advice", e)
            null
        }
    }

    fun applyNutritionistTargets(targets: AdviceTargets?) {
        NutritionAdviceStore.applyTargets(prefs, targets)
        refreshTargets()
    }

    /** Once a day, recent meals are judged (again) the same way as on Android. */
    private fun tagOlderMeals() {
        val day = today.toString()
        if (prefs.getString(BACKFILL_KEY, "") == day) return
        scope.launch {
            try {
                val due = MealTagBackfill.due(log.getAllNutritionEntries(), Now.millis())
                MealTagBackfill.tag(due, MealTagger()) { entry, tags -> log.saveMealTags(entry, tags) }
                prefs.edit { putString(BACKFILL_KEY, day) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("TodayModel", "Tagging older meals failed; will retry next launch", e)
            }
        }
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

    /** Logs a spoken meal, workout or both, the same way as Android's mic (for iPhone voice, step 5b-6). */
    private val smartLogger = SmartLogger(
        history = { log.getAllNutritionEntries() },
        saveMeal = { result ->
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
        },
        saveWorkout = { result ->
            log.addWorkoutEntry(
                WorkoutEntryEntity(
                    description = result.workoutText,
                    caloriesBurned = result.caloriesBurned,
                    workoutType = result.workoutType,
                    setsCount = result.setsCount,
                    repsCount = result.repsCount,
                    timestamp = Now.millis(),
                    difficultyRating = "JUST_RIGHT"
                )
            )
        }
    )

    fun smartLog(input: String, otherVersions: List<String> = emptyList(), kind: LogKind = LogKind.ANY) {
        if (input.isBlank()) return
        scope.launch {
            _parsing.value = ParsingState.Loading
            _parsing.value = smartLogger.log(input, otherVersions, kind)
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

    private companion object {
        // The same day stamp Android keeps, bumped with the nutritionist ratings.
        const val BACKFILL_KEY = "meal_tag_backfill_date_v3"
    }
}
