package com.superfit.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.*
import com.superfit.app.data.AppAnalytics
import com.superfit.app.data.ReviewPrompt
import com.superfit.app.domain.BestKind
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.CoachPatternEngine
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.FoodQualityEngine
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.LogRouter
import com.superfit.app.domain.MacroTargets
import com.superfit.app.domain.MealTags
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.PersonalBest
import com.superfit.app.domain.PersonalBests
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.RepeatMealCache
import com.superfit.app.domain.StreakEngine
import com.superfit.app.domain.WorkoutAddBack
import com.superfit.app.domain.WorkoutDisplay
import com.superfit.app.domain.WorkoutParser
import com.superfit.app.domain.WorkoutView
import com.superfit.app.widget.SuperfitWidget
import com.superfit.app.widget.WidgetSnapshot
import com.superfit.app.widget.WidgetSnapshotStore
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    val healthConnectManager = repository.healthConnectManager

    private val appContext = context.applicationContext

    // The day the dashboard's flows were built for; a ViewModel kept past midnight still shows that day.
    private val dashboardDate = LocalDate.now().toString()
    private val sharedPrefs = getUserSharedPrefs(context)

    private val _parsingState = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsingState: StateFlow<ParsingState> = _parsingState

    private val _workoutParsingState = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val workoutParsingState: StateFlow<ParsingState> = _workoutParsingState.asStateFlow()

    private val _hasHealthConnectPermissions = MutableStateFlow(false)
    val hasHealthConnectPermissions: StateFlow<Boolean> = _hasHealthConnectPermissions

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions

    private val _lastSyncTime = MutableStateFlow(sharedPrefs.getLong("last_telemetry_sync", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _coachingState = MutableStateFlow<CoachingInsightState>(CoachingInsightState.Idle)
    val coachingState: StateFlow<CoachingInsightState> = _coachingState.asStateFlow()

    /** The coach chat, oldest first. Loaded from the phone when the dashboard opens. */
    private val _chatMessages = MutableStateFlow<List<com.superfit.app.domain.ChatEntry>>(emptyList())
    val chatMessages: StateFlow<List<com.superfit.app.domain.ChatEntry>> = _chatMessages.asStateFlow()
    private var chatLoaded = false
    private var lastChatId = 0L

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    /** What the coach knows about the user, from chats and from Settings. */
    private val _coachNotes = MutableStateFlow(repository.loadCoachNotes())
    val coachNotes: StateFlow<List<com.superfit.app.domain.CoachNote>> = _coachNotes.asStateFlow()

    private val _predictedFoods = MutableStateFlow<List<PredictedFood>>(emptyList())
    val predictedFoods: StateFlow<List<PredictedFood>> = _predictedFoods.asStateFlow()

    private val customMacroTrigger = MutableStateFlow(System.currentTimeMillis())

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("custom_") || key.startsWith(WorkoutImportPrefs.KEY_PREFIX))) {
            customMacroTrigger.value = System.currentTimeMillis()
            // Also catches the switch being restored from the cloud backup.
            if (key.startsWith(WorkoutImportPrefs.KEY_PREFIX)) {
                _workoutImportEnabled.value = repository.workoutImportPrefs.enabled
            }
        }
    }

    private val _workoutImportEnabled = MutableStateFlow(repository.workoutImportPrefs.enabled)
    val workoutImportEnabled: StateFlow<Boolean> = _workoutImportEnabled.asStateFlow()

    // The import is on but Health Connect no longer lets Superfit read workouts (taken away in its
    // settings, or a new phone restored from backup): the Connect card asks again.
    private val _workoutPermissionMissing = MutableStateFlow(false)
    val workoutPermissionMissing: StateFlow<Boolean> = _workoutPermissionMissing.asStateFlow()

    private val _workoutCardDismissed = MutableStateFlow(repository.workoutImportPrefs.cardDismissed)
    val workoutCardDismissed: StateFlow<Boolean> = _workoutCardDismissed.asStateFlow()

    private val _workoutImportRunning = MutableStateFlow(false)
    val workoutImportRunning: StateFlow<Boolean> = _workoutImportRunning.asStateFlow()

    private val _duplicateWorkoutPrompt = MutableStateFlow<DuplicateWorkoutPrompt?>(null)
    val duplicateWorkoutPrompt: StateFlow<DuplicateWorkoutPrompt?> = _duplicateWorkoutPrompt.asStateFlow()

    /** A smart scale reading that might be someone else's, waiting for "Is this you?". */
    private val _pendingScaleReading = MutableStateFlow<com.superfit.app.domain.ScaleReading?>(null)
    val pendingScaleReading: StateFlow<com.superfit.app.domain.ScaleReading?> = _pendingScaleReading.asStateFlow()

    /** The nutritionist's weekly note at the top of Diet Quality; the last one shows until today's is written. */
    private val _dietNote = MutableStateFlow(com.superfit.app.data.NutritionAdviceStore.lastNote(appContext))
    val dietNote: StateFlow<com.superfit.app.domain.DietNote?> = _dietNote.asStateFlow()
    private var dietNoteRunning = false

    init {
        com.superfit.app.data.BodyImporter.load(appContext)
        com.superfit.app.data.NutritionAdviceStore.load(appContext)
        com.superfit.app.data.CoachStyleStore.load(appContext)
        _pendingScaleReading.value = com.superfit.app.data.BodyImporter.nextPending(appContext)
        checkPermissions()
        loadCachedCoachingInsight()
        loadChat()
        loadFrequentMeals()
        tagOlderMeals()
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    /**
     * Meals logged before quality tags existed are judged from their names, which is rough.
     * Once a day, ask the AI to tag the last 4 weeks of them in a few batches. Low-scoring meals
     * tagged before the AI gave reasons and swaps are asked again so they get a swap too.
     */
    private fun tagOlderMeals() {
        val todayStr = LocalDate.now().toString()
        if (sharedPrefs.getString(BACKFILL_KEY, "") == todayStr) return
        viewModelScope.launch {
            try {
                val cutoff = System.currentTimeMillis() - 28L * 24 * 3600 * 1000
                val rerateFrom = System.currentTimeMillis() - RERATE_DAYS * 24 * 3600 * 1000
                val untagged = repository.getAllNutritionEntries()
                    .filter { it.timestamp >= cutoff && it.id != 0L && needsTags(it, rerate = it.timestamp >= rerateFrom) }
                    .sortedByDescending { it.timestamp }
                    .take(60)
                val tagger = com.superfit.app.domain.MealTagger()
                untagged.chunked(20).forEach { batch ->
                    val tags = tagger.tag(batch)
                    batch.forEach { entry -> tags[entry.id]?.let { repository.saveMealTags(entry, it.encode()) } }
                }
                sharedPrefs.edit().putString(BACKFILL_KEY, todayStr).apply()
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Tagging older meals failed; will retry next launch", e)
            }
        }
    }

    /**
     * Untagged meals always; recent meals also when they have no nutritionist rating yet, or were
     * rated before the user's nutritionist's advice last changed.
     */
    private fun needsTags(entry: NutritionEntryEntity, rerate: Boolean): Boolean {
        val tags = MealTags.decode(entry.qualityTags) ?: return true
        if (rerate && (tags.rating == null || tags.verdict == null || tags.adviceVersion != com.superfit.app.domain.PersonalNutrition.adviceVersion())) return true
        return !tags.hasAdvice && FoodQualityEngine.scoreFromTags(tags, isHighProtein = false) <= 5
    }

    fun loadFrequentMeals() {
        viewModelScope.launch {
            _predictedFoods.value = repository.getPredictedFoods()
        }
    }

    private fun loadChat() {
        viewModelScope.launch {
            val saved = repository.loadCoachChat()
            val sentMeanwhile = _chatMessages.value
            _chatMessages.value = com.superfit.app.domain.CoachChat.merge(saved, sentMeanwhile)
            lastChatId = maxOf(lastChatId, _chatMessages.value.maxOfOrNull { it.id } ?: 0L)
            chatLoaded = true
            if (sentMeanwhile.any { !it.isError }) persistChat()
        }
    }

    /** Saves the chat on the phone and backs it up. Waits for the saved chat to load, so it is never overwritten. */
    private fun persistChat() {
        if (!chatLoaded) return
        val entries = _chatMessages.value
        viewModelScope.launch { repository.saveCoachChat(entries) }
    }

    private fun newChatId(): Long = maxOf(System.currentTimeMillis(), lastChatId + 1).also { lastChatId = it }

    private fun updateChatEntry(id: Long, change: (com.superfit.app.domain.ChatEntry) -> com.superfit.app.domain.ChatEntry) {
        _chatMessages.value = _chatMessages.value.map { if (it.id == id) change(it) else it }
        persistChat()
    }

    private fun addChatError(message: String) {
        _chatMessages.value = _chatMessages.value + com.superfit.app.domain.ChatEntry(
            id = newChatId(),
            fromUser = false,
            text = message,
            time = System.currentTimeMillis(),
            isError = true
        )
    }

    /** Clears the conversation on the phone and in the backup. The coach's notes stay. */
    fun clearChatHistory() {
        _chatMessages.value = emptyList()
        viewModelScope.launch { repository.clearCoachChat() }
    }

    /** Ready-made questions for the chat, picked for the time of day and what's logged. */
    fun coachQuestions(): List<String> {
        val state = dashboardState.value as? DashboardUiState.Success
        val now = java.time.LocalDateTime.now()
        return com.superfit.app.domain.CoachQuestions.forNow(
            hour = now.hour,
            day = now.dayOfWeek,
            mealsToday = state?.nutritionList?.size ?: 0,
            proteinStillNeeded = state?.let { (it.macroTargets.proteinG - it.proteinEaten).toInt() } ?: 0,
            workedOutToday = state != null && (state.workoutList.isNotEmpty() || state.importedWorkouts.isNotEmpty()),
            hasTodayTip = _coachingState.value is CoachingInsightState.Success
        ).let { if (_coachStyle.value == com.superfit.app.domain.CoachStyle.ROAST) listOf(ROAST_MY_DAY) + it else it }
    }

    /**
     * Asks the coach [text]. [how] is "typed", "voice" or "chip", for the usage counts. The answer
     * uses the user's own logs, notes and recent conversation; afterwards the coach picks out
     * anything worth remembering.
     */
    fun sendChatMessage(text: String, how: String = "typed") {
        val question = text.trim().take(com.superfit.app.domain.CoachChat.MAX_TEXT)
        if (question.isBlank() || _chatLoading.value) return
        AppAnalytics.coachMessageSent(how)
        val before = _chatMessages.value.filterNot { it.isError }
        _chatMessages.value = _chatMessages.value + com.superfit.app.domain.ChatEntry(
            id = newChatId(),
            fromUser = true,
            text = question,
            time = System.currentTimeMillis(),
            spoken = how == "voice"
        )
        persistChat()

        val currentState = dashboardState.value
        if (currentState !is DashboardUiState.Success) {
            addChatError("Your day is still loading. Try again in a moment.")
            return
        }

        viewModelScope.launch {
            _chatLoading.value = true
            try {
                val history = com.superfit.app.domain.CoachChat.historyForAi(before, System.currentTimeMillis()).map {
                    GeminiClient.ChatTurn(fromUser = it.fromUser, text = it.text)
                }
                val raw = GeminiClient.chat(
                    systemInstructionText = coachChatInstruction(currentState, before),
                    history = history,
                    message = question
                )
                val reply = com.superfit.app.domain.CoachChat.parseReply(raw)
                val coachEntry = com.superfit.app.domain.ChatEntry(
                    id = newChatId(),
                    fromUser = false,
                    text = reply.text.ifBlank { raw.trim() },
                    time = System.currentTimeMillis(),
                    mealToLog = reply.mealToLog
                )
                _chatMessages.value = _chatMessages.value + coachEntry
                persistChat()
                rememberFrom(question, coachEntry)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Chat message failed", e)
                addChatError(GeminiClient.sanitizeError(e))
            } finally {
                _chatLoading.value = false
            }
        }
    }

    /** Picks out anything worth remembering from one exchange and shows it under the reply. */
    private fun rememberFrom(question: String, reply: com.superfit.app.domain.ChatEntry) {
        if (question.length < 12 || question == com.superfit.app.domain.CoachChat.TELL_ME_MORE) return
        viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val notes = _coachNotes.value
                val response = GeminiClient.generateContent(
                    prompt = com.superfit.app.domain.CoachNotes.extractPrompt(notes, today, question, reply.text),
                    systemInstructionText = com.superfit.app.domain.CoachNotes.EXTRACT_INSTRUCTION,
                    isJson = true
                )
                val update = com.superfit.app.domain.CoachNotes.parseUpdate(response, _coachNotes.value, today, System.currentTimeMillis())
                if (update.isEmpty) return@launch
                val current = _coachNotes.value
                saveCoachNotes(com.superfit.app.domain.CoachNotes.apply(current, update))
                update.add.forEach { _ -> AppAnalytics.coachNoteSaved(byUser = false) }
                updateChatEntry(reply.id) {
                    it.copy(
                        rememberedIds = update.add.map { n -> n.id },
                        forgotten = current.filter { n -> n.id in update.outdatedIds }
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Picking out coach notes failed", e)
            }
        }
    }

    private fun saveCoachNotes(notes: List<com.superfit.app.domain.CoachNote>) {
        _coachNotes.value = notes
        viewModelScope.launch { repository.saveCoachNotes(notes) }
    }

    /** Undo on "Remembered: …": takes back the notes that reply saved and brings back any it retired. */
    fun undoChatNotes(entryId: Long) {
        val entry = _chatMessages.value.firstOrNull { it.id == entryId } ?: return
        val current = _coachNotes.value
        val restored = current.filterNot { it.id in entry.rememberedIds } +
            entry.forgotten.filter { old -> current.none { it.id == old.id } }
        saveCoachNotes(restored)
        AppAnalytics.coachNoteRemoved("undo")
        updateChatEntry(entryId) { it.copy(rememberedIds = emptyList(), forgotten = emptyList()) }
    }

    private val _coachStyle = MutableStateFlow(com.superfit.app.domain.CoachStyle.current)
    val coachStyle: StateFlow<com.superfit.app.domain.CoachStyle> = _coachStyle.asStateFlow()

    /** Saves the AI coach style and rewrites today's tip in it, if there is one. */
    fun setCoachStyle(style: com.superfit.app.domain.CoachStyle) {
        if (style == _coachStyle.value) return
        com.superfit.app.data.CoachStyleStore.save(appContext, style)
        _coachStyle.value = style
        AppAnalytics.coachStyleChanged(style.name)
        if (_coachingState.value is CoachingInsightState.Success) refreshCoachingInsight()
    }

    fun deleteCoachNote(id: String) {
        saveCoachNotes(_coachNotes.value.filterNot { it.id == id })
        AppAnalytics.coachNoteRemoved("settings")
    }

    /** Adds what the user typed or pasted in Settings; each line becomes its own note. */
    fun addCoachNote(text: String) {
        val notes = com.superfit.app.domain.CoachNotes.fromUserText(text, LocalDate.now(), System.currentTimeMillis())
        if (notes.isEmpty()) return
        saveCoachNotes(com.superfit.app.domain.CoachNotes.apply(_coachNotes.value, com.superfit.app.domain.NoteUpdate(notes, emptyList())))
        notes.forEach { _ -> AppAnalytics.coachNoteSaved(byUser = true) }
    }

    /** "Log this" under a coach reply: the suggested meal goes through the normal meal log. */
    fun logSuggestedMeal(entryId: Long) {
        val entry = _chatMessages.value.firstOrNull { it.id == entryId } ?: return
        val meal = entry.mealToLog ?: return
        if (entry.mealLogged) return
        parseAndAddMeal(meal)
        AppAnalytics.coachMealLogged()
        updateChatEntry(entryId) { it.copy(mealLogged = true) }
    }

    /** The coach chat's instructions: the user's live numbers, their own logs and notes, and the rules. */
    private suspend fun coachChatInstruction(
        currentState: DashboardUiState.Success,
        conversation: List<com.superfit.app.domain.ChatEntry>
    ): String {
        val profile = currentState.profile
        val activity = currentState.activity
        val sleep = currentState.sleep
        val nutrition = currentState.nutritionList
        val macroTargets = currentState.macroTargets
        val today = LocalDate.now()
        val now = java.time.LocalTime.now()

        val sleepText = if (sleep != null) {
            "Sleep Duration: ${String.format("%.1f", sleep.sleepDurationSeconds / 3600.0)}h, Readiness Score: ${sleep.readinessScore}%"
        } else {
            "No sleep data today."
        }

        val nutritionText = "Calories Eaten: ${currentState.caloriesEaten.toInt()} / ${macroTargets.calories.toInt()} kcal. " +
            "Protein: ${currentState.proteinEaten.toInt()} / ${macroTargets.proteinG.toInt()}g. " +
            "Carbs: ${currentState.carbsEaten.toInt()} / ${macroTargets.carbsG.toInt()}g. " +
            "Fat: ${currentState.fatEaten.toInt()} / ${macroTargets.fatG.toInt()}g. " +
            "Fibre (estimated): ${nutrition.sumOf { MealTags.of(it).fiberG }.toInt()} / ${CoachPatternEngine.fibreTargetG(macroTargets.calories).toInt()}g."

        val activityText = "Steps: ${activity.steps}, Active Calories Burned: ${activity.activeCalories.toInt()} kcal."
        val vsUsualText = todayVsUsual(currentState, usualDay.value).chatLine()

        val body = com.superfit.app.domain.BodyReadings.summary(
            com.superfit.app.data.BodyImporter.mine(appContext),
            profile.weightKg,
            profile.heightCm,
            today
        )
        val bodyText = if (body.hasScaleData) {
            listOfNotNull(
                body.bodyFatPct?.let { "body fat ${com.superfit.app.domain.BodyReadings.fmt(it)}%" + (body.bodyFatChange?.let { c -> " (${if (c > 0) "+" else ""}${com.superfit.app.domain.BodyReadings.fmt(c)} pts over 4 weeks)" } ?: "") },
                body.leanKg?.let { "lean mass ${com.superfit.app.domain.BodyReadings.fmt(it)} kg" },
                body.insight
            ).joinToString(", ")
        } else {
            "no smart scale data"
        }

        val allNutrition = repository.getAllNutritionEntries()
        val coachWorkouts = repository.getAllWorkoutsForCoach(currentState.bmr)
        val report = CoachPatternEngine.analyze(
            today = today,
            now = now,
            weightKg = profile.weightKg,
            goal = profile.goal,
            targets = macroTargets,
            maintenanceCalories = if (sharedPrefs.getBoolean("custom_macro_enabled", false)) null else currentState.tdee,
            nutrition = allNutrition,
            workouts = coachWorkouts,
            activity = repository.getAllActivityTelemetry(),
            sleep = repository.getAllSleepTelemetry(),
            weights = repository.getAllWeightEntries(),
            targetWeightKg = profile.targetWeightKg
        )

        val live = """
            You are Superfit AI Coach, a friendly, knowledgeable personal trainer and nutrition coach who knows this user well.
            Answer the user's questions about their fitness, food, sleep and progress.
            Their numbers today:
            - Profile: Age ${profile.age}, Weight ${profile.weightKg} kg, Height ${profile.heightCm} cm
            - Body (smart scale): $bodyText
            - Goal: ${profile.goal} (Calorie Offset: ${profile.calorieOffset} kcal)
            - Their own nutritionist's advice (follow it, never contradict it): ${com.superfit.app.domain.PersonalNutrition.advice ?: "none given"}
            - Targets: Protein ${macroTargets.proteinG.toInt()}g, Carbs ${macroTargets.carbsG.toInt()}g, Fat ${macroTargets.fatG.toInt()}g, Calories ${macroTargets.calories.toInt()} kcal
            - Today's nutrition: $nutritionText
            - Today's activity: $activityText
            - $vsUsualText
            - Today's sleep: $sleepText
        """.trimIndent()

        val personal = com.superfit.app.domain.CoachChatContext.personal(
            today = today,
            now = now,
            todayMeals = nutrition,
            report = report,
            workouts = coachWorkouts,
            targetWeightKg = profile.targetWeightKg.takeIf { GoalDatePref.isGoalSet(sharedPrefs) },
            goalDate = GoalDatePref.get(sharedPrefs),
            commonFoods = CoachingEngine.commonFoods(allNutrition, today),
            notes = _coachNotes.value,
            todayTip = (_coachingState.value as? CoachingInsightState.Success)?.insight ?: CoachInsightStore.loadForDay(sharedPrefs, today),
            earlierTopics = com.superfit.app.domain.CoachChat.earlierTopics(conversation, System.currentTimeMillis())
        )

        return listOf(live, personal, CoachingEngine.chatContext(report).trim(), com.superfit.app.domain.CoachChatContext.RULES + com.superfit.app.domain.CoachStyle.current.instruction())
            .joinToString("\n\n")
    }

    /** Today's steps and workouts next to the usual for this weekday. */
    fun todayVsUsual(state: DashboardUiState.Success, usual: com.superfit.app.domain.UsualDay?) =
        com.superfit.app.domain.TodayVsUsual.of(
            today = LocalDate.now(),
            steps = state.activity.steps,
            workouts = state.importedWorkouts.map { com.superfit.app.domain.ActivityFact.of(it) } + state.workoutList.map {
                com.superfit.app.domain.ActivityFact(it.description, 0, null, it.caloriesBurned, null, false)
            },
            usual = usual
        )

    private fun loadCachedCoachingInsight() {
        CoachInsightStore.loadForDay(sharedPrefs, LocalDate.now())?.let {
            _coachingState.value = CoachingInsightState.Success(it)
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val client = repository.healthConnectManager.healthConnectClient
            val granted = client?.permissionController?.getGrantedPermissions() ?: emptySet()
            val hasAll = repository.healthConnectManager.hasAllPermissions()
            _hasHealthConnectPermissions.value = hasAll
            _grantedPermissions.value = granted
            _workoutPermissionMissing.value = client != null && repository.healthConnectManager.isAvailable() &&
                repository.workoutImportPrefs.enabled && WorkoutImporter.EXERCISE_PERMISSION !in granted
        }
    }

    // --- Workouts from other apps ---

    val workoutImportPrefs: WorkoutImportPrefs get() = repository.workoutImportPrefs

    /** Called with what the user allowed on Health Connect's screen after tapping Connect. */
    fun onWorkoutPermissionsResult(granted: Set<String>) {
        if (WorkoutImporter.EXERCISE_PERMISSION !in granted) return
        viewModelScope.launch {
            repository.setWorkoutImportEnabled(true)
            _workoutImportEnabled.value = true
            checkPermissions()
            _workoutImportRunning.value = true
            try {
                repository.importWorkouts()
            } finally {
                _workoutImportRunning.value = false
            }
        }
    }

    fun setWorkoutImportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWorkoutImportEnabled(enabled)
            _workoutImportEnabled.value = enabled
            if (enabled) AppAnalytics.workoutsConnected()
        }
    }

    fun dismissWorkoutCard() {
        repository.workoutImportPrefs.cardDismissed = true
        _workoutCardDismissed.value = true
        _workoutPermissionMissing.value = false
    }

    fun hideWorkout(workout: WorkoutView) {
        viewModelScope.launch { repository.hideImportedWorkout(workout.merged.memberIds) }
    }

    fun unhideAllWorkouts() {
        viewModelScope.launch {
            repository.workoutImportPrefs.unhideAll()
            repository.backUpWorkoutImportSettings()
        }
    }

    fun setMainWorkoutApp(packageName: String?) {
        viewModelScope.launch {
            repository.workoutImportPrefs.mainApp = packageName
            repository.backUpWorkoutImportSettings()
        }
    }

    fun setWorkoutAddBack(addBack: WorkoutAddBack) {
        viewModelScope.launch {
            repository.workoutImportPrefs.addBack = addBack
            repository.backUpWorkoutImportSettings()
        }
    }

    /** Apps that have shared workouts, as (package, name), for the "Main workout app" choice. */
    suspend fun workoutSourceApps(): List<Pair<String, String>> =
        repository.getAllImportedWorkouts().map { it.sourcePackage }.distinct()
            .map { it to SourceAppNames.label(appContext, it) }
            .sortedBy { it.second }

    /**
     * After a cardio workout is logged by voice, offers to drop it if another app recorded a
     * workout in the last few hours, so it isn't counted twice. The prompt shows both, so the
     * user can tell whether they're the same, and each imported workout is asked about once.
     */
    private fun offerDuplicateCheck(voiceEntryId: Long, voiceDescription: String, workoutType: String) {
        if (workoutType != "Cardio") return
        val state = dashboardState.value as? DashboardUiState.Success ?: return
        val now = System.currentTimeMillis()
        val prefs = repository.workoutImportPrefs
        val imported = state.importedWorkouts
            .filter { now - (it.startTime + it.merged.durationMillis) in 0..DUPLICATE_WINDOW_MILLIS }
            .filterNot { prefs.wasAskedAboutDuplicate(it.merged.primary.id) }
            .maxByOrNull { it.startTime } ?: return
        prefs.markAskedAboutDuplicate(imported.merged.primary.id)
        _duplicateWorkoutPrompt.value = DuplicateWorkoutPrompt(voiceEntryId, voiceDescription, imported)
    }

    fun keepImportedWorkout() {
        val prompt = _duplicateWorkoutPrompt.value ?: return
        _duplicateWorkoutPrompt.value = null
        viewModelScope.launch {
            repository.getWorkoutEntry(prompt.voiceEntryId)?.let { repository.deleteWorkoutEntry(it) }
        }
    }

    fun keepBothWorkouts() {
        _duplicateWorkoutPrompt.value = null
    }

    // Dynamic state combinations
    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.profileFlow,
        repository.getActivityFlow(LocalDate.now().toString()),
        repository.getSleepFlow(LocalDate.now().toString()),
        repository.getNutritionEntriesForDay(LocalDate.now()),
        repository.getWorkoutEntriesForDay(LocalDate.now()),
        repository.weightEntriesFlow,
        repository.streakStateFlow,
        repository.getHabitEntryFlow(LocalDate.now().toString()),
        customMacroTrigger,
        repository.allNutritionEntriesFlow,
        repository.getImportedWorkoutsForDay(LocalDate.now()),
        repository.importedWorkoutCountFlow
    ) { array ->
        val profile = array[0] as? UserProfileEntity
        val activity = array[1] as? ActivityTelemetryEntity
        val sleep = array[2] as? SleepTelemetryEntity

        @Suppress("UNCHECKED_CAST")
        val nutrition = array[3] as? List<NutritionEntryEntity> ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val workouts = array[4] as? List<WorkoutEntryEntity> ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val weightEntries = array[5] as? List<WeightEntryEntity> ?: emptyList()
        val rawHabit = array[7] as? HabitEntryEntity

        @Suppress("UNCHECKED_CAST")
        val allNutritionHistory = array[9] as? List<NutritionEntryEntity> ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val importedRows = array[10] as? List<ImportedWorkoutEntity> ?: emptyList()
        val importedCount = array[11] as? Int ?: 0

        if (profile == null) {
            DashboardUiState.NotInitialized
        } else {
            val todayStr = LocalDate.now().toString()
            // Live physiological calculations
            val bmr = PhysiologyEngine.calculateBmr(profile)
            val manualCal = workouts.sumOf { it.caloriesBurned }

            // Workouts from other apps. Their calories are already inside Health Connect's
            // "burned today", so they only move the target, never the burned number.
            val workoutPrefs = repository.workoutImportPrefs
            val importedWorkouts = if (workoutPrefs.enabled) {
                ImportedWorkouts.views(appContext, importedRows, workoutPrefs, bmr)
            } else {
                emptyList()
            }
            val dailyTarget = ImportedWorkouts.target(profile, workoutPrefs, importedWorkouts, workouts)
            // What the body burns today with all of today's workouts, for the coach's deficit sums.
            val tdee = dailyTarget.maintenance
            val readiness = sleep?.readinessScore ?: 50 // Default 50% if no sleep session found

            // Macro targets from live TDEE and goal (or the user's own numbers)
            val customMacroEnabled = sharedPrefs.getBoolean("custom_macro_enabled", false)
            val macroTargets = if (customMacroEnabled) {
                val customProtein = sharedPrefs.getInt("custom_protein_g", 130).toDouble()
                val customCarbs = sharedPrefs.getInt("custom_carbs_g", 200).toDouble()
                val customFat = sharedPrefs.getInt("custom_fat_g", 70).toDouble()
                val customCals = sharedPrefs.getInt("custom_calories", 2000).toDouble()
                MacroTargets(
                    calories = customCals,
                    proteinG = customProtein,
                    carbsG = customCarbs,
                    fatG = customFat
                )
            } else {
                dailyTarget.targets
            }
            val workoutTargetKcal = if (customMacroEnabled) 0.0 else dailyTarget.workoutKcal

            // Aggregate nutrition eaten today
            val caloriesEaten = nutrition.sumOf { it.calories }
            val proteinEaten = nutrition.sumOf { it.proteinG }
            val carbsEaten = nutrition.sumOf { it.carbsG }
            val fatEaten = nutrition.sumOf { it.fatG }

            val weightMetrics = com.superfit.app.domain.WeightTrendEngine.calculateMetrics(profile, weightEntries)

            // Streak is derived from the full meal log (one missed day forgiven, two end it)
            val streakState = com.superfit.app.domain.StreakEngine.fromMealTimestamps(
                allNutritionHistory.map { it.timestamp }
            )

            val currentSteps = activity?.steps ?: 0
            val currentSleepSecs = sleep?.sleepDurationSeconds ?: 0L
            val workoutMins = (workouts.size * 25)

            val habitEntry = (rawHabit ?: HabitEntryEntity(date = todayStr)).copy(
                stepsCompleted = currentSteps >= 8000,
                sleepCompleted = currentSleepSecs >= (7 * 3600),
                workoutMins = workoutMins
            )

            // What the AI should weigh when it rates this person's meals
            com.superfit.app.domain.PersonalNutrition.goal = profile.goal
            com.superfit.app.domain.PersonalNutrition.calorieTarget = macroTargets.calories
            com.superfit.app.domain.PersonalNutrition.proteinTargetG = macroTargets.proteinG

            val dietQualityMetrics = com.superfit.app.domain.FoodQualityEngine.analyzeDietQuality(
                nutritionHistory = allNutritionHistory,
                userGoal = profile.goal,
                proteinTargetG = macroTargets.proteinG,
                calorieTarget = macroTargets.calories
            )

            DashboardUiState.Success(
                profile = profile,
                activity = activity ?: ActivityTelemetryEntity(todayStr, 0, 0.0),
                sleep = sleep,
                nutritionList = nutrition,
                workoutList = workouts,
                weightEntries = weightEntries,
                weightMetrics = weightMetrics,
                streakState = streakState,
                mealDaysLogged = ReviewPrompt.daysWithMeals(allNutritionHistory.map { it.timestamp }),
                habitEntry = habitEntry,
                dietQualityMetrics = dietQualityMetrics,
                bmr = bmr,
                tdee = tdee,
                readinessScore = readiness,
                macroTargets = macroTargets,
                caloriesEaten = caloriesEaten,
                proteinEaten = proteinEaten,
                carbsEaten = carbsEaten,
                fatEaten = fatEaten,
                manualWorkoutCalories = manualCal,
                totalMealsLogged = allNutritionHistory.size,
                importedWorkouts = importedWorkouts,
                importedWorkoutCount = if (workoutPrefs.enabled) importedCount else 0,
                workoutTargetKcal = workoutTargetKcal,
                workoutTargetSource = WorkoutDisplay.boostSource(importedWorkouts, workouts.size)
            )
        }
    }.onEach { state ->
        if (state is DashboardUiState.Success) {
            updateWidget(state)
            writeDietNote(state.dietQualityMetrics)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    /**
     * The same weekday's usual activity over the last 4 weeks, for Home's "vs your usual" and the
     * coach. Re-read whenever today's steps or the imported workouts change.
     */
    val usualDay: StateFlow<com.superfit.app.domain.UsualDay?> = combine(
        repository.profileFlow,
        repository.allImportedWorkoutsFlow,
        repository.getActivityFlow(LocalDate.now().toString())
    ) { profile, _, _ ->
        if (profile == null) return@combine null
        val imported = com.superfit.app.data.ImportedWorkouts.byDate(repository.getImportedWorkoutViews(PhysiologyEngine.calculateBmr(profile)))
        val days = InsightsData.days(
            nutrition = emptyList(),
            voiceWorkouts = repository.getAllWorkoutEntries(),
            activity = repository.getAllActivityTelemetry(),
            imported = imported,
            kcalTarget = { 0.0 },
            proteinTargetG = 0.0
        )
        com.superfit.app.domain.UsualDay.forDay(days, LocalDate.now())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Personal bests from every imported workout and the meal streak, plus the one new best to
     * offer on Home. Workout bests wait for the first import to finish, so it sets the starting
     * points quietly.
     */
    val personalBests: StateFlow<BestsUiState> = combine(
        repository.profileFlow,
        repository.allImportedWorkoutsFlow,
        repository.allNutritionEntriesFlow,
        customMacroTrigger
    ) { profile, rows, meals, _ ->
        if (profile == null) return@combine BestsUiState()
        val prefs = repository.workoutImportPrefs
        val views = if (prefs.enabled && prefs.lastImportMillis > 0) {
            ImportedWorkouts.views(appContext, rows, prefs, PhysiologyEngine.calculateBmr(profile))
        } else {
            emptyList()
        }
        val streak = StreakEngine.fromMealTimestamps(meals.map { it.timestamp })
        val bests = PersonalBests.compute(views, streak.longestStreak)
        val baseline = prefs.bestBaseline
        val riding = prefs.bestStreakRiding
        val check = PersonalBests.check(bests, baseline, riding, streak.currentStreak, System.currentTimeMillis())
        if (check.baseline != baseline || check.streakRiding != riding) {
            prefs.bestBaseline = check.baseline
            prefs.bestStreakRiding = check.streakRiding
            viewModelScope.launch { runCatching { repository.backUpWorkoutImportSettings() } }
        }
        BestsUiState(all = bests, toOffer = check.announce.firstOrNull())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BestsUiState()
    )

    /** A best was shared or dismissed: remember it so it's offered only once. */
    fun acknowledgeBest(best: PersonalBest) {
        val prefs = repository.workoutImportPrefs
        prefs.bestBaseline = PersonalBests.acknowledge(best, prefs.bestBaseline)
        if (best.kind == BestKind.LONGEST_STREAK) prefs.bestStreakRiding = true
        viewModelScope.launch { runCatching { repository.backUpWorkoutImportSettings() } }
    }

    /** Keeps the home-screen widget's "kcal left" in step with the dashboard. */
    private fun updateWidget(state: DashboardUiState.Success) {
        // Same rounding as the Home ring's "left", so the two never differ by 1 kcal.
        val target = state.macroTargets.calories.toInt()
        val left = (state.macroTargets.calories - state.caloriesEaten).toInt()
        val snapshot = WidgetSnapshot(date = dashboardDate, targetKcal = target, eatenKcal = target - left)
        if (WidgetSnapshotStore.save(appContext, snapshot)) {
            viewModelScope.launch { SuperfitWidget.refresh(appContext) }
        }
    }

    fun addWater(addMl: Int) {
        viewModelScope.launch {
            repository.addWater(LocalDate.now().toString(), addMl)
        }
    }

    fun toggleCleanEats() {
        viewModelScope.launch {
            repository.toggleCleanEats(LocalDate.now().toString())
        }
    }

    fun logWeight(weightKg: Double, note: String = "") {
        viewModelScope.launch {
            repository.addWeightEntry(weightKg, note)
            AppAnalytics.weighIn()
        }
    }

    fun updateWeightGoal(startingWeightKg: Double, targetWeightKg: Double) {
        viewModelScope.launch {
            val currentProfile = repository.getProfile()
            if (currentProfile != null) {
                repository.saveProfile(
                    currentProfile.copy(
                        startingWeightKg = startingWeightKg,
                        targetWeightKg = targetWeightKg
                    )
                )
            }
            GoalDatePref.markGoalSet(sharedPrefs)
            AppAnalytics.goalSet(withDate = false)
        }
    }

    /** The answer to "Is this you?" for the waiting scale reading. */
    fun answerScaleReading(isMe: Boolean) {
        val reading = _pendingScaleReading.value ?: return
        _pendingScaleReading.value = null
        viewModelScope.launch {
            com.superfit.app.data.BodyImporter.answer(appContext, repository, reading.id, isMe)
            _pendingScaleReading.value = com.superfit.app.data.BodyImporter.nextPending(appContext)
        }
    }

    /** "Not now" on "Is this you?": ask again next time the app opens. */
    fun postponeScaleReading() {
        _pendingScaleReading.value = null
    }

    fun syncTelemetry() {
        viewModelScope.launch {
            repository.syncHealthConnectTelemetry(LocalDate.now())
            _pendingScaleReading.value = com.superfit.app.data.BodyImporter.nextPending(appContext)
            val now = System.currentTimeMillis()
            sharedPrefs.edit().putLong("last_telemetry_sync", now).apply()
            _lastSyncTime.value = now
            checkPermissions()
        }
    }

    // What the user last asked to log, so a failed attempt can be retried without saying it again.
    private var lastMealInput: String? = null
    val canRetryLastMeal: Boolean get() = !lastMealInput.isNullOrBlank()

    // Whether the last input came from the mic (food, exercise or both) or the typed meal box.
    private var lastInputWasSpoken = false

    fun retryLastMeal() {
        lastMealInput?.let { if (lastInputWasSpoken) smartLog(it) else parseAndAddMeal(it) }
    }

    /**
     * Logs whatever was said: food, exercise or both. An exact repeat of one of the user's own
     * meals is logged straight away; anything else is split by [LogRouter] and each part goes to
     * its usual parser. If one part can't be understood, the other is still logged.
     */
    fun smartLog(input: String) {
        lastMealInput = input
        lastInputWasSpoken = true

        viewModelScope.launch {
            _parsingState.value = ParsingState.Loading
            try {
                val history = repository.getAllNutritionEntries()
                RepeatMealCache.findRepeatMeal(input, history)?.let { repeat ->
                    saveMeal(repeat)
                    _parsingState.value = ParsingState.Success(repeat.foodText)
                    return@launch
                }

                val routed = LogRouter().route(input)
                if (routed.isEmpty) {
                    _parsingState.value = ParsingState.Error(NOTHING_RECOGNISED)
                    return@launch
                }

                val (meal, workout) = coroutineScope {
                    val mealJob = async {
                        if (routed.food.isBlank()) {
                            null
                        } else {
                            runCatching { NutritionParser().parseFoodInput(routed.food, history) }
                        }
                    }
                    val workoutJob = async {
                        if (routed.exercise.isBlank()) {
                            null
                        } else {
                            runCatching { WorkoutParser().parseWorkoutInput(routed.exercise) }
                        }
                    }
                    mealJob.await() to workoutJob.await()
                }
                listOfNotNull(meal?.exceptionOrNull(), workout?.exceptionOrNull())
                    .firstOrNull { it is kotlinx.coroutines.CancellationException }
                    ?.let { throw it }

                val mealResult = meal?.getOrNull()?.takeIf { it.foodText != "invalid" }
                val workoutResult = workout?.getOrNull()?.takeIf { it.workoutText != "invalid" }
                mealResult?.let {
                    saveMeal(it)
                    // A retry after this point must not log the meal twice.
                    lastMealInput = routed.exercise.ifBlank { null }
                }
                workoutResult?.let { saveWorkout(it, "JUST_RIGHT") }
                val foodMissed = routed.food.isNotBlank() && mealResult == null
                val exerciseMissed = routed.exercise.isNotBlank() && workoutResult == null

                _parsingState.value = when {
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
                        if (failure != null) android.util.Log.e("DashboardViewModel", "Smart log failed", failure)
                        ParsingState.Error(failure?.let { GeminiClient.sanitizeError(it) } ?: NOTHING_RECOGNISED)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Smart log failed", e)
                _parsingState.value = ParsingState.Error(GeminiClient.sanitizeError(e))
            }
        }
    }

    private suspend fun saveMeal(result: com.superfit.app.domain.ParsedNutritionResult) {
        repository.addNutritionEntry(
            NutritionEntryEntity(
                foodText = result.foodText,
                calories = result.calories,
                proteinG = result.proteinG,
                carbsG = result.carbsG,
                fatG = result.fatG,
                timestamp = System.currentTimeMillis(),
                qualityTags = result.tags?.encode(),
                items = result.items?.encode()
            )
        )
        AppAnalytics.mealLogged(if (lastInputWasSpoken) "voice" else "typed")
        loadFrequentMeals()
    }

    private suspend fun saveWorkout(result: com.superfit.app.domain.ParsedWorkoutResult, difficultyRating: String) {
        val id = repository.addWorkoutEntry(
            WorkoutEntryEntity(
                description = result.workoutText,
                caloriesBurned = result.caloriesBurned,
                workoutType = result.workoutType,
                setsCount = result.setsCount,
                repsCount = result.repsCount,
                timestamp = System.currentTimeMillis(),
                difficultyRating = difficultyRating
            )
        )
        AppAnalytics.workoutLogged()
        offerDuplicateCheck(id, result.workoutText, result.workoutType)
    }

    fun parseAndAddMeal(input: String) {
        lastMealInput = input
        lastInputWasSpoken = false

        viewModelScope.launch {
            _parsingState.value = ParsingState.Loading
            try {
                val history = repository.getAllNutritionEntries()
                val parser = NutritionParser()
                val result = parser.parseFoodInput(input, history)

                if (result.foodText == "invalid") {
                    _parsingState.value = ParsingState.Error("Could not recognize any food items. Please try saying 'one apple and some greek yogurt'.")
                    return@launch
                }

                saveMeal(result)
                _parsingState.value = ParsingState.Success(result.foodText)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Meal parsing failed", e)
                val friendlyMsg = GeminiClient.sanitizeError(e)
                _parsingState.value = ParsingState.Error(friendlyMsg)
            }
        }
    }

    /**
     * Saves a meal the user confirmed in the camera's Check your meal sheet. Photo meals already
     * carry quality tags from the same AI call; packaged foods are tagged straight after saving.
     */
    fun addPhotoMeal(entry: NutritionEntryEntity, isPackaged: Boolean) {
        viewModelScope.launch {
            try {
                val id = repository.addNutritionEntry(entry)
                loadFrequentMeals()
                _parsingState.value = ParsingState.Success(entry.foodText)
                // Packaged foods, and photo meals where the user picked a different food, have no tags yet.
                if (isPackaged || entry.qualityTags == null) {
                    val saved = entry.copy(id = id)
                    com.superfit.app.domain.MealTagger().tagOne(saved)?.let { repository.saveMealTags(saved, it.encode()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Saving photo meal failed", e)
                _parsingState.value = ParsingState.Error("Couldn't save this meal. Please try again.")
            }
        }
    }

    /** Saves a meal the user adjusted in the edit sheet. */
    fun updateMeal(entry: NutritionEntryEntity) {
        viewModelScope.launch {
            repository.updateNutritionEntry(entry)
            AppAnalytics.mealEdited()
            // A changed food drops the old tags (see withItems), so the meal is judged again.
            if (entry.qualityTags == null) {
                com.superfit.app.domain.MealTagger().tagOne(entry)?.let { repository.saveMealTags(entry, it.encode()) }
            }
        }
    }

    fun deleteMeal(entry: NutritionEntryEntity) {
        viewModelScope.launch {
            repository.deleteNutritionEntry(entry)
            loadFrequentMeals()
        }
    }

    fun resetParsingState() {
        _parsingState.value = ParsingState.Idle
    }

    fun parseAndAddWorkout(input: String, difficultyRating: String = "JUST_RIGHT") {
        viewModelScope.launch {
            _workoutParsingState.value = ParsingState.Loading
            try {
                val parser = WorkoutParser()
                val result = parser.parseWorkoutInput(input)

                if (result.workoutText == "invalid") {
                    _workoutParsingState.value = ParsingState.Error("Could not recognize any exercises. Please try saying 'I ran 2 miles' or '3 sets of 10 pushups'.")
                    return@launch
                }

                saveWorkout(result, difficultyRating)
                _workoutParsingState.value = ParsingState.Success(result.workoutText)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Workout parsing failed", e)
                val friendlyMsg = GeminiClient.sanitizeError(e)
                _workoutParsingState.value = ParsingState.Error(friendlyMsg)
            }
        }
    }

    fun deleteWorkout(entry: WorkoutEntryEntity) {
        viewModelScope.launch {
            repository.deleteWorkoutEntry(entry)
        }
    }

    fun resetWorkoutParsingState() {
        _workoutParsingState.value = ParsingState.Idle
    }

    fun updateGoal(goal: String, offset: Int) {
        viewModelScope.launch {
            val currentProfile = repository.getProfile() ?: return@launch
            val updated = currentProfile.copy(goal = goal, calorieOffset = offset)
            repository.saveProfile(updated)
        }
    }

    fun updateActivityMultiplier(multiplier: Double) {
        viewModelScope.launch {
            val currentProfile = repository.getProfile() ?: return@launch
            val updated = currentProfile.copy(activityMultiplier = multiplier)
            repository.saveProfile(updated)
        }
    }

    fun refreshCoachingInsight() {
        val currentState = dashboardState.value
        if (currentState !is DashboardUiState.Success) {
            _coachingState.value = CoachingInsightState.Error("Daily metrics are not loaded yet.")
            return
        }

        viewModelScope.launch {
            _coachingState.value = CoachingInsightState.Loading
            try {
                val customMacroEnabled = sharedPrefs.getBoolean("custom_macro_enabled", false)
                val insight = CoachingEngine().generateDailyInsight(
                    profile = currentState.profile,
                    macroTargets = currentState.macroTargets,
                    maintenanceCalories = if (customMacroEnabled) null else currentState.tdee,
                    historyNutrition = repository.getAllNutritionEntries(),
                    historyWorkouts = repository.getAllWorkoutsForCoach(currentState.bmr),
                    historyActivity = repository.getAllActivityTelemetry(),
                    historySleep = repository.getAllSleepTelemetry(),
                    historyWeights = repository.getAllWeightEntries(),
                    memory = CoachInsightStore.memory(sharedPrefs),
                    notes = _coachNotes.value
                )

                CoachInsightStore.save(sharedPrefs, LocalDate.now(), insight)

                _coachingState.value = CoachingInsightState.Success(insight)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Coaching insight refresh failed", e)
                val friendlyMsg = GeminiClient.sanitizeError(e)
                _coachingState.value = CoachingInsightState.Error(friendlyMsg)
            }
        }
    }

    /** Writes today's nutritionist note once, when the week has enough logs for a score. */
    private fun writeDietNote(metrics: com.superfit.app.domain.DietQualityMetrics) {
        if (!metrics.hasEnoughData || dietNoteRunning) return
        val stamp = LocalDate.now().toString()
        com.superfit.app.data.NutritionAdviceStore.note(appContext, stamp)?.let {
            _dietNote.value = it
            return
        }
        dietNoteRunning = true
        viewModelScope.launch {
            try {
                com.superfit.app.domain.NutritionistNote.write(metrics)?.let { note ->
                    com.superfit.app.data.NutritionAdviceStore.saveNote(appContext, stamp, note)
                    _dietNote.value = note
                }
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Diet Quality note failed; the last one stays", e)
            } finally {
                dietNoteRunning = false
            }
        }
    }

    /** "Fine for me": the food stops counting as a treat and stops getting swaps. */
    fun setFineForMe(key: String, fine: Boolean) {
        com.superfit.app.data.NutritionAdviceStore.setFineForMe(appContext, key, fine)
    }

    /**
     * Saves the user's nutritionist's advice and re-rates recent meals with it. Returns the daily
     * targets the advice names, for the user to confirm, or null when it names none.
     */
    suspend fun saveNutritionistAdvice(text: String): com.superfit.app.domain.AdviceTargets? {
        com.superfit.app.data.NutritionAdviceStore.saveAdvice(appContext, text)
        sharedPrefs.edit().remove(BACKFILL_KEY).apply()
        tagOlderMeals()
        if (text.isBlank()) return null
        return try {
            com.superfit.app.domain.NutritionistNote.readTargets(text)
        } catch (e: Exception) {
            android.util.Log.w("DashboardViewModel", "Couldn't read targets from the advice", e)
            null
        }
    }

    fun applyNutritionistTargets(targets: com.superfit.app.domain.AdviceTargets?) {
        com.superfit.app.data.NutritionAdviceStore.applyTargets(appContext, targets)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    companion object {
        /** First chat chip in Roast me style. */
        const val ROAST_MY_DAY = "Roast"

        /** Bumped with the nutritionist ratings, so recent meals are re-rated straight after the update. */
        private const val BACKFILL_KEY = "meal_tag_backfill_date_v3"
        private const val RERATE_DAYS = 14L
    }
}

private const val NOTHING_RECOGNISED = "Couldn't catch any food or exercise. Try 'two roti and dal' or 'ran 5k'."

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

private const val DUPLICATE_WINDOW_MILLIS = 6L * 3600 * 1000

sealed interface CoachingInsightState {
    object Idle : CoachingInsightState
    object Loading : CoachingInsightState
    data class Success(val insight: CoachInsight) : CoachingInsightState
    data class Error(val message: String) : CoachingInsightState
}
