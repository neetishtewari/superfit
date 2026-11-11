package com.superfit.app.ui.history

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.Prefs
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.domain.WorkoutView
import com.superfit.app.ui.dashboard.TodayState
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class DayStatus {
    Deficit,
    Surplus,
    Insufficient
}

/** One day on the History calendar. */
data class HistoryDayState(
    val date: LocalDate,
    val caloriesEaten: Double,
    val tdee: Double,
    val mealsLogged: Int,
    val status: DayStatus
)

/** The day picked on the History calendar, with its meals and workouts. */
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
    val importedWorkouts: List<WorkoutView> = emptyList()
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

/** The last 30 days: how many fully logged days came in under target, and how many had a workout. */
data class LogConsistency(
    val score: Int,
    val deficitDays: Int,
    val loggedDays: Int,
    val trainingScore: Int
)

/** Everything the History page works out from the saved log, the same on Android and iPhone. */
object HistoryLog {

    /** A day needs this many meals before it counts as logged (fewer is "not fully logged"). */
    const val LOGGED_MEALS = 2

    @OptIn(ExperimentalTime::class)
    fun <T> byDay(items: List<T>, zone: TimeZone, millis: (T) -> Long): Map<LocalDate, List<T>> =
        items.groupBy { Instant.fromEpochMilliseconds(millis(it)).toLocalDateTime(zone).date }

