package com.superfit.app.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.DataRepository
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.domain.PhysiologyEngine
import kotlinx.coroutines.launch
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class DayStatus {
    Deficit,
    Surplus,
    Insufficient
}

data class HistoryDayState(
    val date: LocalDate,
    val caloriesEaten: Double,
    val tdee: Double,
    val mealsLogged: Int,
    val status: DayStatus
)

data class DaySummaryState(
    val date: LocalDate,
    val caloriesEaten: Double,
    val tdee: Double,
    val activeBurn: Double,
    val steps: Int,
    val netBalance: Double,
    val meals: List<NutritionEntryEntity>
)

data class WeeklyTrendsState(
    val avgNetCalories: Double,
    val avgSteps: Double,
    val avgSleepDurationSeconds: Long,
    val avgSleepReadiness: Int,
    val avgProteinG: Double,
    val trackedDaysCount: Int
)

sealed interface HistoryParsingState {
    object Idle : HistoryParsingState
    object Loading : HistoryParsingState
    data class Success(val foodText: String) : HistoryParsingState
    data class Error(val message: String) : HistoryParsingState
}

class HistoryViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("superfit_prefs", Context.MODE_PRIVATE)

    private val _parsingState = MutableStateFlow<HistoryParsingState>(HistoryParsingState.Idle)
    val parsingState: StateFlow<HistoryParsingState> = _parsingState.asStateFlow()

    var currentMonth by mutableStateOf(YearMonth.now())
        private set

    var daysInMonth by mutableStateOf<List<HistoryDayState>>(emptyList())
        private set

    var consistencyScore by mutableStateOf(0)
        private set

    var deficitDaysCount by mutableStateOf(0)
        private set

    var loggedDaysCount by mutableStateOf(0)
        private set

    var selectedDate by mutableStateOf<LocalDate>(LocalDate.now().minusDays(1))
        private set

    var selectedSummary by mutableStateOf<DaySummaryState?>(null)
        private set

    var weeklyTrends by mutableStateOf<WeeklyTrendsState?>(null)
        private set

    private var nutritionByDate: Map<LocalDate, List<NutritionEntryEntity>> = emptyMap()
    private var activityByDate: Map<LocalDate, ActivityTelemetryEntity> = emptyMap()
    private var bmr: Double = 0.0
    private var activityMultiplier: Double = 1.2

    init {
        loadData()
    }

    fun nextMonth() {
        currentMonth = currentMonth.plusMonths(1)
        loadData()
    }

    fun prevMonth() {
        currentMonth = currentMonth.minusMonths(1)
        loadData()
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        updateSelectedSummary()
    }

    private fun updateSelectedSummary() {
        val meals = nutritionByDate[selectedDate] ?: emptyList()
        val activity = activityByDate[selectedDate]
        val activeCal = activity?.activeCalories ?: 0.0
        val tdee = PhysiologyEngine.calculateTdee(bmr, activityMultiplier, activeCal)
        val eaten = meals.sumOf { it.calories }

        selectedSummary = DaySummaryState(
            date = selectedDate,
            caloriesEaten = eaten,
            tdee = tdee,
            activeBurn = activeCal,
            steps = activity?.steps ?: 0,
            netBalance = eaten - tdee,
            meals = meals
        )
    }

    fun loadData() {
        viewModelScope.launch {
            val profile = repository.getProfile() ?: return@launch
            bmr = PhysiologyEngine.calculateBmr(profile)
            activityMultiplier = profile.activityMultiplier

            // Get all local entries to compute month grid and consistency
            val allNutrition = repository.getAllNutritionEntries()
            val allActivity = repository.getAllActivityTelemetry()
            val allSleep = repository.getAllSleepTelemetry()

            nutritionByDate = allNutrition.groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            activityByDate = allActivity.associateBy {
                try {
                    LocalDate.parse(it.date)
                } catch (e: Exception) {
                    LocalDate.now()
                }
            }
            val sleepByDate = allSleep.associateBy {
                try {
                    LocalDate.parse(it.date)
                } catch (e: Exception) {
                    LocalDate.now()
                }
            }

            // Calculate current month days
            val firstDayOfMonth = currentMonth.atDay(1)
            val lastDayOfMonth = currentMonth.atEndOfMonth()
            val daysList = mutableListOf<HistoryDayState>()

            var tempDate = firstDayOfMonth
            while (!tempDate.isAfter(lastDayOfMonth)) {
                val meals = nutritionByDate[tempDate] ?: emptyList()
                val mealsCount = meals.size
                val caloriesEaten = meals.sumOf { it.calories }

                val activity = activityByDate[tempDate]
                val activeCal = activity?.activeCalories ?: 0.0
                val tdee = PhysiologyEngine.calculateTdee(bmr, activityMultiplier, activeCal)

                val status = when {
                    mealsCount < 2 -> DayStatus.Insufficient
                    caloriesEaten < tdee -> DayStatus.Deficit
                    else -> DayStatus.Surplus
                }

                daysList.add(
                    HistoryDayState(
                        date = tempDate,
                        caloriesEaten = caloriesEaten,
                        tdee = tdee,
                        mealsLogged = mealsCount,
                        status = status
                    )
                )
                tempDate = tempDate.plusDays(1)
            }
            daysInMonth = daysList

            // Calculate rolling 30-day consistency score (from today backwards)
            val today = LocalDate.now()
            var rollingDeficitCount = 0
            var rollingLoggedCount = 0

            for (i in 0 until 30) {
                val checkDate = today.minusDays(i.toLong())
                val meals = nutritionByDate[checkDate] ?: emptyList()
                val mealsCount = meals.size
                if (mealsCount >= 2) {
                    rollingLoggedCount++
                    val caloriesEaten = meals.sumOf { it.calories }
                    val activity = activityByDate[checkDate]
                    val activeCal = activity?.activeCalories ?: 0.0
                    val tdee = PhysiologyEngine.calculateTdee(bmr, activityMultiplier, activeCal)
                    if (caloriesEaten < tdee) {
                        rollingDeficitCount++
                    }
                }
            }

            deficitDaysCount = rollingDeficitCount
            loggedDaysCount = rollingLoggedCount
            consistencyScore = if (rollingLoggedCount > 0) {
                ((rollingDeficitCount.toDouble() / rollingLoggedCount) * 100).toInt()
            } else {
                0
            }

            // Calculate rolling 7-day weekly trends (from today backwards)
            var totalNetCalories = 0.0
            var totalSteps = 0.0
            var totalSleepDurationSeconds = 0L
            var totalSleepReadiness = 0
            var sleepDaysCount = 0
            var totalProteinG = 0.0
            var trackedDaysCount = 0
            var activeDaysCount = 0

            for (i in 0 until 7) {
                val checkDate = today.minusDays(i.toLong())
                val meals = nutritionByDate[checkDate] ?: emptyList()
                val mealsCount = meals.size

                if (mealsCount >= 2) {
                    val eatenCalories = meals.sumOf { it.calories }
                    val activity = activityByDate[checkDate]
                    val activeCal = activity?.activeCalories ?: 0.0
                    val tdee = PhysiologyEngine.calculateTdee(bmr, activityMultiplier, activeCal)
                    totalNetCalories += (eatenCalories - tdee)
                    totalProteinG += meals.sumOf { it.proteinG }
                    trackedDaysCount++
                }

                val activity = activityByDate[checkDate]
                if (activity != null) {
                    totalSteps += activity.steps
                    activeDaysCount++
                }

                val sleep = sleepByDate[checkDate]
                if (sleep != null) {
                    totalSleepDurationSeconds += sleep.sleepDurationSeconds
                    totalSleepReadiness += sleep.readinessScore
                    sleepDaysCount++
                }
            }

            weeklyTrends = WeeklyTrendsState(
                avgNetCalories = if (trackedDaysCount > 0) totalNetCalories / trackedDaysCount else 0.0,
                avgSteps = totalSteps / 7.0,
                avgSleepDurationSeconds = if (sleepDaysCount > 0) totalSleepDurationSeconds / sleepDaysCount else 0L,
                avgSleepReadiness = if (sleepDaysCount > 0) totalSleepReadiness / sleepDaysCount else 0,
                avgProteinG = if (trackedDaysCount > 0) totalProteinG / trackedDaysCount else 0.0,
                trackedDaysCount = trackedDaysCount
            )

            updateSelectedSummary()
        }
    }

    fun resetParsingState() {
        _parsingState.value = HistoryParsingState.Idle
    }

    fun deleteMeal(entry: NutritionEntryEntity) {
        viewModelScope.launch {
            repository.deleteNutritionEntry(entry)
            loadData()
        }
    }

    fun parseAndAddMeal(input: String) {
        val key = sharedPrefs.getString("gemini_api_key", "AIzaSyBO5mX4dLLtuZHoYHlZyT2W9CLoaMLYYLM") ?: "AIzaSyBO5mX4dLLtuZHoYHlZyT2W9CLoaMLYYLM"
        if (key.isBlank()) {
            _parsingState.value = HistoryParsingState.Error("Please enter your Gemini API Key in Settings first.")
            return
        }

        viewModelScope.launch {
            _parsingState.value = HistoryParsingState.Loading
            try {
                val parser = com.superfit.app.domain.NutritionParser(key)
                val result = parser.parseFoodInput(input)

                if (result.foodText == "invalid") {
                    _parsingState.value = HistoryParsingState.Error("Could not recognize any food items. Please try typing something like 'one apple and greek yogurt'.")
                    return@launch
                }

                // Construct a timestamp at exactly 12:00 PM of the selected date to avoid timezone boundaries
                val targetTimestamp = selectedDate.atTime(12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val entry = NutritionEntryEntity(
                    foodText = result.foodText,
                    calories = result.calories,
                    proteinG = result.proteinG,
                    carbsG = result.carbsG,
                    fatG = result.fatG,
                    timestamp = targetTimestamp
                )
                repository.addNutritionEntry(entry)
                loadData()
                _parsingState.value = HistoryParsingState.Success(result.foodText)
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
                _parsingState.value = HistoryParsingState.Error(friendlyMsg)
            }
        }
    }
}
