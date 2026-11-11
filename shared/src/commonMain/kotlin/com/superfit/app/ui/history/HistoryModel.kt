package com.superfit.app.ui.history

import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

/**
 * History for the iPhone, worked out by the same [HistoryLog] as Android: a month calendar of
 * logged days and the picked day's meals. Workouts from other apps join with Apple Health.
 */
class HistoryModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    scope: CoroutineScope,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    private val today = Now.today()
    private val thisMonth = LocalDate(today.year, today.month, 1)
    private val month = MutableStateFlow(thisMonth)

    // Yesterday, like Android: today is on the Today tab.
    private val selected = MutableStateFlow(today.minus(DatePeriod(days = 1)))

    val page: StateFlow<HistoryPage?> = combine(log.profileFlow, log.allNutritionEntriesFlow, month, selected) { profile, meals, monthStart, day ->
        if (profile == null) return@combine null
        val tz = zone()
        val nutrition = HistoryLog.byDay(meals, tz) { it.timestamp }
        val workouts = HistoryLog.byDay(log.getAllWorkoutEntries(), tz) { it.timestamp }
        val activity = HistoryLog.activityByDay(log.getAllActivityTelemetry())
        val target = { date: LocalDate -> HistoryLog.calorieTarget(profile, prefs, emptyList(), workouts[date].orEmpty()) }
        HistoryPage(
            monthStart = monthStart,
            days = HistoryLog.monthDays(monthStart, nutrition, target),
            selected = day,
            summary = HistoryLog.daySummary(day, nutrition, workouts, activity, emptyMap(), target),
            canGoNext = monthStart < thisMonth
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun select(date: LocalDate) {
        selected.value = date
    }

    fun prevMonth() {
        month.value = month.value.minus(DatePeriod(months = 1))
    }

    fun nextMonth() {
        if (month.value < thisMonth) month.value = month.value.plus(DatePeriod(months = 1))
    }
}
