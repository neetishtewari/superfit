package com.superfit.app.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.*
import com.superfit.app.domain.PhysiologyEngine
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val meals: List<NutritionEntryEntity>,
    val workouts: List<WorkoutEntryEntity>,
    // Workouts from other apps, without doubles or hidden ones.
    val importedWorkouts: List<com.superfit.app.domain.WorkoutView> = emptyList()
)

data class WeeklyTrendsState(
    val avgNetCalories: Double,
    val avgSteps: Double,
    val avgSleepDurationSeconds: Long,
    val avgSleepReadiness: Int,
    val avgProteinG: Double,
    val trackedDaysCount: Int,
    val totalWorkouts: Int,
    val totalStrengthSets: Int
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

    private val sharedPrefs = getUserSharedPrefs(context)

    private val _parsingState = MutableStateFlow<HistoryParsingState>(HistoryParsingState.Idle)
    val parsingState: StateFlow<HistoryParsingState> = _parsingState.asStateFlow()

    var currentMonth by mutableStateOf(YearMonth.now())
        private set

    var daysInMonth by mutableStateOf<List<HistoryDayState>>(emptyList())
        private set

    var consistencyScore by mutableStateOf(0)
        private set

    var workoutConsistencyScore by mutableStateOf(0)
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

    var predictedFoods by mutableStateOf<List<PredictedFood>>(emptyList())
        private set

    /** The Insights view: last 7 full days, what's working and what to watch. */
    var weekReport by mutableStateOf<com.superfit.app.domain.WeekReport?>(null)
        private set

    /** The Journey view: goal covered and the forecast at current habits. */
    var journey by mutableStateOf<com.superfit.app.domain.JourneyForecast?>(null)
        private set

    var goalTargetDate by mutableStateOf<LocalDate?>(null)
        private set

    /** False until the user saves a goal weight; Journey asks for one instead of forecasting a made-up goal. */
    var goalSet by mutableStateOf(true)
        private set

    private var nutritionByDate: Map<LocalDate, List<NutritionEntryEntity>> = emptyMap()
    private var workoutsByDate: Map<LocalDate, List<WorkoutEntryEntity>> = emptyMap()
    private var activityByDate: Map<LocalDate, ActivityTelemetryEntity> = emptyMap()
    private var importedByDate: Map<LocalDate, List<com.superfit.app.domain.WorkoutView>> = emptyMap()
    private var profile: UserProfileEntity? = null
    private var dayTotals: Map<LocalDate, com.superfit.app.domain.DayTotals> = emptyMap()
    private var allMeals: List<NutritionEntryEntity> = emptyList()

    /** The day's calorie target, including that day's workouts for people who connected them. */
    private fun calculateTargetCalories(date: LocalDate): Double {
        val customMacroEnabled = sharedPrefs.getBoolean("custom_macro_enabled", false)
        val profile = profile
        return if (customMacroEnabled || profile == null) {
            sharedPrefs.getInt("custom_calories", 2000).toDouble()
        } else {
            ImportedWorkouts.target(
                profile,
                repository.workoutImportPrefs,
                importedByDate[date].orEmpty(),
                workoutsByDate[date].orEmpty()
            ).targets.calories
        }
    }

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
        val workouts = workoutsByDate[selectedDate] ?: emptyList()
        val activity = activityByDate[selectedDate]
        val manualCal = workouts.sumOf { it.caloriesBurned }
        val activeCal = (activity?.activeCalories ?: 0.0) + manualCal
        val tdee = calculateTargetCalories(selectedDate)
        val eaten = meals.sumOf { it.calories }

        selectedSummary = DaySummaryState(
            date = selectedDate,
            caloriesEaten = eaten,
            tdee = tdee,
            activeBurn = activeCal,
            steps = activity?.steps ?: 0,
            netBalance = eaten - tdee,
            meals = meals,
            workouts = workouts,
            importedWorkouts = importedByDate[selectedDate].orEmpty()
        )
    }

    fun loadData() {
        viewModelScope.launch {
            val profile = repository.getProfile() ?: return@launch
            this@HistoryViewModel.profile = profile
            importedByDate = ImportedWorkouts.byDate(repository.getImportedWorkoutViews(PhysiologyEngine.calculateBmr(profile)))

            // Get all local entries to compute month grid and consistency
            val allNutrition = repository.getAllNutritionEntries()
            val allWorkouts = repository.getAllWorkoutEntries()
            val allActivity = repository.getAllActivityTelemetry()
            val allSleep = repository.getAllSleepTelemetry()

            nutritionByDate = allNutrition.groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            workoutsByDate = allWorkouts.groupBy {
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

                val tdee = calculateTargetCalories(tempDate)

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
                    val tdee = calculateTargetCalories(checkDate)
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

            // Calculate rolling 30-day training consistency score (from today backwards)
            var rollingWorkoutDaysCount = 0
            for (i in 0 until 30) {
                val checkDate = today.minusDays(i.toLong())
                val workouts = workoutsByDate[checkDate] ?: emptyList()
                if (workouts.isNotEmpty() || !importedByDate[checkDate].isNullOrEmpty()) {
                    rollingWorkoutDaysCount++
                }
            }
            workoutConsistencyScore = ((rollingWorkoutDaysCount.toDouble() / 30) * 100).toInt()

            // Calculate rolling 7-day weekly trends (from today backwards)
            var totalNetCalories = 0.0
            var totalSteps = 0.0
            var totalSleepDurationSeconds = 0L
            var totalSleepReadiness = 0
            var sleepDaysCount = 0
            var totalProteinG = 0.0
            var trackedDaysCount = 0
            var activeDaysCount = 0
            var totalWorkouts = 0
            var totalStrengthSets = 0

            for (i in 0 until 7) {
                val checkDate = today.minusDays(i.toLong())
                val meals = nutritionByDate[checkDate] ?: emptyList()
                val mealsCount = meals.size

                val workouts = workoutsByDate[checkDate] ?: emptyList()
                totalWorkouts += workouts.size + importedByDate[checkDate].orEmpty().size
                totalStrengthSets += workouts.filter { it.workoutType == "Strength" }.sumOf { it.setsCount }

                if (mealsCount >= 2) {
                    val eatenCalories = meals.sumOf { it.calories }
                    val tdee = calculateTargetCalories(checkDate)
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
                trackedDaysCount = trackedDaysCount,
                totalWorkouts = totalWorkouts,
                totalStrengthSets = totalStrengthSets
            )

            predictedFoods = repository.getPredictedFoods()

            val proteinTarget = PhysiologyEngine.proteinPerKg(profile.goal) * PhysiologyEngine.proteinReferenceWeightKg(profile)
            val dayTotals = InsightsData.days(allNutrition, allWorkouts, allActivity, importedByDate, ::calculateTargetCalories, proteinTarget)
            this@HistoryViewModel.dayTotals = dayTotals
            allMeals = allNutrition
            val loseGoal = profile.targetWeightKg < profile.startingWeightKg
            weekReport = com.superfit.app.domain.InsightsEngine.weekReport(dayTotals, today, loseGoal)
            goalTargetDate = GoalDatePref.get(sharedPrefs)
            val oldDefault = profile.startingWeightKg == 75.0 && profile.targetWeightKg == 70.0
            goalSet = (GoalDatePref.isGoalSet(sharedPrefs) || !oldDefault) && abs(profile.startingWeightKg - profile.targetWeightKg) >= 0.5
            journey = com.superfit.app.domain.GoalForecast.compute(
                startKg = profile.startingWeightKg,
                targetKg = profile.targetWeightKg,
                startDate = Instant.ofEpochMilli(profile.startDateTimestamp).atZone(ZoneId.systemDefault()).toLocalDate(),
                weighIns = InsightsData.weighIns(repository.getAllWeightEntries()),
                days = dayTotals,
                calorieOffset = profile.calorieOffset,
                targetDate = goalTargetDate,
                today = today,
                fallbackCurrentKg = profile.weightKg
            )

            updateSelectedSummary()
        }
    }

    /** Builds the coach report for the last [weeks] full weeks and opens the share sheet. */
    fun shareWithCoach(context: Context, weeks: Int) {
        viewModelScope.launch {
            val profile = profile ?: return@launch
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val proteinTarget = PhysiologyEngine.proteinPerKg(profile.goal) * PhysiologyEngine.proteinReferenceWeightKg(profile)
            val yesterday = today.minusDays(1)
            val dietScores = (weeks - 1 downTo 0).map { weeksAgo ->
                com.superfit.app.domain.DietScoreEngine.score(allMeals, yesterday, proteinTarget, calculateTargetCalories(yesterday), zone, weeksAgo)?.score
            }
            val report = com.superfit.app.domain.CoachExportBuilder.build(
                header = com.superfit.app.domain.CoachExportHeader(
                    name = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() },
                    goal = profile.goal,
                    age = profile.age,
                    heightCm = profile.heightCm,
                    isMale = profile.isMale,
                    startKg = profile.startingWeightKg,
                    targetKg = profile.targetWeightKg
                ),
                weeks = weeks,
                today = today,
                days = dayTotals,
                meals = allMeals,
                weighIns = InsightsData.weighIns(repository.getAllWeightEntries()),
                proteinTargetG = proteinTarget,
                dietScores = dietScores,
                zone = zone
            )
            com.superfit.app.ui.share.CoachExportPdf.share(context, report)
            com.superfit.app.data.AppAnalytics.reportShared(weeks)
        }
    }

    fun logWeight(weightKg: Double) {
        viewModelScope.launch {
            repository.addWeightEntry(weightKg)
            com.superfit.app.data.AppAnalytics.weighIn()
            loadData()
        }
    }

    /** Start weight, goal weight and the optional date to reach it by. */
    fun updateGoal(startingWeightKg: Double, targetWeightKg: Double, targetDate: LocalDate?) {
        viewModelScope.launch {
            repository.getProfile()?.let {
                repository.saveProfile(it.copy(startingWeightKg = startingWeightKg, targetWeightKg = targetWeightKg))
            }
            GoalDatePref.set(sharedPrefs, targetDate)
            GoalDatePref.markGoalSet(sharedPrefs)
            com.superfit.app.data.AppAnalytics.goalSet(withDate = targetDate != null)
            loadData()
        }
    }

    fun resetParsingState() {
        _parsingState.value = HistoryParsingState.Idle
    }

    /** Saves a meal the user adjusted in the edit sheet. */
    fun updateMeal(entry: NutritionEntryEntity) {
        viewModelScope.launch {
            repository.updateNutritionEntry(entry)
            com.superfit.app.data.AppAnalytics.mealEdited()
            loadData()
        }
    }

    fun deleteMeal(entry: NutritionEntryEntity) {
        viewModelScope.launch {
            repository.deleteNutritionEntry(entry)
            loadData()
        }
    }

    /** Hides a workout from another app in Superfit; it stays in that app. */
    fun hideImportedWorkout(workout: com.superfit.app.domain.WorkoutView) {
        viewModelScope.launch {
            repository.hideImportedWorkout(workout.merged.memberIds)
            loadData()
        }
    }

    fun deleteWorkout(entry: WorkoutEntryEntity) {
        viewModelScope.launch {
            repository.deleteWorkoutEntry(entry)
            loadData()
        }
    }

    fun parseAndAddMeal(input: String) {
        viewModelScope.launch {
            _parsingState.value = HistoryParsingState.Loading
            try {
                val history = repository.getAllNutritionEntries()
                val parser = com.superfit.app.domain.NutritionParser()
                val result = parser.parseFoodInput(input, history)

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
                    timestamp = targetTimestamp,
                    qualityTags = result.tags?.encode(),
                    items = result.items?.encode()
                )
                repository.addNutritionEntry(entry)
                com.superfit.app.data.AppAnalytics.mealLogged("typed")
                loadData()
                _parsingState.value = HistoryParsingState.Success(result.foodText)
            } catch (e: Exception) {
                val friendlyMsg = com.superfit.app.domain.GeminiClient.sanitizeError(e)
                _parsingState.value = HistoryParsingState.Error(friendlyMsg)
            }
        }
    }
}
