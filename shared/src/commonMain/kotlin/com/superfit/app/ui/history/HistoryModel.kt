package com.superfit.app.ui.history

import com.superfit.app.data.DayTotalsBuilder
import com.superfit.app.data.GoalDatePref
import com.superfit.app.data.HealthSourceNames
import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionAdviceStore
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import com.superfit.app.data.Prefs
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.data.WorkoutSync
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.BodyPeriod
import com.superfit.app.domain.MealTagger
import com.superfit.app.domain.NutritionParser
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.WorkoutView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What the History page shows: the month's days, the chart week and the picked day. */
data class HistoryPage(
    val monthStart: LocalDate,
    val days: List<HistoryDayState>,
    val selected: LocalDate,
    val summary: DaySummaryState,
    val canGoNext: Boolean
)

/** What Insights and Journey show, with the last 30 days' consistency and the week's averages. */
data class ProgressPage(
    val progress: ProgressState,
    val consistency: LogConsistency,
    val trends: WeeklyTrendsState,
    val period: BodyPeriod
)

/**
 * History for the iPhone, worked out by the same [HistoryLog] as Android: a month calendar of
 * logged days and the picked day, where meals can be edited, deleted or logged late. Workouts
 * from other apps join with Apple Health.
 */
class HistoryModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val parser: NutritionParser = NutritionParser(),
    private val tagger: MealTagger = MealTagger(),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    private val today = Now.today()
    private val thisMonth = LocalDate(today.year, today.month, 1)
    private val month = MutableStateFlow(thisMonth)

    // Yesterday, like Android: today is on the Today tab.
    private val selected = MutableStateFlow(today.minus(DatePeriod(days = 1)))

    // Workouts aren't watched by the database here, so a change to them asks for a re-read.
    private val reload = MutableStateFlow(0)

    private val _parsing = MutableStateFlow<HistoryParsingState>(HistoryParsingState.Idle)
    val parsing: StateFlow<HistoryParsingState> = _parsing.asStateFlow()

    private val _predictedFoods = MutableStateFlow<List<PredictedFood>>(emptyList())
    val predictedFoods: StateFlow<List<PredictedFood>> = _predictedFoods.asStateFlow()

    private val workoutPrefs = WorkoutImportPrefs(prefs)

    /** Workouts from other apps (the phone's health app) by day, as Today shows them. */
    private suspend fun importedByDay(profile: UserProfileEntity, tz: TimeZone): Map<LocalDate, List<WorkoutView>> =
        DayTotalsBuilder.byDate(
            WorkoutSync.views(log.importedWorkouts.getAll(), workoutPrefs, HealthSourceNames(prefs), PhysiologyEngine.calculateBmr(profile)),
            tz
        )

    val page: StateFlow<HistoryPage?> = combine(log.profileFlow, log.allNutritionEntriesFlow, month, selected, reload) { profile, meals, monthStart, day, _ ->
        if (profile == null) return@combine null
        val tz = zone()
        val nutrition = HistoryLog.byDay(meals, tz) { it.timestamp }
        val workouts = HistoryLog.byDay(log.getAllWorkoutEntries(), tz) { it.timestamp }
        val activity = HistoryLog.activityByDay(log.getAllActivityTelemetry())
        val imported = importedByDay(profile, tz)
        val target = { date: LocalDate -> HistoryLog.calorieTarget(profile, prefs, imported[date].orEmpty(), workouts[date].orEmpty()) }
        HistoryPage(
            monthStart = monthStart,
            days = HistoryLog.monthDays(monthStart, nutrition, target),
            selected = day,
            summary = HistoryLog.daySummary(day, nutrition, workouts, activity, imported, target),
            canGoNext = monthStart < thisMonth
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val period = MutableStateFlow(BodyPeriod.fromLabel(prefs.getString(ProgressLog.KEY_BODY_PERIOD, null)))

    /** Insights and Journey. Scale readings (body fat and more) come with Apple Health. */
    val progress: StateFlow<ProgressPage?> = combine(log.profileFlow, log.allNutritionEntriesFlow, log.weightEntriesFlow, period, reload) { profile, meals, weights, bodyPeriod, _ ->
        if (profile == null) return@combine null
        val tz = zone()
        val day = Now.today()
        val workoutList = log.getAllWorkoutEntries()
        val activityList = log.getAllActivityTelemetry()
        val nutrition = HistoryLog.byDay(meals, tz) { it.timestamp }
        val workouts = HistoryLog.byDay(workoutList, tz) { it.timestamp }
        val imported = importedByDay(profile, tz)
        val target = { date: LocalDate -> HistoryLog.calorieTarget(profile, prefs, imported[date].orEmpty(), workouts[date].orEmpty()) }
        val totals = DayTotalsBuilder.days(meals, workoutList, activityList, imported, target, PhysiologyEngine.proteinTargetG(profile), tz)
        ProgressPage(
            progress = ProgressLog.build(profile, prefs, meals, weights, totals, emptyList(), bodyPeriod, day, tz),
            consistency = HistoryLog.consistency(day, nutrition, workouts, imported, target),
            trends = HistoryLog.weeklyTrends(
                day,
                nutrition,
                workouts,
                imported,
                HistoryLog.activityByDay(activityList),
                HistoryLog.sleepByDay(log.getAllSleepTelemetry()),
                target
            ),
            period = bodyPeriod
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun choosePeriod(p: BodyPeriod) {
        prefs.edit { putString(ProgressLog.KEY_BODY_PERIOD, p.label) }
        period.value = p
    }

    /** "Fine for me" on a food, kept with the person's settings so ratings leave it be. */
    fun setFineForMe(key: String, fine: Boolean) {
        NutritionAdviceStore.setFineForMe(prefs, key, fine)
    }

    fun logWeight(kg: Double) {
        scope.launch { log.addWeightEntry(kg) }
    }

    /** Start weight, goal weight and the optional date to reach it by, as on Android. */
    fun updateGoal(startKg: Double, targetKg: Double, date: LocalDate?) {
        scope.launch {
            log.getProfile()?.let { log.saveProfile(it.copy(startingWeightKg = startKg, targetWeightKg = targetKg)) }
            GoalDatePref.set(prefs, date)
            GoalDatePref.markGoalSet(prefs)
            reload.value++
        }
    }

    init {
        scope.launch { _predictedFoods.value = runCatching { log.getPredictedFoods(Now.hour()) }.getOrDefault(emptyList()) }
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    fun prevMonth() {
        month.value = month.value.minus(DatePeriod(months = 1))
    }

    fun nextMonth() {
        if (month.value < thisMonth) month.value = month.value.plus(DatePeriod(months = 1))
    }

    /** Logs a meal that was missed on the picked day, at midday so it never slips into the next day. */
    fun addMeal(text: String) {
        if (text.isBlank()) return
        val day = selected.value
        scope.launch {
            _parsing.value = HistoryParsingState.Loading
            _parsing.value = try {
                val result = parser.parseFoodInput(text, log.getAllNutritionEntries())
                if (result.foodText == "invalid") {
                    HistoryParsingState.Error("Could not recognize any food items. Please try typing something like 'one apple and greek yogurt'.")
                } else {
                    log.addNutritionEntry(HistoryLog.missedMeal(result, day, zone()))
                    HistoryParsingState.Success(result.foodText)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("HistoryModel", "Missed meal failed", e)
                HistoryParsingState.Error(AiFailover.sanitizeError(e))
            }
        }
    }

    fun resetParsing() {
        _parsing.value = HistoryParsingState.Idle
    }

    /** Saves an edited meal; a changed food drops its old tags, so it is judged again, as on Android. */
    fun updateMeal(entry: NutritionEntryEntity) {
        scope.launch {
            log.updateNutritionEntry(entry)
            if (entry.qualityTags == null) tagger.tagOne(entry)?.let { log.saveMealTags(entry, it.encode()) }
        }
    }

    fun deleteMeal(entry: NutritionEntryEntity) {
        scope.launch { log.deleteNutritionEntry(entry) }
    }

    fun deleteWorkout(entry: WorkoutEntryEntity) {
        scope.launch {
            log.deleteWorkoutEntry(entry)
            reload.value++
        }
    }
}
