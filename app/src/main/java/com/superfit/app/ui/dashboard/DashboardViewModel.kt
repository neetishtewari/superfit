package com.superfit.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.*
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.WorkoutParser
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.MacroTargets
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.CoachPatternEngine
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.MealTags
import com.superfit.app.domain.FoodQualityEngine
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.LogRouter
import com.superfit.app.domain.RepeatMealCache
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
import com.superfit.app.widget.SuperfitWidget
import com.superfit.app.widget.WidgetSnapshot
import com.superfit.app.widget.WidgetSnapshotStore
import java.time.LocalDate

class DashboardViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    val healthConnectManager = repository.healthConnectManager

    private val appContext = context.applicationContext
    // The day the dashboard's flows were built for; a ViewModel kept past midnight still shows that day.
    private val dashboardDate = LocalDate.now().toString()
    private val sharedPrefs = getUserSharedPrefs(context)
    private val geminiKeyStore = GeminiKeyStore(context)

    private val _apiKey = MutableStateFlow(geminiKeyStore.get())
    val apiKey: StateFlow<String> = _apiKey

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

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(MessageSender.Coach, "Hi! I'm your Superfit AI Coach. Ask me anything about your metrics, meal logging, or workout plans!")
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    private val _predictedFoods = MutableStateFlow<List<PredictedFood>>(emptyList())
    val predictedFoods: StateFlow<List<PredictedFood>> = _predictedFoods.asStateFlow()

    private val _customMacroTrigger = MutableStateFlow(System.currentTimeMillis())

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("custom_")) {
            _customMacroTrigger.value = System.currentTimeMillis()
        }
    }

    init {
        checkPermissions()
        loadCachedCoachingInsight()
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
        if (sharedPrefs.getString("meal_tag_backfill_date", "") == todayStr) return
        viewModelScope.launch {
            try {
                val cutoff = System.currentTimeMillis() - 28L * 24 * 3600 * 1000
                val untagged = repository.getAllNutritionEntries()
                    .filter { it.timestamp >= cutoff && it.id != 0L && needsTags(it) }
                    .sortedByDescending { it.timestamp }
                    .take(60)
                val tagger = com.superfit.app.domain.MealTagger(_apiKey.value)
                untagged.chunked(20).forEach { batch ->
                    val tags = tagger.tag(batch)
                    batch.forEach { entry -> tags[entry.id]?.let { repository.saveMealTags(entry, it.encode()) } }
                }
                sharedPrefs.edit().putString("meal_tag_backfill_date", todayStr).apply()
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Tagging older meals failed; will retry next launch", e)
            }
        }
    }

    private fun needsTags(entry: NutritionEntryEntity): Boolean {
        val tags = MealTags.decode(entry.qualityTags) ?: return true
        return !tags.hasAdvice && FoodQualityEngine.scoreFromTags(tags, isHighProtein = false) <= 5
    }

    fun loadFrequentMeals() {
        viewModelScope.launch {
            _predictedFoods.value = repository.getPredictedFoods()
        }
    }


    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(MessageSender.Coach, "Hi! I'm your Superfit AI Coach. Ask me anything about your metrics, meal logging, or workout plans!")
        )
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(MessageSender.User, text)
        _chatMessages.value = _chatMessages.value + userMsg
        
        val key = _apiKey.value

        val currentState = dashboardState.value
        if (currentState !is DashboardUiState.Success) {
            _chatMessages.value = _chatMessages.value + ChatMessage(MessageSender.Coach, "Error: Daily metrics are not loaded yet.")
            return
        }

        viewModelScope.launch {
            _chatLoading.value = true
            try {
                val profile = currentState.profile
                val activity = currentState.activity
                val sleep = currentState.sleep
                val nutrition = currentState.nutritionList
                val macroTargets = currentState.macroTargets
                val caloriesEaten = currentState.caloriesEaten
                val proteinEaten = currentState.proteinEaten
                val carbsEaten = currentState.carbsEaten
                val fatEaten = currentState.fatEaten

                val sleepText = if (sleep != null) {
                    "Sleep Duration: ${String.format("%.1f", sleep.sleepDurationSeconds / 3600.0)}h, Readiness Score: ${sleep.readinessScore}%"
                } else {
                    "No sleep telemetry logged today."
                }
                
                val nutritionText = "Calories Eaten: ${caloriesEaten.toInt()} / ${macroTargets.calories.toInt()} kcal. " +
                    "Protein: ${proteinEaten.toInt()} / ${macroTargets.proteinG.toInt()}g. " +
                    "Carbs: ${carbsEaten.toInt()} / ${macroTargets.carbsG.toInt()}g. " +
                    "Fat: ${fatEaten.toInt()} / ${macroTargets.fatG.toInt()}g. " +
                    "Fibre (estimated): ${nutrition.sumOf { MealTags.of(it).fiberG }.toInt()} / ${CoachPatternEngine.fibreTargetG(macroTargets.calories).toInt()}g."

                val workoutsText = if (currentState.workoutList.isEmpty()) {
                    "No manual workouts logged today."
                } else {
                    currentState.workoutList.joinToString { 
                        if (it.workoutType == "Strength") {
                            "${it.description} (Strength, ${it.setsCount} sets x ${it.repsCount} reps, ~${it.caloriesBurned.toInt()} kcal)"
                        } else {
                            "${it.description} (Cardio, ~${it.caloriesBurned.toInt()} kcal)"
                        }
                    }
                }

                val activityText = "Steps: ${activity.steps}, Active Calories Burned: ${activity.activeCalories.toInt()} kcal."

                val systemInstructionText = """
                    You are Superfit AI Coach, a premium, friendly, highly analytical personal trainer and health scientist.
                    Answer the user's questions about their fitness, diet, sleep, or daily telemetry.
                    Use the user's live daily metrics below for context:
                    - User Profile: Age ${profile.age}, Weight ${profile.weightKg} kg, Height ${profile.heightCm} cm
                    - Goal: ${profile.goal} (Calorie Offset: ${profile.calorieOffset} kcal)
                    - Macro Targets: Protein: ${macroTargets.proteinG.toInt()}g, Carbs: ${macroTargets.carbsG.toInt()}g, Fat: ${macroTargets.fatG.toInt()}g, Calories: ${macroTargets.calories.toInt()} kcal
                    - Daily Nutrition: $nutritionText
                    - Daily Activity: $activityText
                    - Daily Workouts: $workoutsText
                    - Daily Sleep: $sleepText
                    
                    Rules:
                    1. CRITICAL: Distinguish between incomplete logging vs actual low calorie intake. If daily logged calories are low or missing, assume the user did not finish logging their meals, rather than assuming starvation, extreme dieting, or 'feast or famine' behavior.
                    2. Keep answers extremely concise, short, and to the point. Do not write long essays or multiple paragraphs.
                    3. Limit responses to exactly 2 to 3 sentences maximum (under 75 words).
                    4. Do not include unnecessary pleasantries, greetings, or signature sign-offs.
                    5. Use clean markdown formatting (bold, bullet points) sparingly where it adds clarity.
                    6. When the user asks whether a number is good or bad (like "is 50g of protein enough?"), answer for them specifically: compare it with their target and goal using the notes below, and look at the pattern over days rather than one day.
                """.trimIndent() + "\n\n" + CoachingEngine.chatContext(
                    CoachPatternEngine.analyze(
                        today = LocalDate.now(),
                        now = java.time.LocalTime.now(),
                        weightKg = profile.weightKg,
                        goal = profile.goal,
                        targets = macroTargets,
                        maintenanceCalories = if (sharedPrefs.getBoolean("custom_macro_enabled", false)) null else currentState.tdee,
                        nutrition = repository.getAllNutritionEntries(),
                        workouts = repository.getAllWorkoutEntries(),
                        activity = repository.getAllActivityTelemetry(),
                        sleep = repository.getAllSleepTelemetry(),
                        weights = repository.getAllWeightEntries(),
                        targetWeightKg = profile.targetWeightKg
                    )
                )

                val history = _chatMessages.value.dropLast(1).map { msg ->
                    GeminiClient.ChatTurn(fromUser = msg.sender == MessageSender.User, text = msg.text)
                }

                val coachResponseText = GeminiClient.chat(
                    apiKey = key,
                    systemInstructionText = systemInstructionText,
                    history = history,
                    message = text
                )
                _chatMessages.value = _chatMessages.value + ChatMessage(MessageSender.Coach, coachResponseText)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Chat message failed", e)
                val friendlyMsg = GeminiClient.sanitizeError(e)
                _chatMessages.value = _chatMessages.value + ChatMessage(MessageSender.Coach, friendlyMsg)
            } finally {
                _chatLoading.value = false
            }
        }
    }

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
        }
    }

    fun updateApiKey(key: String) {
        val trimmed = key.trim()
        _apiKey.value = trimmed
        geminiKeyStore.set(trimmed)
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
        _customMacroTrigger,
        repository.allNutritionEntriesFlow
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

        if (profile == null) {
            DashboardUiState.NotInitialized
        } else {
            val todayStr = LocalDate.now().toString()
            // Live physiological calculations
            val bmr = PhysiologyEngine.calculateBmr(profile)
            val manualCal = workouts.sumOf { it.caloriesBurned }
            val healthConnectCal = activity?.activeCalories ?: 0.0
            val totalActiveCal = healthConnectCal + manualCal

            val tdee = PhysiologyEngine.calculateTdee(bmr, profile.activityMultiplier, totalActiveCal)
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
                PhysiologyEngine.calculateMacroTargets(profile, tdee, totalActiveCal)
            }

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
                totalMealsLogged = allNutritionHistory.size
            )
        }
    }.onEach { state ->
        if (state is DashboardUiState.Success) updateWidget(state)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

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
        }
    }

    fun syncTelemetry() {
        viewModelScope.launch {
            repository.syncHealthConnectTelemetry(LocalDate.now())
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
        val key = _apiKey.value
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

                val routed = LogRouter(key).route(input)
                if (routed.isEmpty) {
                    _parsingState.value = ParsingState.Error(NOTHING_RECOGNISED)
                    return@launch
                }

                val (meal, workout) = coroutineScope {
                    val mealJob = async {
                        if (routed.food.isBlank()) null
                        else runCatching { NutritionParser(key).parseFoodInput(routed.food, history) }
                    }
                    val workoutJob = async {
                        if (routed.exercise.isBlank()) null
                        else runCatching { WorkoutParser(key).parseWorkoutInput(routed.exercise) }
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
                        message = if (foodMissed) "Logged workout: ${workoutResult.workoutText} · Couldn't log the food. Try saying it again."
                            else "Logged workout: ${workoutResult.workoutText}"
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
        loadFrequentMeals()
    }

    private suspend fun saveWorkout(result: com.superfit.app.domain.ParsedWorkoutResult, difficultyRating: String) {
        repository.addWorkoutEntry(
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
    }

    fun parseAndAddMeal(input: String) {
        val key = _apiKey.value
        lastMealInput = input
        lastInputWasSpoken = false

        viewModelScope.launch {
            _parsingState.value = ParsingState.Loading
            try {
                val history = repository.getAllNutritionEntries()
                val parser = NutritionParser(key)
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
                if (isPackaged) {
                    val saved = entry.copy(id = id)
                    try {
                        com.superfit.app.domain.MealTagger(_apiKey.value).tag(listOf(saved))[id]
                            ?.let { repository.saveMealTags(saved, it.encode()) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The daily backfill tags it later.
                        android.util.Log.w("DashboardViewModel", "Tagging packaged food failed", e)
                    }
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
        val key = _apiKey.value

        viewModelScope.launch {
            _workoutParsingState.value = ParsingState.Loading
            try {
                val parser = WorkoutParser(key)
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
        val key = _apiKey.value

        val currentState = dashboardState.value
        if (currentState !is DashboardUiState.Success) {
            _coachingState.value = CoachingInsightState.Error("Daily metrics are not loaded yet.")
            return
        }

        viewModelScope.launch {
            _coachingState.value = CoachingInsightState.Loading
            try {
                val customMacroEnabled = sharedPrefs.getBoolean("custom_macro_enabled", false)
                val insight = CoachingEngine(key).generateDailyInsight(
                    profile = currentState.profile,
                    macroTargets = currentState.macroTargets,
                    maintenanceCalories = if (customMacroEnabled) null else currentState.tdee,
                    historyNutrition = repository.getAllNutritionEntries(),
                    historyWorkouts = repository.getAllWorkoutEntries(),
                    historyActivity = repository.getAllActivityTelemetry(),
                    historySleep = repository.getAllSleepTelemetry(),
                    historyWeights = repository.getAllWeightEntries(),
                    memory = CoachInsightStore.memory(sharedPrefs)
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

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
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
        val totalMealsLogged: Int = 0
    ) : DashboardUiState
}

sealed interface CoachingInsightState {
    object Idle : CoachingInsightState
    object Loading : CoachingInsightState
    data class Success(val insight: CoachInsight) : CoachingInsightState
    data class Error(val message: String) : CoachingInsightState
}

enum class MessageSender {
    User,
    Coach
}

data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