    /** Activity rows by their saved date ("2026-11-11"); a row with a broken date is left out. */
    fun activityByDay(rows: List<ActivityTelemetryEntity>): Map<LocalDate, ActivityTelemetryEntity> =
        rows.mapNotNull { row -> runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row } }.toMap()

    fun sleepByDay(rows: List<SleepTelemetryEntity>): Map<LocalDate, SleepTelemetryEntity> =
        rows.mapNotNull { row -> runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row } }.toMap()

    /** The day's calorie target: the person's own number, or worked out with that day's workouts added back. */
    fun calorieTarget(
        profile: UserProfileEntity?,
        prefs: Prefs,
        imported: List<WorkoutView>,
        workouts: List<WorkoutEntryEntity>
    ): Double = if (prefs.getBoolean("custom_macro_enabled", false) || profile == null) {
        prefs.getInt("custom_calories", 2000).toDouble()
    } else {
        TodayState.target(profile, WorkoutImportPrefs(prefs), imported, workouts).targets.calories
    }

    /** Every day of the month that holds [anyDay], with what was eaten against that day's target. */
    fun monthDays(
        anyDay: LocalDate,
        nutrition: Map<LocalDate, List<NutritionEntryEntity>>,
        target: (LocalDate) -> Double
    ): List<HistoryDayState> {
        val days = mutableListOf<HistoryDayState>()
        var day = LocalDate(anyDay.year, anyDay.month, 1)
        while (day.month == anyDay.month) {
            val meals = nutrition[day].orEmpty()
            val eaten = meals.sumOf { it.calories }
            val tdee = target(day)
            days += HistoryDayState(
                date = day,
                caloriesEaten = eaten,
                tdee = tdee,
                mealsLogged = meals.size,
                status = when {
                    meals.size < LOGGED_MEALS -> DayStatus.Insufficient
                    eaten < tdee -> DayStatus.Deficit
                    else -> DayStatus.Surplus
                }
            )
            day = day.plus(DatePeriod(days = 1))
        }
        return days
    }

    /** The last 30 days up to [today]. A blank day never counts as eating under target. */
    fun consistency(
        today: LocalDate,
        nutrition: Map<LocalDate, List<NutritionEntryEntity>>,
        workouts: Map<LocalDate, List<WorkoutEntryEntity>>,
        imported: Map<LocalDate, List<WorkoutView>>,
        target: (LocalDate) -> Double
    ): LogConsistency {
        var deficit = 0
        var logged = 0
        var training = 0
        for (i in 0 until 30) {
            val day = today.minus(DatePeriod(days = i))
            val meals = nutrition[day].orEmpty()
            if (meals.size >= LOGGED_MEALS) {
                logged++
                if (meals.sumOf { it.calories } < target(day)) deficit++
            }
            if (workouts[day].orEmpty().isNotEmpty() || imported[day].orEmpty().isNotEmpty()) training++
        }
        return LogConsistency(
            score = if (logged > 0) ((deficit.toDouble() / logged) * 100).toInt() else 0,
            deficitDays = deficit,
            loggedDays = logged,
            trainingScore = ((training.toDouble() / 30) * 100).toInt()
        )
    }

    /** The last 7 days up to [today]: averages over the days that have each kind of data. */
    fun weeklyTrends(
        today: LocalDate,
        nutrition: Map<LocalDate, List<NutritionEntryEntity>>,
        workouts: Map<LocalDate, List<WorkoutEntryEntity>>,
        imported: Map<LocalDate, List<WorkoutView>>,
        activity: Map<LocalDate, ActivityTelemetryEntity>,
        sleep: Map<LocalDate, SleepTelemetryEntity>,
        target: (LocalDate) -> Double
    ): WeeklyTrendsState {
        var net = 0.0
        var steps = 0.0
        var sleepSeconds = 0L
        var readiness = 0
        var sleepDays = 0
        var protein = 0.0
        var tracked = 0
        var workoutCount = 0
        var strengthSets = 0
        for (i in 0 until 7) {
            val day = today.minus(DatePeriod(days = i))
            val meals = nutrition[day].orEmpty()
            val dayWorkouts = workouts[day].orEmpty()
            workoutCount += dayWorkouts.size + imported[day].orEmpty().size
            strengthSets += dayWorkouts.filter { it.workoutType == "Strength" }.sumOf { it.setsCount }
            if (meals.size >= LOGGED_MEALS) {
                net += meals.sumOf { it.calories } - target(day)
                protein += meals.sumOf { it.proteinG }
                tracked++
            }
            activity[day]?.let { steps += it.steps }
            sleep[day]?.let {
                sleepSeconds += it.sleepDurationSeconds
                readiness += it.readinessScore
                sleepDays++
            }
        }
        return WeeklyTrendsState(
            avgNetCalories = if (tracked > 0) net / tracked else 0.0,
            avgSteps = steps / 7.0,
            avgSleepDurationSeconds = if (sleepDays > 0) sleepSeconds / sleepDays else 0L,
            avgSleepReadiness = if (sleepDays > 0) readiness / sleepDays else 0,
            avgProteinG = if (tracked > 0) protein / tracked else 0.0,
            trackedDaysCount = tracked,
            totalWorkouts = workoutCount,
            totalStrengthSets = strengthSets
        )
    }

    /** The picked day: eaten against target, with burn from steps and logged workouts. */
    fun daySummary(
        date: LocalDate,
        nutrition: Map<LocalDate, List<NutritionEntryEntity>>,
        workouts: Map<LocalDate, List<WorkoutEntryEntity>>,
        activity: Map<LocalDate, ActivityTelemetryEntity>,
        imported: Map<LocalDate, List<WorkoutView>>,
        target: (LocalDate) -> Double
    ): DaySummaryState {
        val meals = nutrition[date].orEmpty()
        val dayWorkouts = workouts[date].orEmpty()
        val dayActivity = activity[date]
        val eaten = meals.sumOf { it.calories }
        val tdee = target(date)
        return DaySummaryState(
            date = date,
            caloriesEaten = eaten,
            tdee = tdee,
            activeBurn = (dayActivity?.activeCalories ?: 0.0) + dayWorkouts.sumOf { it.caloriesBurned },
            steps = dayActivity?.steps ?: 0,
            netBalance = eaten - tdee,
            meals = meals,
            workouts = dayWorkouts,
            importedWorkouts = imported[date].orEmpty()
        )
    }

    /** The chart shows the week up to the picked day (or the month's last week). */
    fun chartWeek(days: List<HistoryDayState>, selected: LocalDate): List<HistoryDayState> {
        val index = days.indexOfFirst { it.date == selected }
        return if (index != -1) days.subList((index - 6).coerceAtLeast(0), index + 1) else days.takeLast(7)
    }
}
