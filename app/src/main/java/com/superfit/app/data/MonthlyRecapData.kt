package com.superfit.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.superfit.app.domain.BodyArea
import com.superfit.app.domain.BodyTrend
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.GeminiClient
import com.superfit.app.domain.GoalForecast
import com.superfit.app.domain.MonthRecap
import com.superfit.app.domain.MonthlyRecap
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.RecapSetup
import com.superfit.app.domain.RecapWords
import com.superfit.app.domain.RecapWriter
import com.superfit.app.domain.StrengthSession
import com.superfit.app.widget.WidgetPin
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.datetime.toJavaYearMonth
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toKotlinYearMonth
import org.json.JSONObject

/**
 * Loads a month's recap from the stored logs and remembers what the user has seen. The words are
 * written by one AI call per month and coach style, then kept on the phone so reopening is free.
 */
object MonthlyRecapData {

    private const val KEY_OPENED = "recap_opened_" // + yyyy-MM -> the date it was first opened
    private const val KEY_NOTIFIED = "recap_notified_month"

    // v2: the calorie and new activity cards need their own words, so saved months are written again once.
    private const val KEY_WORDS = "recap_words_v3_" // + yyyy-MM_STYLE -> JSON
    private const val LAST_HOME_DAY = 10
    private const val DAYS_AFTER_OPENING = 2

    /** The month whose recap is offered today: last month, from the 1st. */
    fun offeredMonth(today: LocalDate): YearMonth = YearMonth.from(today).minusMonths(1)

    /**
     * Home shows the recap card from the 1st until a couple of days after it was opened, so someone
     * who skips the app on the 1st still sees it, and never after the 10th.
     */
    fun showOnHome(context: Context, today: LocalDate): Boolean {
        if (today.dayOfMonth > LAST_HOME_DAY) return false
        val opened = openedOn(context, offeredMonth(today)) ?: return true
        return !today.isAfter(opened.plusDays(DAYS_AFTER_OPENING.toLong()))
    }

    /** Progress keeps the new recap at the top for the first week of the month. */
    fun showAtTopOfProgress(today: LocalDate): Boolean = today.dayOfMonth <= 7

