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

class HistoryViewModel(private val repository: DataRepository) : ViewModel() {

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

            updateSelectedSummary()
        }
    }
}
