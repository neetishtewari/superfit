package com.superfit.app.ui.history

import com.superfit.app.data.GoalDatePref
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.Prefs
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.domain.BodyHistory
import com.superfit.app.domain.BodyPeriod
import com.superfit.app.domain.BodyReadings
import com.superfit.app.domain.BodySeries
import com.superfit.app.domain.BodySummary
import com.superfit.app.domain.BodyTrend
import com.superfit.app.domain.DayTotals
import com.superfit.app.domain.GoalForecast
import com.superfit.app.domain.InsightsEngine
import com.superfit.app.domain.JourneyForecast
import com.superfit.app.domain.ScaleReading
import com.superfit.app.domain.WeekReport
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Insights and Journey, worked out the same way on Android and iPhone. */
data class ProgressState(
    val report: WeekReport,
    val journey: JourneyForecast,
    val goalSet: Boolean,
    val goalDate: LocalDate?,
    val body: BodySummary,
    val latestWeightKg: Double,
    val weightChange4w: Double?,
    val goalKgChange: Double,
    val bodySeries: List<BodySeries>,
    val bodyHasHistory: Boolean
)

object ProgressLog {

    /** Where the chosen body trend period (1M, 3M...) is kept. */
    const val KEY_BODY_PERIOD = "body_trend_period"

    /** One weight a day (the day's last), oldest first. */
    @OptIn(ExperimentalTime::class)
    fun weighIns(entries: List<WeightEntryEntity>, zone: TimeZone): List<Pair<LocalDate, Double>> =
        entries.sortedBy { it.timestamp }
            .map { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).date to it.weightKg }
            .groupBy { it.first }
            .map { (date, list) -> date to list.last().second }
            .sortedBy { it.first }

    @OptIn(ExperimentalTime::class)
    fun build(
        profile: UserProfileEntity,
        prefs: Prefs,
        meals: List<NutritionEntryEntity>,
        weights: List<WeightEntryEntity>,
        days: Map<LocalDate, DayTotals>,
        scale: List<ScaleReading>,
        period: BodyPeriod,
        today: LocalDate,
        zone: TimeZone
    ): ProgressState {
        fun dateOf(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
        val weighIns = weighIns(weights, zone)
        val goalSet = GoalDatePref.hasGoal(prefs, profile)
        val goalDate = GoalDatePref.get(prefs)
        val report = InsightsEngine.weekReport(
            days,
            today,
            profile.targetWeightKg < profile.startingWeightKg,
            meals,
            body = BodyTrend.fourWeeks(scale, today, zone)
        )
        val journey = GoalForecast.compute(
            startKg = profile.startingWeightKg,
            targetKg = profile.targetWeightKg,
            startDate = GoalForecast.startDate(
                profileStart = profile.startDateTimestamp.takeIf { it > 0 }?.let(::dateOf),
                weighIns = weighIns,
                firstLogged = meals.minOfOrNull { it.timestamp }?.let(::dateOf),
                today = today
            ),
            weighIns = weighIns,
            days = days,
            calorieOffset = profile.calorieOffset,
            targetDate = goalDate,
            today = today,
            fallbackCurrentKg = profile.weightKg
        )
        val goalKgChange = if (goalSet) profile.targetWeightKg - profile.startingWeightKg else 0.0
        return ProgressState(
            report = report,
            journey = journey,
            goalSet = goalSet,
            goalDate = goalDate,
            body = BodyReadings.summary(
                mine = scale,
                latestWeightKg = weights.maxByOrNull { it.timestamp }?.weightKg ?: profile.weightKg,
                heightCm = profile.heightCm,
                today = today,
                zone = zone
            ),
            latestWeightKg = weighIns.lastOrNull()?.second ?: profile.weightKg,
            weightChange4w = weighIns.lastOrNull()?.let { (lastDay, lastKg) ->
                weighIns.lastOrNull { it.first <= lastDay.minus(28, DateTimeUnit.DAY) }?.let { lastKg - it.second }
            },
            goalKgChange = goalKgChange,
            bodySeries = BodyHistory.series(weighIns, scale, profile.heightCm, goalKgChange, period, today, zone),
            bodyHasHistory = weighIns.size >= 2
        )
    }
}
