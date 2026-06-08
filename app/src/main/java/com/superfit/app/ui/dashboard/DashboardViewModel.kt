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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.superfit.app.domain.CoachingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    val healthConnectManager = repository.healthConnectManager

    private val sharedPrefs = getUserSharedPrefs(context)

    private val _apiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "") ?: "")
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

    private val _frequentMeals = MutableStateFlow<List<String>>(emptyList())
    val frequentMeals: StateFlow<List<String>> = _frequentMeals.asStateFlow()

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
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun loadFrequentMeals() {
        viewModelScope.launch {
            _frequentMeals.value = repository.getFrequentFoodTexts(3)
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
        if (key.isBlank()) {
            _chatMessages.value = _chatMessages.value + ChatMessage(MessageSender.Coach, "Error: Please enter your Gemini API Key in Settings first.")
            return
        }

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
                    "Fat: ${fatEaten.toInt()} / ${macroTargets.fatG.toInt()}g."

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
                    1. CRITICAL: Keep answers extremely concise, short, and to the point. Do not write long essays or multiple paragraphs.
                    2. Limit responses to exactly 2 to 3 sentences maximum (under 75 words).
                    3. Do not include unnecessary pleasantries, greetings, or signature sign-offs.
                    4. Use clean markdown formatting (bold, bullet points) sparingly where it adds clarity.
                """.trimIndent()

                val model = GenerativeModel(
                    modelName = "gemini-3.1-flash-lite",
                    apiKey = key,
                    systemInstruction = content { text(systemInstructionText) }
                )

                val history = _chatMessages.value.dropLast(1).map { msg ->
                    content(role = if (msg.sender == MessageSender.User) "user" else "model") {
                        text(msg.text)
                    }
                }

                val chatSession = model.startChat(history = history)
                val response = chatSession.sendMessage(text)
                val coachResponseText = response.text ?: "I'm not sure, could you please rephrase?"
                _chatMessages.value = _chatMessages.value + ChatMessage(MessageSender.Coach, coachResponseText)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Chat message failed", e)
                val msg = e.localizedMessage ?: ""
                val friendlyMsg = when {
                    msg.contains("429", ignoreCase = true) || msg.contains("quota", ignoreCase = true) || msg.contains("exhausted", ignoreCase = true) -> {
                        "Quota exceeded. Please configure your own free Gemini API Key in Settings."
                    }
                    msg.contains("API key", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) || msg.contains("400", ignoreCase = true) -> {
                        "Invalid API Key. Please update your API key in Settings."
                    }
                    else -> "Error: ${e.localizedMessage ?: "Unknown error"}"
                }
                _chatMessages.value = _chatMessages.value + ChatMessage(MessageSender.Coach, friendlyMsg)
            } finally {
                _chatLoading.value = false
            }
        }
    }

    private fun loadCachedCoachingInsight() {
        val todayStr = LocalDate.now().toString()
        val cachedDate = sharedPrefs.getString("coaching_insight_date", "")
        if (cachedDate == todayStr) {
            val text = sharedPrefs.getString("coaching_insight_text", "")
            if (!text.isNullOrBlank()) {
                _coachingState.value = CoachingInsightState.Success(text)
            }
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
        _apiKey.value = key
        sharedPrefs.edit().putString("gemini_api_key", key).apply()
    }

    // Dynamic state combinations
    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.profileFlow,
        repository.getActivityFlow(LocalDate.now().toString()),
        repository.getSleepFlow(LocalDate.now().toString()),
        repository.getNutritionEntriesForDay(LocalDate.now()),
        repository.getWorkoutEntriesForDay(LocalDate.now()),
        _customMacroTrigger
    ) { array ->
        val profile = array[0] as? UserProfileEntity
        val activity = array[1] as? ActivityTelemetryEntity
        val sleep = array[2] as? SleepTelemetryEntity
        @Suppress("UNCHECKED_CAST")
        val nutrition = array[3] as? List<NutritionEntryEntity> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val workouts = array[4] as? List<WorkoutEntryEntity> ?: emptyList()

        if (profile == null) {
            DashboardUiState.NotInitialized
        } else {
            // Live physiological calculations
            val bmr = PhysiologyEngine.calculateBmr(profile)
            val manualCal = workouts.sumOf { it.caloriesBurned }
            val healthConnectCal = activity?.activeCalories ?: 0.0
            val totalActiveCal = healthConnectCal + manualCal

            val tdee = PhysiologyEngine.calculateTdee(bmr, profile.activityMultiplier, totalActiveCal)
            val readiness = sleep?.readinessScore ?: 50 // Default 50% if no sleep session found

            // Compute dynamic macro targets based on live TDEE and sleep readiness
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
                PhysiologyEngine.calculateMacroTargets(profile, tdee, readiness, totalActiveCal)
            }

            // Aggregate nutrition eaten today
            val caloriesEaten = nutrition.sumOf { it.calories }
            val proteinEaten = nutrition.sumOf { it.proteinG }
            val carbsEaten = nutrition.sumOf { it.carbsG }
            val fatEaten = nutrition.sumOf { it.fatG }

            DashboardUiState.Success(
                profile = profile,
                activity = activity ?: ActivityTelemetryEntity(LocalDate.now().toString(), 0, 0.0),
                sleep = sleep,
                nutritionList = nutrition,
                workoutList = workouts,
                bmr = bmr,
                tdee = tdee,
                readinessScore = readiness,
                macroTargets = macroTargets,
                caloriesEaten = caloriesEaten,
                proteinEaten = proteinEaten,
                carbsEaten = carbsEaten,
                fatEaten = fatEaten,
                manualWorkoutCalories = manualCal
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    fun syncTelemetry() {
        viewModelScope.launch {
            repository.syncHealthConnectTelemetry(LocalDate.now())
            val now = System.currentTimeMillis()
            sharedPrefs.edit().putLong("last_telemetry_sync", now).apply()
            _lastSyncTime.value = now
            checkPermissions()
        }
    }

    fun parseAndAddMeal(input: String) {
        val key = _apiKey.value
        if (key.isBlank()) {
            _parsingState.value = ParsingState.Error("Please enter your Gemini API Key in Settings first.")
            return
        }

        viewModelScope.launch {
            _parsingState.value = ParsingState.Loading
            try {
                val parser = NutritionParser(key)
                val result = parser.parseFoodInput(input)
                
                if (result.foodText == "invalid") {
                    _parsingState.value = ParsingState.Error("Could not recognize any food items. Please try saying 'one apple and some greek yogurt'.")
                    return@launch
                }

                val entry = NutritionEntryEntity(
                    foodText = result.foodText,
                    calories = result.calories,
                    proteinG = result.proteinG,
                    carbsG = result.carbsG,
                    fatG = result.fatG,
                    timestamp = System.currentTimeMillis()
                )
                repository.addNutritionEntry(entry)
                loadFrequentMeals()
                _parsingState.value = ParsingState.Success(result.foodText)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Meal parsing failed", e)
                val msg = e.localizedMessage ?: ""
                val friendlyMsg = when {
                    msg.contains("429", ignoreCase = true) || msg.contains("quota", ignoreCase = true) || msg.contains("exhausted", ignoreCase = true) -> {
                        "Quota exceeded. Please configure your own free Gemini API Key in Settings."
                    }
                    msg.contains("API key", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) || msg.contains("400", ignoreCase = true) -> {
                        "Invalid API Key. Please update your API key in Settings."
                    }
                    else -> "Unable to track meal: ${e.localizedMessage ?: "Unknown error"}"
                }
                _parsingState.value = ParsingState.Error(friendlyMsg)
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

    fun parseAndAddWorkout(input: String) {
        val key = _apiKey.value
        if (key.isBlank()) {
            _workoutParsingState.value = ParsingState.Error("Please enter your Gemini API Key in Settings first.")
            return
        }

        viewModelScope.launch {
            _workoutParsingState.value = ParsingState.Loading
            try {
                val parser = WorkoutParser(key)
                val result = parser.parseWorkoutInput(input)
                
                if (result.workoutText == "invalid") {
                    _workoutParsingState.value = ParsingState.Error("Could not recognize any exercises. Please try saying 'I ran 2 miles' or '3 sets of 10 pushups'.")
                    return@launch
                }

                val entry = WorkoutEntryEntity(
                    description = result.workoutText,
                    caloriesBurned = result.caloriesBurned,
                    workoutType = result.workoutType,
                    setsCount = result.setsCount,
                    repsCount = result.repsCount,
                    timestamp = System.currentTimeMillis()
                )
                repository.addWorkoutEntry(entry)
                _workoutParsingState.value = ParsingState.Success(result.workoutText)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Workout parsing failed", e)
                val msg = e.localizedMessage ?: ""
                val friendlyMsg = when {
                    msg.contains("429", ignoreCase = true) || msg.contains("quota", ignoreCase = true) || msg.contains("exhausted", ignoreCase = true) -> {
                        "Quota exceeded. Please configure your own free Gemini API Key in Settings."
                    }
                    msg.contains("API key", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) || msg.contains("400", ignoreCase = true) -> {
                        "Invalid API Key. Please update your API key in Settings."
                    }
                    else -> "Unable to track workout: ${e.localizedMessage ?: "Unknown error"}"
                }
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
        if (key.isBlank()) {
            _coachingState.value = CoachingInsightState.Error("Please enter your Gemini API Key in Settings first.")
            return
        }

        val currentState = dashboardState.value
        if (currentState !is DashboardUiState.Success) {
            _coachingState.value = CoachingInsightState.Error("Daily metrics are not loaded yet.")
            return
        }

        viewModelScope.launch {
            _coachingState.value = CoachingInsightState.Loading
            try {
                val engine = CoachingEngine(key)
                val insight = engine.generateDailyInsight(
                    profile = currentState.profile,
                    activity = currentState.activity,
                    sleep = currentState.sleep,
                    nutrition = currentState.nutritionList,
                    macroTargets = currentState.macroTargets,
                    caloriesEaten = currentState.caloriesEaten,
                    proteinEaten = currentState.proteinEaten,
                    carbsEaten = currentState.carbsEaten,
                    fatEaten = currentState.fatEaten
                )

                val todayStr = LocalDate.now().toString()
                sharedPrefs.edit()
                    .putString("coaching_insight_date", todayStr)
                    .putString("coaching_insight_text", insight)
                    .apply()

                _coachingState.value = CoachingInsightState.Success(insight)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Coaching insight refresh failed", e)
                val msg = e.localizedMessage ?: ""
                val friendlyMsg = when {
                    msg.contains("429", ignoreCase = true) || msg.contains("quota", ignoreCase = true) || msg.contains("exhausted", ignoreCase = true) -> {
                        "Quota exceeded. Please configure your own free Gemini API Key in Settings."
                    }
                    msg.contains("API key", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) || msg.contains("400", ignoreCase = true) -> {
                        "Invalid API Key. Please update your API key in Settings."
                    }
                    else -> "Failed to load insight: ${e.localizedMessage ?: "Unknown error"}"
                }
                _coachingState.value = CoachingInsightState.Error(friendlyMsg)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }
}

sealed interface ParsingState {
    object Idle : ParsingState
    object Loading : ParsingState
    data class Success(val foodText: String) : ParsingState
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
        val bmr: Double,
        val tdee: Double,
        val readinessScore: Int,
        val macroTargets: MacroTargets,
        val caloriesEaten: Double,
        val proteinEaten: Double,
        val carbsEaten: Double,
        val fatEaten: Double,
        val manualWorkoutCalories: Double
    ) : DashboardUiState
}

sealed interface CoachingInsightState {
    object Idle : CoachingInsightState
    object Loading : CoachingInsightState
    data class Success(val insight: String) : CoachingInsightState
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
