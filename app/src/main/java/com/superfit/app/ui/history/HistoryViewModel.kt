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
import kotlinx.datetime.LocalDate as KDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone

class HistoryViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    private val sharedPrefs = getUserSharedPrefs(context)
    private val appContext = context.applicationContext

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

    var selectedDate by mutableStateOf(LocalDate.now().minusDays(1).toKotlinLocalDate())
        private set

    var selectedSummary by mutableStateOf<DaySummaryState?>(null)
        private set

    var weeklyTrends by mutableStateOf<WeeklyTrendsState?>(null)
        private set

    var predictedFoods by mutableStateOf<List<PredictedFood>>(emptyList())
        private set

    /** The Insights view: last 7 full days, what's working and what to watch. */

    /** Earlier months with enough logged for a recap, newest first (up to 12). */
    var recapMonths by mutableStateOf<List<YearMonth>>(emptyList())
        private set

    var weekReport by mutableStateOf<com.superfit.app.domain.WeekReport?>(null)
        private set

    /** The Journey view: goal covered and the forecast at current habits. */
    var journey by mutableStateOf<com.superfit.app.domain.JourneyForecast?>(null)
        private set

    var goalTargetDate by mutableStateOf<LocalDate?>(null)
        private set

    /** The Body card in Journey: what the user's smart scale measures, plus BMI. */
    var body by mutableStateOf<com.superfit.app.domain.BodySummary?>(null)
        private set

    /** Latest weight and its change over about 4 weeks, for "Your body now" on Insights. */
    var latestWeightKg by mutableStateOf<Double?>(null)
        private set
    var weightChange4w by mutableStateOf<Double?>(null)
        private set

    /** Journey's body trends: period (3 months unless changed) and one series per measure the person has. */
    var bodyPeriod by mutableStateOf(com.superfit.app.domain.BodyPeriod.fromLabel(sharedPrefs.getString(KEY_BODY_PERIOD, null)))
        private set
    var bodySeries by mutableStateOf<List<com.superfit.app.domain.BodySeries>>(emptyList())
        private set

    /** Goal weight minus start weight (0 without a goal): which way weight should move. */
    var goalKgChange by mutableStateOf(0.0)
        private set

    var bodyHasHistory by mutableStateOf(false)
        private set
    private var bodyInputs: (() -> List<com.superfit.app.domain.BodySeries>)? = null

    fun chooseBodyPeriod(period: com.superfit.app.domain.BodyPeriod) {
        bodyPeriod = period
        sharedPrefs.edit().putString(KEY_BODY_PERIOD, period.label).apply()
        bodyInputs?.let { bodySeries = it() }
    }

    /** Health Connect is on the phone but body measurements (body fat, BMR...) aren't allowed yet. */
    var canAskBodyAccess by mutableStateOf(false)
        private set

    /** False until the user saves a goal weight; Journey asks for one instead of forecasting a made-up goal. */
    var goalSet by mutableStateOf(true)
        private set

    private var nutritionByDate: Map<KDate, List<NutritionEntryEntity>> = emptyMap()
    private var workoutsByDate: Map<KDate, List<WorkoutEntryEntity>> = emptyMap()
    private var activityByDate: Map<KDate, ActivityTelemetryEntity> = emptyMap()
    private var importedByDate: Map<KDate, List<com.superfit.app.domain.WorkoutView>> = emptyMap()
    private var profile: UserProfileEntity? = null
    private var dayTotals: Map<kotlinx.datetime.LocalDate, com.superfit.app.domain.DayTotals> = emptyMap()
    private var allMeals: List<NutritionEntryEntity> = emptyList()

    /** The day's calorie target, including that day's workouts for people who connected them. */
    private fun calculateTargetCalories(date: LocalDate): Double = targetOn(date.toKotlinLocalDate())

    private fun targetOn(date: KDate): Double =
        HistoryLog.calorieTarget(profile, userPrefs(appContext), importedByDate[date].orEmpty(), workoutsByDate[date].orEmpty())

    init {
        loadData()
    }

    /** The calendar stops at the current month; there's nothing to show in the future. */
    val canGoToNextMonth: Boolean get() = currentMonth < YearMonth.now()

    fun nextMonth() {
        if (!canGoToNextMonth) return
        currentMonth = currentMonth.plusMonths(1)
        loadData()
    }

    fun prevMonth() {
        currentMonth = currentMonth.minusMonths(1)
        loadData()
    }

    fun selectDate(date: KDate) {
        selectedDate = date
        updateSelectedSummary()
    }

    private fun updateSelectedSummary() {
        selectedSummary = HistoryLog.daySummary(selectedDate, nutritionByDate, workoutsByDate, activityByDate, importedByDate, ::targetOn)
    }

    fun loadData() {
        viewModelScope.launch {
            val profile = repository.getProfile() ?: return@launch
            this@HistoryViewModel.profile = profile
            val zone = TimeZone.currentSystemDefault()
            importedByDate = DayTotalsBuilder.byDate(repository.getImportedWorkoutViews(PhysiologyEngine.calculateBmr(profile)), zone)

            // Get all local entries to compute month grid and consistency
            val allNutrition = repository.getAllNutritionEntries()
            val allWorkouts = repository.getAllWorkoutEntries()
            val allActivity = repository.getAllActivityTelemetry()
            val allSleep = repository.getAllSleepTelemetry()

            nutritionByDate = HistoryLog.byDay(allNutrition, zone) { it.timestamp }
            workoutsByDate = HistoryLog.byDay(allWorkouts, zone) { it.timestamp }
            activityByDate = HistoryLog.activityByDay(allActivity)
            val sleepByDate = HistoryLog.sleepByDay(allSleep)

            daysInMonth = HistoryLog.monthDays(currentMonth.atDay(1).toKotlinLocalDate(), nutritionByDate, ::targetOn)

            val today = LocalDate.now()
            val todayK = today.toKotlinLocalDate()
            val consistency = HistoryLog.consistency(todayK, nutritionByDate, workoutsByDate, importedByDate, ::targetOn)
            deficitDaysCount = consistency.deficitDays
            loggedDaysCount = consistency.loggedDays
            consistencyScore = consistency.score
            workoutConsistencyScore = consistency.trainingScore

            weeklyTrends = HistoryLog.weeklyTrends(todayK, nutritionByDate, workoutsByDate, importedByDate, activityByDate, sleepByDate, ::targetOn)

            predictedFoods = repository.getPredictedFoods()

            val proteinTarget = PhysiologyEngine.proteinTargetG(profile)
            val dayTotals = DayTotalsBuilder.days(allNutrition, allWorkouts, allActivity, importedByDate, ::targetOn, proteinTarget, zone)
            this@HistoryViewModel.dayTotals = dayTotals
            recapMonths = (1..12).map { YearMonth.from(today).minusMonths(it.toLong()) }.filter { m ->
                val inMonth = dayTotals.values.filter { YearMonth.from(it.date.toJavaLocalDate()) == m }
                inMonth.count { it.logged } >= 3 || inMonth.count { it.active } >= 3
            }
            allMeals = allNutrition
            val loseGoal = profile.targetWeightKg < profile.startingWeightKg
            val scaleReadings = com.superfit.app.data.BodyImporter.mine(appContext)
            weekReport = com.superfit.app.domain.InsightsEngine.weekReport(
                dayTotals,
                today.toKotlinLocalDate(),
                loseGoal,
                allNutrition,
                body = com.superfit.app.domain.BodyTrend.fourWeeks(scaleReadings, today.toKotlinLocalDate())
            )
            goalTargetDate = GoalDatePref.get(sharedPrefs)
            val oldDefault = profile.startingWeightKg == 75.0 && profile.targetWeightKg == 70.0
            goalSet = (GoalDatePref.isGoalSet(sharedPrefs) || !oldDefault) && abs(profile.startingWeightKg - profile.targetWeightKg) >= 0.5
            val journeyWeighIns = InsightsData.weighIns(repository.getAllWeightEntries())
            val kJourneyWeighIns = journeyWeighIns.map { it.first.toKotlinLocalDate() to it.second }
            val journeyStart = com.superfit.app.domain.GoalForecast.startDate(
                profileStart = profile.startDateTimestamp.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toKotlinLocalDate() },
                weighIns = kJourneyWeighIns,
                firstLogged = allNutrition.minOfOrNull { it.timestamp }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toKotlinLocalDate() },
                today = today.toKotlinLocalDate()
            )
            journey = com.superfit.app.domain.GoalForecast.compute(
                startKg = profile.startingWeightKg,
                targetKg = profile.targetWeightKg,
                startDate = journeyStart,
                weighIns = kJourneyWeighIns,
                days = dayTotals,
                calorieOffset = profile.calorieOffset,
                targetDate = goalTargetDate?.toKotlinLocalDate(),
                today = today.toKotlinLocalDate(),
                fallbackCurrentKg = profile.weightKg
            )

            body = com.superfit.app.domain.BodyReadings.summary(
                mine = scaleReadings,
                latestWeightKg = repository.getAllWeightEntries().maxByOrNull { it.timestamp }?.weightKg ?: profile.weightKg,
                heightCm = profile.heightCm,
                today = today.toKotlinLocalDate()
            )
            latestWeightKg = kJourneyWeighIns.lastOrNull()?.second ?: profile.weightKg
            weightChange4w = kJourneyWeighIns.lastOrNull()?.let { (lastDay, lastKg) ->
                kJourneyWeighIns.lastOrNull { it.first <= lastDay.minus(28, kotlinx.datetime.DateTimeUnit.DAY) }
                    ?.let { lastKg - it.second }
            }
            goalKgChange = if (goalSet) profile.targetWeightKg - profile.startingWeightKg else 0.0
            val goalChange = goalKgChange
            bodyInputs = {
                com.superfit.app.domain.BodyHistory.series(kJourneyWeighIns, scaleReadings, profile.heightCm, goalChange, bodyPeriod, todayK)
            }
            bodySeries = bodyInputs!!()
            bodyHasHistory = kJourneyWeighIns.size >= 2
            val hc = repository.healthConnectManager
            canAskBodyAccess = hc.isAvailable() && !hc.hasBodyPermissions()

            updateSelectedSummary()
        }
    }

    val bodyPermissions: Set<String> get() = repository.healthConnectManager.bodyPermissions

    /** After the body measurements permission screen: bring in body fat for recent readings too. */
    fun onBodyAccessResult() {
        viewModelScope.launch {
            if (repository.healthConnectManager.hasBodyPermissions()) {
                com.superfit.app.data.BodyImporter.rescanRecent(appContext)
                repository.syncHealthConnectTelemetry(LocalDate.now())
            }
            loadData()
        }
    }

    /** Builds the coach report for the last [weeks] full weeks and opens the share sheet. */
    fun shareWithCoach(context: Context, weeks: Int) {
        viewModelScope.launch {
            val profile = profile ?: return@launch
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val proteinTarget = PhysiologyEngine.proteinTargetG(profile)
            val yesterday = today.minusDays(1)
            val dietScores = (weeks - 1 downTo 0).map { weeksAgo ->
                com.superfit.app.domain.DietScoreEngine.score(allMeals, yesterday.toKotlinLocalDate(), proteinTarget, calculateTargetCalories(yesterday), zone.toKotlinTimeZone(), weeksAgo)?.score
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
                today = today.toKotlinLocalDate(),
                days = dayTotals,
                meals = allMeals,
                weighIns = InsightsData.weighIns(repository.getAllWeightEntries()).map { it.first.toKotlinLocalDate() to it.second },
                proteinTargetG = proteinTarget,
                dietScores = dietScores,
                zone = zone.toKotlinTimeZone(),
                body = body
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
            // A changed food drops the old tags (see withItems), so the meal is judged again.
            if (entry.qualityTags == null) {
                com.superfit.app.domain.MealTagger().tagOne(entry)?.let {
                    repository.saveMealTags(entry, it.encode())
                    loadData()
                }
            }
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

                val entry = HistoryLog.missedMeal(result, selectedDate, TimeZone.currentSystemDefault())
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

    private companion object {
        const val KEY_BODY_PERIOD = "body_trend_period"
    }
}
