package com.superfit.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.DataRepository
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.MacroTargets
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

    private val sharedPrefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "AIzaSyBO5mX4dLLtuZHoYHlZyT2W9CLoaMLYYLM") ?: "AIzaSyBO5mX4dLLtuZHoYHlZyT2W9CLoaMLYYLM")
    val apiKey: StateFlow<String> = _apiKey

    private val _parsingState = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsingState: StateFlow<ParsingState> = _parsingState

    private val _hasHealthConnectPermissions = MutableStateFlow(false)
    val hasHealthConnectPermissions: StateFlow<Boolean> = _hasHealthConnectPermissions

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions

    private val _lastSyncTime = MutableStateFlow(sharedPrefs.getLong("last_telemetry_sync", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _coachingState = MutableStateFlow<CoachingInsightState>(CoachingInsightState.Idle)
    val coachingState: StateFlow<CoachingInsightState> = _coachingState.asStateFlow()

    init {
        checkPermissions()
        loadCachedCoachingInsight()
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
        repository.getNutritionEntriesForDay(LocalDate.now())
    ) { profile, activity, sleep, nutrition ->
        if (profile == null) {
            DashboardUiState.NotInitialized
        } else {
            // Live physiological calculations
            val bmr = PhysiologyEngine.calculateBmr(profile)
            val activeCal = activity?.activeCalories ?: 0.0
            val tdee = PhysiologyEngine.calculateTdee(bmr, profile.activityMultiplier, activeCal)
            val readiness = sleep?.readinessScore ?: 50 // Default 50% if no sleep session found

            // Compute dynamic macro targets based on live TDEE and sleep readiness
            val macroTargets = PhysiologyEngine.calculateMacroTargets(profile, tdee, readiness, activeCal)

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
                bmr = bmr,
                tdee = tdee,
                readinessScore = readiness,
                macroTargets = macroTargets,
                caloriesEaten = caloriesEaten,
                proteinEaten = proteinEaten,
                carbsEaten = carbsEaten,
                fatEaten = fatEaten
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
                
                val entry = NutritionEntryEntity(
                    foodText = result.foodText,
                    calories = result.calories,
                    proteinG = result.proteinG,
                    carbsG = result.carbsG,
                    fatG = result.fatG,
                    timestamp = System.currentTimeMillis()
                )
                repository.addNutritionEntry(entry)
                _parsingState.value = ParsingState.Success
            } catch (e: Exception) {
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
        }
    }

    fun resetParsingState() {
        _parsingState.value = ParsingState.Idle
    }

    fun updateGoal(goal: String, offset: Int) {
        viewModelScope.launch {
            val currentProfile = repository.getProfile() ?: return@launch
            val updated = currentProfile.copy(goal = goal, calorieOffset = offset)
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
}

sealed interface ParsingState {
    object Idle : ParsingState
    object Loading : ParsingState
    object Success : ParsingState
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
        val bmr: Double,
        val tdee: Double,
        val readinessScore: Int,
        val macroTargets: MacroTargets,
        val caloriesEaten: Double,
        val proteinEaten: Double,
        val carbsEaten: Double,
        val fatEaten: Double
    ) : DashboardUiState
}

sealed interface CoachingInsightState {
    object Idle : CoachingInsightState
    object Loading : CoachingInsightState
    data class Success(val insight: String) : CoachingInsightState
    data class Error(val message: String) : CoachingInsightState
}
