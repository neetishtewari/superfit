package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.CoachInsightStore
import com.superfit.app.data.CoachStyleStore
import com.superfit.app.data.DayTotalsBuilder
import com.superfit.app.data.GoalDatePref
import com.superfit.app.data.HabitEntryEntity
import com.superfit.app.data.HealthSource
import com.superfit.app.data.HealthSourceNames
import com.superfit.app.data.HealthSync
import com.superfit.app.data.ImportedWorkoutEntity
import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionAdviceStore
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.Prefs
import com.superfit.app.data.ScaleImport
import com.superfit.app.data.ShareCardPrefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.StepGoalStore
import com.superfit.app.data.UsageEvents
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.data.WorkoutSync
import com.superfit.app.domain.AdviceTargets
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachQuestions
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.DaySteps
import com.superfit.app.domain.FitnessGoal
import com.superfit.app.domain.LogKind
import com.superfit.app.domain.MealTagBackfill
import com.superfit.app.domain.MealTagger
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.NutritionistNote
import com.superfit.app.domain.PersonalBest
import com.superfit.app.domain.PersonalBests
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.ScaleReading
import com.superfit.app.domain.ShareOffer
import com.superfit.app.domain.StepGoal
import com.superfit.app.domain.StepGoalResult
import com.superfit.app.domain.TodayVsUsual
import com.superfit.app.domain.UsualDay
import com.superfit.app.domain.WorkoutAddBack
import com.superfit.app.domain.WorkoutView
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Today for the iPhone, built from the same shared pieces as Android: today's meals, targets,
 * streak, weigh-ins and voice-logged workouts from [log], worked out by [TodayState.build]. Steps,
 * active calories, distance and sleep come from the phone's health app ([health], Apple Health)
 * once the person connects it, and workouts other apps and the watch saved there.
 */
class TodayModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val parser: NutritionParser = NutritionParser(),
    private val coachNotes: () -> List<CoachNote> = { emptyList() },
    private val coach: CoachingEngine = CoachingEngine(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val health: HealthSource? = null,
    private val scale: ScaleImport? = null,
    voice: VoiceRecognizer? = null
) {
    private val today = Now.today()

    /** The day this Today shows (fixed when it opens, like Android's dashboard date). */
    val day: String get() = today.toString()

    // The person's coach style and own nutritionist's advice, before anything is worked out with them.
    init {
        CoachStyleStore.load(prefs)
        NutritionAdviceStore.load(prefs)
        // The last body fat from a smart scale, for the calorie target.
        scale?.load()
    }

    private val _coachStyle = MutableStateFlow(CoachStyle.current)
    val coachStyle: StateFlow<CoachStyle> = _coachStyle.asStateFlow()

    private val _parsing = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsing: StateFlow<ParsingState> = _parsing.asStateFlow()

    private val _predictedFoods = MutableStateFlow<List<PredictedFood>>(emptyList())
    val predictedFoods: StateFlow<List<PredictedFood>> = _predictedFoods.asStateFlow()
    private val _mealSuggester = MutableStateFlow(com.superfit.app.domain.MealSuggester.EMPTY)
    val mealSuggester: StateFlow<com.superfit.app.domain.MealSuggester> = _mealSuggester.asStateFlow()

    private var lastMeal: String? = null

    /** Today's mic buttons, or null where the app doesn't provide speech recognition yet. */
    val voiceLog: VoiceLog? = voice?.let { recognizer ->
        VoiceLog(recognizer) { heard, kind -> smartLog(heard.text, heard.otherVersions, kind) }
    }
    val canRetryLastMeal: Boolean get() = !lastMeal.isNullOrBlank()

    // Settings saved outside the database (own calorie targets) ask Today to work things out again.
    private val settingsChanged = MutableStateFlow(0)

    private val workoutPrefs = WorkoutImportPrefs(prefs)
    private val sourceNames = HealthSourceNames(prefs)

    /** Workouts from other apps as Today and the usual day show them; empty while the import is off. */
    private fun workoutViews(rows: List<ImportedWorkoutEntity>, profile: UserProfileEntity): List<WorkoutView> =
        WorkoutSync.views(rows, workoutPrefs, sourceNames, PhysiologyEngine.calculateBmr(profile))

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
            settingsChanged,
            log.allImportedWorkoutsFlow
        )
    ) { values ->
        val profile = values[0] as? UserProfileEntity ?: return@combine DashboardUiState.NotInitialized
        val importedRows = values[9] as? List<ImportedWorkoutEntity> ?: emptyList()
        val todayViews = DayTotalsBuilder.byDate(workoutViews(importedRows, profile), zone())[today].orEmpty()
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
            importedWorkouts = todayViews,
            importedCount = importedRows.size,
            workoutPrefs = workoutPrefs,
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
        log.getWorkoutEntriesForDay(today),
        log.allImportedWorkoutsFlow
    ) { profile, _, _, imported ->
        if (profile == null) return@combine null
        TodayState.usualDay(
            voiceWorkouts = log.getAllWorkoutEntries(),
            activity = log.getAllActivityTelemetry(),
            imported = workoutViews(imported, profile),
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
            // Connecting brings workouts in too, as turning on the import does on Android.
            if (!workoutPrefs.enabled) workoutPrefs.enabled = true
            _workoutImportEnabled.value = true
            _healthAsked.value = true
            readHealth(source)
        }
    }

    /** Reads today's and yesterday's steps and sleep again; the app calls this whenever it comes to the front. */
    fun syncHealth() {
        val askedBefore = HealthSync.EARLIER_KEYS.any { prefs.getBoolean(it, false) }
        val source = health?.takeIf { healthAvailable && (_healthAsked.value || askedBefore) } ?: return
        scope.launch { readHealth(source) }
    }

    private val _workoutImportEnabled = MutableStateFlow(workoutPrefs.enabled)

    /** Whether workouts from other apps are brought in (on once Apple Health is connected). */
    val workoutImportEnabled: StateFlow<Boolean> = _workoutImportEnabled.asStateFlow()

    private val _workoutImportRunning = MutableStateFlow(false)
    val workoutImportRunning: StateFlow<Boolean> = _workoutImportRunning.asStateFlow()

    private val _pendingScaleReading = MutableStateFlow(scale?.nextPending())

    /** A smart-scale reading far from the person's own, waiting for "Is this you?". */
    val pendingScaleReading: StateFlow<ScaleReading?> = _pendingScaleReading.asStateFlow()

    fun answerScaleReading(isMe: Boolean) {
        val reading = _pendingScaleReading.value ?: return
        val importer = scale ?: return
        scope.launch {
            importer.answer(reading.id, isMe)
            _pendingScaleReading.value = importer.nextPending()
            settingsChanged.value++
        }
    }

    /** "Not now": asked again next time the app opens. */
    fun postponeScaleReading() {
        _pendingScaleReading.value = null
    }

    /** Hides a workout from another app (every app's copy of it), as on Android. */
    fun hideImportedWorkout(view: WorkoutView) {
        workoutPrefs.hide(view.merged.memberIds)
        workoutSettingsChanged()
    }

    /**
     * Personal bests from every workout from other apps and daily steps, plus the one new best to
     * offer on Today, as on Android. Workout bests wait for the first import to finish, so it sets
     * the starting points quietly.
     */
    val personalBests: StateFlow<BestsUiState> = combine(
        log.profileFlow,
        log.allImportedWorkoutsFlow,
        log.allActivityTelemetryFlow,
        settingsChanged
    ) { profile, rows, activity, _ ->
        if (profile == null) return@combine BestsUiState()
        val views = if (workoutPrefs.enabled && workoutPrefs.lastImportMillis > 0) workoutViews(rows, profile) else emptyList()
        val steps = activity.mapNotNull { row -> runCatching { DaySteps(LocalDate.parse(row.date), row.steps) }.getOrNull() }
        val bests = PersonalBests.compute(views, steps, Now.today())
        val baseline = workoutPrefs.bestBaseline
        val seen = workoutPrefs.bestPodiumSeen
        val check = PersonalBests.check(bests, baseline, seen, Now.millis())
        if (check.baseline != baseline || check.podiumSeen != seen) {
            workoutPrefs.bestBaseline = check.baseline
            workoutPrefs.bestPodiumSeen = check.podiumSeen
            backUpWorkoutSettings()
        }
        BestsUiState(all = bests, toOffer = check.announce.firstOrNull())
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), BestsUiState())

    /** A new best was shared or dismissed: it's offered only once. */
    fun acknowledgeBest(best: PersonalBest) {
        workoutPrefs.bestBaseline = PersonalBests.acknowledge(best, workoutPrefs.bestBaseline)
        workoutPrefs.bestPodiumSeen = PersonalBests.acknowledgePodium(best, workoutPrefs.bestPodiumSeen)
        workoutSettingsChanged()
    }

    /** Settings › workouts from other apps: what's chosen now. */
    val workoutImportPrefs: WorkoutImportPrefs get() = workoutPrefs

    /** Turning the import on asks for health access first when it hasn't been asked yet. */
    fun setWorkoutImportEnabled(on: Boolean) {
        if (on && !_healthAsked.value) {
            connectHealth()
            return
        }
        workoutPrefs.enabled = on
        _workoutImportEnabled.value = on
        workoutSettingsChanged()
        if (on) UsageEvents.workoutsConnected()
        if (on) health?.takeIf { healthAvailable }?.let { source -> scope.launch { readHealth(source) } }
    }

    fun setMainWorkoutApp(sourceId: String?) {
        workoutPrefs.mainApp = sourceId
        workoutSettingsChanged()
    }

    fun setWorkoutAddBack(addBack: WorkoutAddBack) {
        workoutPrefs.addBack = addBack
        workoutSettingsChanged()
    }

    fun unhideAllWorkouts() {
        workoutPrefs.unhideAll()
        workoutSettingsChanged()
    }

    /** Apps that have shared workouts, as (id, name), for the "Main workout app" choice. */
    suspend fun workoutSourceApps(): List<Pair<String, String>> =
        log.importedWorkouts.getAll().map { it.sourcePackage }.distinct()
            .map { it to sourceNames.label(it) }
            .sortedBy { it.second }

    /** Today works its numbers out again, and the choices go to the backup. */
    private fun workoutSettingsChanged() {
        settingsChanged.value++
        backUpWorkoutSettings()
    }

    private fun backUpWorkoutSettings() {
        scope.launch {
            try {
                log.backUpWorkoutImportSettings(workoutPrefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("TodayModel", "Backing up workout settings failed", e)
            }
        }
    }

    private suspend fun readHealth(source: HealthSource) {
        HealthSync.sync(source, log.healthStore, Now.today())
        log.getProfile()?.let { profile ->
            _workoutImportRunning.value = workoutPrefs.enabled && workoutPrefs.lastImportMillis == 0L
            try {
                WorkoutSync.import(source, log.importedWorkouts, workoutPrefs, sourceNames, PhysiologyEngine.calculateBmr(profile), Now.millis())
            } finally {
                _workoutImportRunning.value = false
            }
        }
        scale?.let { importer ->
            importer.importNew(source, Now.millis())
            _pendingScaleReading.value = importer.nextPending()
            // Body fat may have changed the calorie target.
            settingsChanged.value++
        }
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
        UsageEvents.goalSet(withDate = false)
    }

    /** Sex, birth month, height and activity level from Settings > About you. */
    fun saveAboutYou(updated: UserProfileEntity) {
        UsageEvents.profileEdited()
        scope.launch {
            // Only the About you fields: a weigh-in or goal saved meanwhile is kept.
            log.getProfile()?.let {
                log.saveProfile(
                    it.copy(
                        isMale = updated.isMale,
                        age = updated.age,
                        birthYearMonth = updated.birthYearMonth,
                        heightCm = updated.heightCm,
                        activityMultiplier = updated.activityMultiplier
                    )
                )
            }
        }
    }

    /** The person's own daily step goal, or null to go back to the automatic one. */
    fun setOwnStepGoal(steps: Int?) {
        StepGoalStore.setOwnGoal(prefs, steps?.coerceIn(StepGoal.OWN_MIN, StepGoal.OWN_MAX))
        UsageEvents.stepGoalChanged("set", if (steps == null) "auto" else "own")
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
                        workouts = coachWorkouts(current),
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

    /** Every workout from other apps as Today shows them, for the share cards; empty while the import is off. */
    suspend fun importedViews(bmr: Double): List<WorkoutView> = WorkoutSync.views(log.importedWorkouts.getAll(), workoutPrefs, sourceNames, bmr)

    /**
     * Today's "ready to share" card, as on Android: the newest run, or long enough walk, from today
     * or yesterday, unless it was already shared or dismissed.
     */
    suspend fun shareOffer(share: ShareCardPrefs): WorkoutView? {
        val profile = log.getProfile() ?: return null
        if (!workoutPrefs.enabled) return null
        return ShareOffer.pick(
            workouts = importedViews(PhysiologyEngine.calculateBmr(profile)),
            today = Now.today(),
            runsOn = share.offerRuns,
            walkMinMinutes = share.offerWalkMinutes,
            handledIds = share.handledOffers
        )
    }

    /** Voice-logged workouts plus the ones from other apps and the watch, for the coach (as on Android). */
    private suspend fun coachWorkouts(state: DashboardUiState.Success): List<WorkoutEntryEntity> =
        log.getAllWorkoutEntries() + workoutViews(log.importedWorkouts.getAll(), state.profile).map(WorkoutSync::coachEntry)

    /** What the coach chat's instructions are written from, read fresh for each question; null until today has loaded. */
    @OptIn(ExperimentalTime::class)
    suspend fun coachChatFacts(notes: List<CoachNote>, conversation: List<ChatEntry>): CoachChatFacts? {
        val current = state.value as? DashboardUiState.Success ?: return null
        val now = Now.millis()
        return CoachChatFacts(
            state = current,
            today = today,
            now = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone()).time,
            nowMs = now,
            scaleReadings = scale?.mine().orEmpty(),
            vsUsualLine = vsUsual(current, usualDay.value, distanceKm.value).chatLine(),
            allNutrition = log.getAllNutritionEntries(),
            workouts = coachWorkouts(current),
            activity = log.getAllActivityTelemetry(),
            sleep = log.getAllSleepTelemetry(),
            weights = log.getAllWeightEntries(),
            stepGoal = stepGoal.value?.goal,
            customMacros = prefs.getBoolean("custom_macro_enabled", false),
            targetWeightKg = current.profile.targetWeightKg.takeIf { GoalDatePref.isGoalSet(prefs) },
            goalDate = GoalDatePref.get(prefs),
            notes = notes,
            todayTip = (_coaching.value as? CoachingInsightState.Success)?.insight ?: CoachInsightStore.loadForDay(prefs, today),
            conversation = conversation
        )
    }

    /** Ready-made questions for the coach chat, picked for the time of day and what's logged, as on Android. */
    @OptIn(ExperimentalTime::class)
    fun coachQuestions(): List<String> {
        val current = state.value as? DashboardUiState.Success
        val now = Instant.fromEpochMilliseconds(Now.millis()).toLocalDateTime(zone())
        return CoachQuestions.forNow(
            hour = now.hour,
            day = now.dayOfWeek,
            mealsToday = current?.nutritionList?.size ?: 0,
            proteinStillNeeded = current?.let { (it.macroTargets.proteinG - it.proteinEaten).toInt() } ?: 0,
            workedOutToday = current != null && (current.workoutList.isNotEmpty() || current.importedWorkouts.isNotEmpty()),
            hasTodayTip = _coaching.value is CoachingInsightState.Success
        ).let { if (_coachStyle.value == CoachStyle.ROAST) listOf(CoachChat.ROAST_MY_DAY) + it else it }
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
        UsageEvents.coachStyleChanged(style.name)
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
            runCatching { log.getMealSuggester(Now.hour()) }.onSuccess { _mealSuggester.value = it }
        }
    }

    /**
     * Works out a typed meal with the AI and logs it, the same way as Android. [countAs] is how it
     * shows in usage reports (null when the caller counts it itself, like the coach's "Log this").
     */
    fun logMeal(text: String, countAs: String? = "typed") {
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
                    countAs?.let(UsageEvents::mealLogged)
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
            UsageEvents.mealLogged("voice")
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
            UsageEvents.workoutLogged()
        }
    )

    fun smartLog(input: String, otherVersions: List<String> = emptyList(), kind: LogKind = LogKind.ANY) {
        if (input.isBlank()) return
        scope.launch {
            _parsing.value = ParsingState.Loading
            _parsing.value = smartLogger.log(input, otherVersions, kind)
        }
    }

    /**
     * Saves a meal from the camera once the person checked it, as Android does: packets (and
     * meals whose food was changed) get their quality tags afterwards.
     */
    fun addPhotoMeal(entry: NutritionEntryEntity, isPackaged: Boolean, method: String = "photo") {
        UsageEvents.mealLogged(method)
        scope.launch {
            try {
                val id = log.addNutritionEntry(entry)
                loadPredictedFoods()
                _parsing.value = ParsingState.Success(entry.foodText)
                if (isPackaged || entry.qualityTags == null) {
                    val saved = entry.copy(id = id)
                    MealTagger().tagOne(saved)?.let { log.saveMealTags(saved, it.encode()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("TodayModel", "Saving photo meal failed", e)
                _parsing.value = ParsingState.Error("Couldn't save this meal. Please try again.")
            }
        }
    }

    /** The result has been shown (in the banner), so the box goes back to its usual look. */
    fun resetParsing() {
        _parsing.value = ParsingState.Idle
    }

    fun retryLastMeal() {
        lastMeal?.let { logMeal(it) }
    }

    fun updateMeal(entry: NutritionEntryEntity) {
        UsageEvents.mealEdited()
        scope.launch { log.updateNutritionEntry(entry) }
    }

    fun deleteMeal(entry: NutritionEntryEntity) {
        scope.launch { log.deleteNutritionEntry(entry) }
    }

    fun deleteWorkout(entry: WorkoutEntryEntity) {
        scope.launch { log.deleteWorkoutEntry(entry) }
    }

    fun logWeight(weightKg: Double, note: String) {
        UsageEvents.weighIn()
        scope.launch { log.addWeightEntry(weightKg, note) }
    }

    private companion object {
        // The same day stamp Android keeps, bumped with the nutritionist ratings.
        const val BACKFILL_KEY = "meal_tag_backfill_date_v3"
    }
}