    fun openedOn(context: Context, month: YearMonth): LocalDate? =
        getUserSharedPrefs(context).getString(KEY_OPENED + month, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun markOpened(context: Context, month: YearMonth, today: LocalDate = LocalDate.now()) {
        if (openedOn(context, month) == null) getUserSharedPrefs(context).edit().putString(KEY_OPENED + month, today.toString()).apply()
    }

    /** True once per month: the notification on the 1st goes out at most once. */
    fun claimNotification(context: Context, month: YearMonth): Boolean {
        val prefs = getUserSharedPrefs(context)
        if (prefs.getString(KEY_NOTIFIED, null) == month.toString()) return false
        prefs.edit().putString(KEY_NOTIFIED, month.toString()).apply()
        return true
    }

    /** Builds [month]'s recap from everything stored. Null when there's too little to say. */
    suspend fun load(context: Context, repository: DataRepository, month: YearMonth, today: LocalDate = LocalDate.now()): MonthRecap? {
        val profile = repository.getProfile() ?: return null
        val prefs = getUserSharedPrefs(context)
        val zone = ZoneId.systemDefault()
        val views = repository.getImportedWorkoutViews(PhysiologyEngine.calculateBmr(profile))
        val importedByDate = ImportedWorkouts.byDate(views)
        val nutrition = repository.getAllNutritionEntries()
        val workouts = repository.getAllWorkoutEntries()
        val workoutsByDate = workouts.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        fun target(date: LocalDate): Double =
            if (prefs.getBoolean("custom_macro_enabled", false)) {
                prefs.getInt("custom_calories", 2000).toDouble()
            } else {
                ImportedWorkouts.target(profile, repository.workoutImportPrefs, importedByDate[date].orEmpty(), workoutsByDate[date].orEmpty()).targets.calories
            }
        val days = InsightsData.days(nutrition, workouts, repository.getAllActivityTelemetry(), importedByDate, ::target, PhysiologyEngine.proteinTargetG(profile))
        val weighIns = InsightsData.weighIns(repository.getAllWeightEntries())
        val kWeighIns = weighIns.map { it.first.toKotlinLocalDate() to it.second }

        // The goal as it stood at the end of the month (or today, for a month still going).
        val asOf = minOf(month.atEndOfMonth().plusDays(1), today)
        val goalSet = GoalDatePref.hasGoal(AndroidPrefs(prefs), profile)
        val journey = GoalForecast.compute(
            startKg = profile.startingWeightKg,
            targetKg = profile.targetWeightKg,
            startDate = GoalForecast.startDate(
                profileStart = profile.startDateTimestamp.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toKotlinLocalDate() },
                weighIns = kWeighIns,
                firstLogged = nutrition.minOfOrNull { it.timestamp }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toKotlinLocalDate() },
                today = asOf.toKotlinLocalDate()
            ),
            weighIns = kWeighIns.filter { it.first <= asOf.toKotlinLocalDate() },
            days = days,
            calorieOffset = profile.calorieOffset,
            targetDate = GoalDatePref.get(AndroidPrefs(prefs)),
            today = asOf.toKotlinLocalDate(),
            fallbackCurrentKg = profile.weightKg
        )
        val strengthLogs = workouts.filter { it.workoutType == "Strength" }
        val tags = strengthTags(context, strengthLogs.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month })
        val strength = strengthLogs.map { w ->
            val t = tags[tagKey(w)]
            StrengthSession(Instant.ofEpochMilli(w.timestamp).atZone(zone).toLocalDate().toKotlinLocalDate(), t?.second, t?.first.orEmpty())
        } + views.filter { it.title == "Strength workout" }.map { v ->
            StrengthSession(Instant.ofEpochMilli(v.startTime).atZone(zone).toLocalDate().toKotlinLocalDate(), v.minutes.toInt().takeIf { it > 0 })
        }
        val hc = repository.healthConnectManager
        val setup = RecapSetup(
            healthConnect = !hc.isAvailable() || runCatching { hc.hasAllPermissions() }.getOrDefault(true),
            mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            widget = !WidgetPin.canPin(context) || WidgetPin.isPlaced(context)
        )
        val body = BodyTrend.month(BodyImporter.mine(context), month.year, month.monthValue, zone.toKotlinTimeZone())
        return MonthlyRecap.build(month.toKotlinYearMonth(), days, nutrition, strength, kWeighIns, journey, goalSet, setup, zone.toKotlinTimeZone(), body)
    }

    fun tagKey(w: WorkoutEntryEntity) = StrengthTags.key(w)

    /** What each voice-logged strength session worked and how long it took (shared with the iPhone). */
    suspend fun strengthTags(context: Context, logs: List<WorkoutEntryEntity>): Map<String, Pair<Set<BodyArea>, Int?>> =
        StrengthTags.load(AndroidPrefs(getUserSharedPrefs(context)), logs)

    /**
     * The card words in the current coach style: saved ones if this month was already written,
     * else one AI call. A month still in progress (the preview) is never saved.
     */
    suspend fun words(context: Context, recap: MonthRecap, today: LocalDate = LocalDate.now()): Map<String, RecapWords> {
        val prefs = getUserSharedPrefs(context)
        val month = recap.month.toJavaYearMonth()
        val key = KEY_WORDS + month + "_" + CoachStyle.current.name
        val finished = month.isBefore(YearMonth.from(today))
        if (finished) prefs.getString(key, null)?.let { return RecapWriter.parse(it, recap) }
        val json = runCatching {
            GeminiClient.generateContent(
                prompt = RecapWriter.prompt(recap),
                systemInstructionText = RecapWriter.SYSTEM_INSTRUCTION + CoachStyle.current.instruction(),
                isJson = true
            )
        }.getOrNull() ?: return recap.cards.associate { it.id to RecapWriter.plain(it) }
        if (finished && runCatching { JSONObject(json) }.isSuccess) prefs.edit().putString(key, json).apply()
        return RecapWriter.parse(json, recap)
    }
}
