package com.superfit.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.*
import com.superfit.app.data.ReviewPrompt
import com.superfit.app.data.UsageEvents
import com.superfit.app.data.userPrefs
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.DaySteps
import com.superfit.app.domain.FoodQualityEngine
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.LogKind
import com.superfit.app.domain.LogRouter
import com.superfit.app.domain.MacroTargets
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.PersonalBest
import com.superfit.app.domain.PersonalBests
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.StepGoal
import com.superfit.app.domain.StepGoalResult
import com.superfit.app.domain.WorkoutAddBack
import com.superfit.app.domain.WorkoutDisplay
import com.superfit.app.domain.WorkoutParser
import com.superfit.app.domain.WorkoutView
import com.superfit.app.domain.currentAge
import com.superfit.app.widget.SuperfitWidget
import com.superfit.app.widget.WidgetSnapshot
import com.superfit.app.widget.WidgetSnapshotStore
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinDayOfWeek
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime

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
    private val _mealSuggester = MutableStateFlow(com.superfit.app.domain.MealSuggester.EMPTY)
    val mealSuggester: StateFlow<com.superfit.app.domain.MealSuggester> = _mealSuggester.asStateFlow()

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
    private val _dietNote = MutableStateFlow(com.superfit.app.data.NutritionAdviceStore.lastNote(userPrefs(appContext)))
    val dietNote: StateFlow<com.superfit.app.domain.DietNote?> = _dietNote.asStateFlow()
    private var dietNoteRunning = false

    /** Last month's recap while Home offers it (from the 1st until just after it's opened), else null. */
    private val _recapOffer = MutableStateFlow<java.time.YearMonth?>(null)
    val recapOffer: StateFlow<java.time.YearMonth?> = _recapOffer.asStateFlow()

    fun refreshRecapOffer() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val month = com.superfit.app.data.MonthlyRecapData.offeredMonth(today)
            _recapOffer.value = month.takeIf {
                com.superfit.app.data.MonthlyRecapData.showOnHome(appContext, today) &&
                    com.superfit.app.data.MonthlyRecapData.load(appContext, repository, month)?.cards
                        ?.any { it !is com.superfit.app.domain.RecapCard.Setup } == true
            }
        }
    }

    init {
        com.superfit.app.data.BodyImporter.load(appContext)
        com.superfit.app.data.NutritionAdviceStore.load(userPrefs(appContext))
        com.superfit.app.data.CoachStyleStore.load(userPrefs(appContext))
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
                val due = com.superfit.app.domain.MealTagBackfill.due(repository.getAllNutritionEntries(), System.currentTimeMillis())
                com.superfit.app.domain.MealTagBackfill.tag(due, com.superfit.app.domain.MealTagger()) { entry, tags -> repository.saveMealTags(entry, tags) }
                sharedPrefs.edit().putString(BACKFILL_KEY, todayStr).apply()
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Tagging older meals failed; will retry next launch", e)
            }
        }
    }

    fun loadFrequentMeals() {
        viewModelScope.launch {
            _predictedFoods.value = repository.getPredictedFoods()
            runCatching { repository.getMealSuggester() }
                .onSuccess { _mealSuggester.value = it }
                .onFailure { android.util.Log.w("DashboardViewModel", "Meal suggestions failed", it) }
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
            day = now.dayOfWeek.toKotlinDayOfWeek(),
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
        UsageEvents.coachMessageSent(how)
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
                    com.superfit.app.domain.AiChatTurn(fromUser = it.fromUser, text = it.text)
                }
                val raw = com.superfit.app.domain.Ai.chat(
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
                val today = LocalDate.now().toKotlinLocalDate()
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
                update.add.forEach { _ -> UsageEvents.coachNoteSaved(byUser = false) }
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
        UsageEvents.coachNoteRemoved("undo")
        updateChatEntry(entryId) { it.copy(rememberedIds = emptyList(), forgotten = emptyList()) }
    }

    private val _coachStyle = MutableStateFlow(com.superfit.app.domain.CoachStyle.current)
    val coachStyle: StateFlow<com.superfit.app.domain.CoachStyle> = _coachStyle.asStateFlow()

    /** Saves the AI coach style and rewrites today's tip in it, if there is one. */
    fun setCoachStyle(style: com.superfit.app.domain.CoachStyle) {
        // Saved even when unchanged, so the one-time chooser counts "Not now" as an answer.
        com.superfit.app.data.CoachStyleStore.save(userPrefs(appContext), style)
        if (style == _coachStyle.value) return
        _coachStyle.value = style
        UsageEvents.coachStyleChanged(style.name)
        if (_coachingState.value is CoachingInsightState.Success) refreshCoachingInsight()
    }

    fun deleteCoachNote(id: String) {
        saveCoachNotes(_coachNotes.value.filterNot { it.id == id })
        UsageEvents.coachNoteRemoved("settings")
    }

    /** Adds what the user typed or pasted in Settings; each line becomes its own note. */
    fun addCoachNote(text: String) {
        val notes = com.superfit.app.domain.CoachNotes.fromUserText(text, LocalDate.now().toKotlinLocalDate(), System.currentTimeMillis())
        if (notes.isEmpty()) return
        saveCoachNotes(com.superfit.app.domain.CoachNotes.apply(_coachNotes.value, com.superfit.app.domain.NoteUpdate(notes, emptyList())))
        notes.forEach { _ -> UsageEvents.coachNoteSaved(byUser = true) }
    }

    /** "Log this" under a coach reply: the suggested meal goes through the normal meal log. */
    fun logSuggestedMeal(entryId: Long) {
        val entry = _chatMessages.value.firstOrNull { it.id == entryId } ?: return
        val meal = entry.mealToLog ?: return
        if (entry.mealLogged) return
        parseAndAddMeal(meal)
        UsageEvents.coachMealLogged()
        updateChatEntry(entryId) { it.copy(mealLogged = true) }
    }

    /** The coach chat's instructions: the user's live numbers, their own logs and notes, and the rules (shared with the iPhone). */
    private suspend fun coachChatInstruction(
        currentState: DashboardUiState.Success,
        conversation: List<com.superfit.app.domain.ChatEntry>
    ): String {
        val today = LocalDate.now().toKotlinLocalDate()
        return com.superfit.app.ui.dashboard.CoachChatPrompt.instruction(
            com.superfit.app.ui.dashboard.CoachChatFacts(
                state = currentState,
                today = today,
                now = java.time.LocalTime.now().toKotlinLocalTime(),
                nowMs = System.currentTimeMillis(),
                scaleReadings = com.superfit.app.data.BodyImporter.mine(appContext),
                vsUsualLine = todayVsUsual(currentState, usualDay.value).chatLine(),
                allNutrition = repository.getAllNutritionEntries(),
                workouts = repository.getAllWorkoutsForCoach(currentState.bmr),
                activity = repository.getAllActivityTelemetry(),
                sleep = repository.getAllSleepTelemetry(),
                weights = repository.getAllWeightEntries(),
                stepGoal = stepGoal.value?.goal,
                customMacros = sharedPrefs.getBoolean("custom_macro_enabled", false),
                targetWeightKg = currentState.profile.targetWeightKg.takeIf { GoalDatePref.isGoalSet(AndroidPrefs(sharedPrefs)) },
                goalDate = GoalDatePref.get(AndroidPrefs(sharedPrefs)),
                notes = _coachNotes.value,
                todayTip = (_coachingState.value as? CoachingInsightState.Success)?.insight ?: CoachInsightStore.loadForDay(userPrefs(appContext), today),
                conversation = conversation
            )
        )
    }

    /** Today's steps and workouts next to the usual for this weekday. */
    fun todayVsUsual(state: DashboardUiState.Success, usual: com.superfit.app.domain.UsualDay?, distance: TodayDistance? = todayDistance.value) =
        TodayState.vsUsual(state, usual, LocalDate.now().toKotlinLocalDate(), distance?.takeIf { it.date == LocalDate.now() }?.km)

    /** Today's recorded distance, read live from Health Connect and kept only in memory (not stored). */
    data class TodayDistance(val date: LocalDate, val km: Double)

    private val _todayDistance = MutableStateFlow<TodayDistance?>(null)
    val todayDistance: StateFlow<TodayDistance?> = _todayDistance

    private fun loadCachedCoachingInsight() {
        CoachInsightStore.loadForDay(userPrefs(appContext), LocalDate.now().toKotlinLocalDate())?.let {
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
            if (enabled) UsageEvents.workoutsConnected()
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
            // Workouts from other apps, named the way this phone names their apps.
            val workoutPrefs = repository.workoutImportPrefs
            val importedWorkouts = if (workoutPrefs.enabled) {
                ImportedWorkouts.views(appContext, importedRows, workoutPrefs, PhysiologyEngine.calculateBmr(profile))
            } else {
                emptyList()
            }
            TodayState.build(
                today = LocalDate.now().toString(),
                zone = kotlinx.datetime.TimeZone.currentSystemDefault(),
                profile = profile,
                activity = activity,
                sleep = sleep,
                nutrition = nutrition,
                workouts = workouts,
                weightEntries = weightEntries,
                habit = rawHabit,
                allNutritionHistory = allNutritionHistory,
                importedWorkouts = importedWorkouts,
                importedCount = importedCount,
                workoutPrefs = workoutPrefs,
                prefs = userPrefs(appContext)
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
        TodayState.usualDay(
            voiceWorkouts = repository.getAllWorkoutEntries(),
            activity = repository.getAllActivityTelemetry(),
            imported = repository.getImportedWorkoutViews(PhysiologyEngine.calculateBmr(profile)),
            today = LocalDate.now().toKotlinLocalDate(),
            zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Personal bests from every imported workout and daily steps, plus the one new best to offer
     * on Home. Workout bests wait for the first import to finish, so it sets the starting points
     * quietly.
     */
    val personalBests: StateFlow<BestsUiState> = combine(
        repository.profileFlow,
        repository.allImportedWorkoutsFlow,
        repository.allActivityTelemetryFlow,
        customMacroTrigger
    ) { profile, rows, activity, _ ->
        if (profile == null) return@combine BestsUiState()
        val prefs = repository.workoutImportPrefs
        val views = if (prefs.enabled && prefs.lastImportMillis > 0) {
            ImportedWorkouts.views(appContext, rows, prefs, PhysiologyEngine.calculateBmr(profile))
        } else {
            emptyList()
        }
        val steps = activity.mapNotNull { row -> runCatching { DaySteps(kotlinx.datetime.LocalDate.parse(row.date), row.steps) }.getOrNull() }
        val bests = PersonalBests.compute(views, steps, LocalDate.now().toKotlinLocalDate())
        val baseline = prefs.bestBaseline
        val seen = prefs.bestPodiumSeen
        val check = PersonalBests.check(bests, baseline, seen, System.currentTimeMillis())
        if (check.baseline != baseline || check.podiumSeen != seen) {
            prefs.bestBaseline = check.baseline
            prefs.bestPodiumSeen = check.podiumSeen
            viewModelScope.launch { runCatching { repository.backUpWorkoutImportSettings() } }
        }
        BestsUiState(all = bests, toOffer = check.announce.firstOrNull())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BestsUiState()
    )

    private val stepGoalTrigger = MutableStateFlow(0L)

    /**
     * Today's daily step goal, worked out from the last two weeks of steps and the person's age
     * (see StepGoal), or their own number from Settings. Saved so it holds steady all week.
     */
    val stepGoal: StateFlow<StepGoalResult?> = combine(
        repository.profileFlow,
        repository.allActivityTelemetryFlow,
        stepGoalTrigger
    ) { profile, activity, _ ->
        if (profile == null) return@combine null
        TodayCoach.stepGoal(profile, activity, userPrefs(appContext), LocalDate.now().toKotlinLocalDate()) { up ->
            UsageEvents.stepGoalChanged(if (up) "up" else "down", "auto")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _stepGoalNoteDismissedOn = MutableStateFlow(StepGoalStore.noteDismissedOn(userPrefs(appContext)))
    val stepGoalNoteDismissedOn: StateFlow<String?> = _stepGoalNoteDismissedOn.asStateFlow()

    /** Hides today's "New step goal" note on Home. */
    fun dismissStepGoalNote() {
        val today = LocalDate.now().toKotlinLocalDate()
        StepGoalStore.dismissNote(userPrefs(appContext), today)
        _stepGoalNoteDismissedOn.value = today.toString()
    }

    /** Sets the person's own daily step goal, or null to go back to the automatic one. */
    fun setOwnStepGoal(steps: Int?) {
        val clamped = steps?.coerceIn(StepGoal.OWN_MIN, StepGoal.OWN_MAX)
        StepGoalStore.setOwnGoal(userPrefs(appContext), clamped)
        UsageEvents.stepGoalChanged("set", if (clamped == null) "auto" else "own")
        stepGoalTrigger.value = System.currentTimeMillis()
    }

    /** A best was shared or dismissed: remember it so it's offered only once. */
    private val shareOfferTrigger = MutableStateFlow(0)

    /**
     * Today's "ready to share" card: the newest run, or walk long enough, from today or yesterday,
     * unless it was already shared or dismissed. Recalculated when workouts come in.
     */
    val shareOffer: StateFlow<WorkoutView?> = combine(
        repository.profileFlow,
        repository.allImportedWorkoutsFlow,
        shareOfferTrigger
    ) { profile, rows, _ ->
        val prefs = repository.workoutImportPrefs
        if (profile == null || !prefs.enabled) return@combine null
        val views = ImportedWorkouts.views(appContext, rows, prefs, PhysiologyEngine.calculateBmr(profile))
        val sharePrefs = com.superfit.app.data.ShareCardPrefs.of(appContext)
        com.superfit.app.domain.ShareOffer.pick(
            workouts = views,
            today = LocalDate.now().toKotlinLocalDate(),
            runsOn = sharePrefs.offerRuns,
            walkMinMinutes = sharePrefs.offerWalkMinutes,
            handledIds = sharePrefs.handledOffers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /** Shared or dismissed: the card goes and this workout isn't offered again. */
    fun handleShareOffer(workout: WorkoutView, shared: Boolean) {
        com.superfit.app.data.ShareCardPrefs.of(appContext).markOfferHandled(workout.merged.primary.id)
        com.superfit.app.data.UsageEvents.shareOffer(if (shared) "shared" else "dismissed", if (com.superfit.app.domain.ShareOffer.isRun(workout)) "run" else "walk")
        shareOfferTrigger.value++
    }

    /** Settings > Sharing changed which workouts are offered. */
    fun refreshShareOffer() {
        shareOfferTrigger.value++
    }

    fun acknowledgeBest(best: PersonalBest) {
        val prefs = repository.workoutImportPrefs
        prefs.bestBaseline = PersonalBests.acknowledge(best, prefs.bestBaseline)
        prefs.bestPodiumSeen = PersonalBests.acknowledgePodium(best, prefs.bestPodiumSeen)
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
            UsageEvents.weighIn()
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
            GoalDatePref.markGoalSet(AndroidPrefs(sharedPrefs))
            UsageEvents.goalSet(withDate = false)
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
            val today = LocalDate.now()
            repository.syncHealthConnectTelemetry(today)
            // Yesterday too, so a day that was still being counted at midnight gets its final total.
            repository.refreshActivity(today.minusDays(1))
            // Once: days saved before the single-app steps fix could hold two apps added together.
            if (!sharedPrefs.getBoolean(PAST_STEPS_CORRECTED, false)) {
                repository.correctPastSteps(fromDaysAgo = 2, toDaysAgo = 30)
                sharedPrefs.edit().putBoolean(PAST_STEPS_CORRECTED, true).apply()
            }
            _todayDistance.value = repository.healthConnectManager.readDistanceMeters(today)?.let { TodayDistance(today, it / 1000) }
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

    // The phone's other versions of what it heard, kept for a retry.
    private var lastSpokenVersions: List<String> = emptyList()

    /** Which mic the last spoken log came from, so Retry treats it the same way. */
    private var lastKind = LogKind.ANY

    fun retryLastMeal() {
        lastMealInput?.let { if (lastInputWasSpoken) smartLog(it, lastSpokenVersions, lastKind) else parseAndAddMeal(it) }
    }

    /**
     * Logs what was said. From the Food or Activity mic ([kind]) it goes straight to that parser;
     * otherwise it can be food, exercise or both and is split by [LogRouter] first. An exact
     * repeat of one of the user's own meals is logged straight away. If one part can't be
     * understood, the other is still logged.
     */
    fun smartLog(input: String, otherVersions: List<String> = emptyList(), kind: LogKind = LogKind.ANY) {
        lastMealInput = input
        lastInputWasSpoken = true
        lastSpokenVersions = otherVersions
        lastKind = kind

        viewModelScope.launch {
            _parsingState.value = ParsingState.Loading
            _parsingState.value = smartLogger.log(input, otherVersions, kind) { remainingExercise ->
                // A retry after this point must not log the meal twice.
                lastMealInput = remainingExercise.ifBlank { null }
                lastSpokenVersions = emptyList()
                lastKind = LogKind.WORKOUT
            }
        }
    }

    private val smartLogger = SmartLogger(
        history = { repository.getAllNutritionEntries() },
        saveMeal = { saveMeal(it) },
        saveWorkout = { saveWorkout(it, "JUST_RIGHT") }
    )

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
        UsageEvents.mealLogged(if (lastInputWasSpoken) "voice" else "typed")
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
        UsageEvents.workoutLogged()
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
            UsageEvents.mealEdited()
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

    /** Sex, birth month, height and activity level from Settings > About you. */
    fun saveAboutYou(updated: UserProfileEntity) {
        viewModelScope.launch {
            val current = repository.getProfile() ?: return@launch
            // Only the About you fields: a weigh-in or goal saved meanwhile is kept.
            repository.saveProfile(
                current.copy(
                    isMale = updated.isMale,
                    age = updated.age,
                    birthYearMonth = updated.birthYearMonth,
                    heightCm = updated.heightCm,
                    activityMultiplier = updated.activityMultiplier
                )
            )
            UsageEvents.profileEdited()
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
                val insight = TodayCoach.dailyTip(
                    state = currentState,
                    prefs = userPrefs(appContext),
                    nutrition = repository.getAllNutritionEntries(),
                    workouts = repository.getAllWorkoutsForCoach(currentState.bmr),
                    activity = repository.getAllActivityTelemetry(),
                    sleep = repository.getAllSleepTelemetry(),
                    weights = repository.getAllWeightEntries(),
                    stepGoal = stepGoal.value?.goal,
                    notes = _coachNotes.value,
                    today = LocalDate.now().toKotlinLocalDate()
                )

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
        com.superfit.app.data.NutritionAdviceStore.note(userPrefs(appContext), stamp)?.let {
            _dietNote.value = it
            return
        }
        dietNoteRunning = true
        viewModelScope.launch {
            try {
                com.superfit.app.domain.NutritionistNote.write(metrics)?.let { note ->
                    com.superfit.app.data.NutritionAdviceStore.saveNote(userPrefs(appContext), stamp, note)
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
        com.superfit.app.data.NutritionAdviceStore.setFineForMe(userPrefs(appContext), key, fine)
    }

    /**
     * Saves the user's nutritionist's advice and re-rates recent meals with it. Returns the daily
     * targets the advice names, for the user to confirm, or null when it names none.
     */
    suspend fun saveNutritionistAdvice(text: String): com.superfit.app.domain.AdviceTargets? {
        com.superfit.app.data.NutritionAdviceStore.saveAdvice(userPrefs(appContext), text)
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
        com.superfit.app.data.NutritionAdviceStore.applyTargets(userPrefs(appContext), targets)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    companion object {
        private const val PAST_STEPS_CORRECTED = "past_steps_watch_first_v2"

        /** First chat chip in Roast me style. */
        const val ROAST_MY_DAY = com.superfit.app.domain.CoachChat.ROAST_MY_DAY

        /** Bumped with the nutritionist ratings, so recent meals are re-rated straight after the update. */
        private const val BACKFILL_KEY = "meal_tag_backfill_date_v3"
    }
}

private const val DUPLICATE_WINDOW_MILLIS = 6L * 3600 * 1000
