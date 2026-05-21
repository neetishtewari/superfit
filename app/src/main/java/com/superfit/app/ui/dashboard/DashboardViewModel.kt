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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "AIzaSyBO5mX4dLLtuZHoYHlZyT2W9CLoaMLYYLM") ?: "AIzaSyBO5mX4dLLtuZHoYHlZyT2W9CLoaMLYYLM")
    val apiKey: StateFlow<String> = _apiKey

    private val _parsingState = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsingState: StateFlow<ParsingState> = _parsingState

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
            val tdee = PhysiologyEngine.calculateTdee(bmr, activeCal)
            val readiness = sleep?.readinessScore ?: 50 // Default 50% if no sleep session found

            // Compute dynamic macro targets based on live TDEE and sleep readiness
            val macroTargets = PhysiologyEngine.calculateMacroTargets(profile, tdee, readiness)

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
        }
    }

    fun parseAndAddMeal(input: String) {
        val key = _apiKey.value
        if (key.isBlank()) {
            _parsingState.value = ParsingState.Error("Please enter your Gemini API Key first.")
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
                _parsingState.value = ParsingState.Error("Parsing failed: ${e.localizedMessage ?: "Unknown error"}")
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
