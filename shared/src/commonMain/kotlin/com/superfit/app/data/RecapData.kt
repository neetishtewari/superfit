package com.superfit.app.data

import com.superfit.app.domain.Ai
import com.superfit.app.domain.BodyTrend
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.GoalForecast
import com.superfit.app.domain.MonthRecap
import com.superfit.app.domain.MonthlyRecap
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.RecapSetup
import com.superfit.app.domain.RecapWords
import com.superfit.app.domain.RecapWriter
import com.superfit.app.domain.ScaleReading
import com.superfit.app.domain.StrengthSession
import com.superfit.app.json.JSONObject
import com.superfit.app.ui.history.ProgressLog
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

/**
 * A month's recap from the stored logs, and what the person has seen of it, worked out the same
 * way as Android's MonthlyRecapData (same settings keys, so a restored backup agrees). The words
 * come from one AI call per month and coach style, kept on the phone so reopening is free.
 */
@OptIn(ExperimentalTime::class)
object RecapData {
    private const val KEY_OPENED = "recap_opened_" // + yyyy-MM -> the date it was first opened
    private const val KEY_NOTIFIED = "recap_notified_month"
    private const val KEY_WORDS = "recap_words_v3_" // + yyyy-MM_STYLE -> JSON
    private const val LAST_HOME_DAY = 10
    private const val DAYS_AFTER_OPENING = 2

    /** The month whose recap is offered today: last month, from the 1st. */
    fun offeredMonth(today: LocalDate): YearMonth = today.minus(DatePeriod(months = 1)).yearMonth

    /** Home shows the recap card from the 1st until a couple of days after it was opened, never after the 10th. */
    fun showOnHome(prefs: Prefs, today: LocalDate): Boolean {
        if (today.day > LAST_HOME_DAY) return false
        val opened = openedOn(prefs, offeredMonth(today)) ?: return true
        return today <= opened.plus(DatePeriod(days = DAYS_AFTER_OPENING))
    }

    /** Progress keeps the new recap at the top for the first week of the month. */
    fun showAtTopOfProgress(today: LocalDate): Boolean = today.day <= 7

    fun openedOn(prefs: Prefs, month: YearMonth): LocalDate? =
        prefs.getString(KEY_OPENED + month, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun markOpened(prefs: Prefs, month: YearMonth, today: LocalDate) {
        if (openedOn(prefs, month) == null) prefs.edit { putString(KEY_OPENED + month, today.toString()) }
    }

    /** True once per month: the reminder on the 1st goes out at most once. */
    fun claimNotification(prefs: Prefs, month: YearMonth): Boolean {
        if (prefs.getString(KEY_NOTIFIED, null) == month.toString()) return false
        prefs.edit { putString(KEY_NOTIFIED, month.toString()) }
        return true
    }

    /** Builds [month]'s recap from everything stored. Null when there's too little to say. */
    suspend fun load(
        log: LogRepository,
        prefs: Prefs,
        month: YearMonth,
        today: LocalDate,
        setup: RecapSetup,
        scale: List<ScaleReading>,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): MonthRecap? {
        val profile = log.getProfile() ?: return null
        fun dateOf(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
        val views = WorkoutSync.views(log.importedWorkouts.getAll(), WorkoutImportPrefs(prefs), HealthSourceNames(prefs), PhysiologyEngine.calculateBmr(profile))
        val imported = DayTotalsBuilder.byDate(views, zone)
        val nutrition = log.getAllNutritionEntries()
        val workouts = log.getAllWorkoutEntries()
        val workoutsByDate = workouts.groupBy { dateOf(it.timestamp) }
        val target = { date: LocalDate -> com.superfit.app.ui.history.HistoryLog.calorieTarget(profile, prefs, imported[date].orEmpty(), workoutsByDate[date].orEmpty()) }
        val days = DayTotalsBuilder.days(nutrition, workouts, log.getAllActivityTelemetry(), imported, target, PhysiologyEngine.proteinTargetG(profile), zone)
        val weighIns = ProgressLog.weighIns(log.getAllWeightEntries(), zone)

        // The goal as it stood at the end of the month (or today, for a month still going).
        val asOf = minOf(month.lastDay.plus(DatePeriod(days = 1)), today)
        val journey = GoalForecast.compute(
            startKg = profile.startingWeightKg,
            targetKg = profile.targetWeightKg,
            startDate = GoalForecast.startDate(
                profileStart = profile.startDateTimestamp.takeIf { it > 0 }?.let(::dateOf),
                weighIns = weighIns,
                firstLogged = nutrition.minOfOrNull { it.timestamp }?.let(::dateOf),
                today = asOf
            ),
            weighIns = weighIns.filter { it.first <= asOf },
            days = days,
            calorieOffset = profile.calorieOffset,
            targetDate = GoalDatePref.get(prefs),
            today = asOf,
            fallbackCurrentKg = profile.weightKg
        )
        val strengthLogs = workouts.filter { it.workoutType == "Strength" }
        val tags = StrengthTags.load(prefs, strengthLogs.filter { dateOf(it.timestamp).yearMonth == month })
        val strength = strengthLogs.map { w ->
            val t = tags[StrengthTags.key(w)]
            StrengthSession(dateOf(w.timestamp), t?.second, t?.first.orEmpty())
        } + views.filter { it.title == "Strength workout" }.map { v ->
            StrengthSession(dateOf(v.startTime), v.minutes.toInt().takeIf { it > 0 })
        }
        val body = BodyTrend.month(scale, month.year, month.month.ordinal + 1, zone)
        return MonthlyRecap.build(month, days, nutrition, strength, weighIns, journey, GoalDatePref.hasGoal(prefs, profile), setup, zone, body)
    }

    /**
     * The card words in the current coach style: saved ones if this month was already written,
     * else one AI call. A month still in progress (the preview) is never saved.
     */
    suspend fun words(prefs: Prefs, recap: MonthRecap, today: LocalDate): Map<String, RecapWords> {
        val key = KEY_WORDS + recap.month + "_" + CoachStyle.current.name
        val finished = recap.month < today.yearMonth
        if (finished) prefs.getString(key, null)?.let { return RecapWriter.parse(it, recap) }
        val json = runCatching {
            Ai.generate(
                prompt = RecapWriter.prompt(recap),
                systemInstructionText = RecapWriter.SYSTEM_INSTRUCTION + CoachStyle.current.instruction(),
                isJson = true
            )
        }.getOrNull() ?: return recap.cards.associate { it.id to RecapWriter.plain(it) }
        if (finished && runCatching { JSONObject(json) }.isSuccess) prefs.edit { putString(key, json) }
        return RecapWriter.parse(json, recap)
    }
}
