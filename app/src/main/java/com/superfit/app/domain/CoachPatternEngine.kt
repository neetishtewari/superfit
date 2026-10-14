package com.superfit.app.domain

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MealSlot { BREAKFAST, LUNCH, SNACK, DINNER }

/**
 * How much of a day's eating we think was logged. People often forget to log a meal, so
 * daily totals (protein, calories) are only judged on COMPLETE days. Foods that were logged
 * are facts on any day, so treats and per-meal checks use PARTIAL days too.
 */
enum class DayLogStatus { COMPLETE, PARTIAL, BLANK }

data class DayLog(
    val date: LocalDate,
    val entries: List<NutritionEntryEntity>,
    val calories: Double,
    val proteinG: Double,
    val mainMealsLogged: Set<MealSlot>,
    val status: DayLogStatus
)

enum class FindingKind { CONCERN, WIN, LOGGING }

/**
 * One thing the coach noticed. [fact] and [meaning] are plain sentences shown to the user
 * under "Why?" and handed to the AI; [suggestion] is the fallback action if the AI is down.
 */
data class CoachFinding(
    val id: String,
    val area: String,
    val kind: FindingKind,
    val priority: Int,
    val fact: String,
    val meaning: String,
    val suggestion: String
)

data class TodaySnapshot(
    val caloriesSoFar: Int,
    val proteinSoFar: Int,
    val proteinStillNeeded: Int,
    val mealsLogged: List<MealSlot>,
    val treatsToday: List<String>,
    val hour: Int
)

data class CoachReport(
    /** Highest priority first. */
    val findings: List<CoachFinding>,
    /** Areas that had enough data to be judged, whether or not they produced a finding. */
    val checkedAreas: Set<String>,
    /** Yesterday first, going back [CoachPatternEngine.LOOKBACK_DAYS] days. Today is not included. */
    val days: List<DayLog>,
    val typicalDayCalories: Int,
    val completeDaysLastWeek: Int,
    val loggedDaysLastWeek: Int,
    val today: TodaySnapshot,
    /** What the user's targets mean for them, e.g. protein per kg of body weight. */
    val targetMeanings: List<String>
)

/**
 * Looks at the last few weeks of logs the way a coach would: one short day is fine, a run of
 * them is a pattern. All counting happens here, in plain code; the AI only puts the result
 * into words.
 */
object CoachPatternEngine {

    const val LOOKBACK_DAYS = 35
    const val WEEKLY_TREAT_ALLOWANCE = 2

    /** Pattern checks on daily totals need at least this many complete days in the last week. */
    const val MIN_COMPLETE_DAYS = 3

    fun analyze(
        today: LocalDate,
        now: LocalTime,
        weightKg: Double,
        goal: String,
        targets: MacroTargets,
        maintenanceCalories: Double?,
        nutrition: List<NutritionEntryEntity>,
        workouts: List<WorkoutEntryEntity>,
        activity: List<ActivityTelemetryEntity>,
        sleep: List<SleepTelemetryEntity>,
        weights: List<WeightEntryEntity> = emptyList(),
        targetWeightKg: Double? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): CoachReport {
        val byDate = nutrition.groupBy { dateOf(it.timestamp, zone) }
        val rawDays = (1..LOOKBACK_DAYS).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to byDate[date].orEmpty()
        }
        val typical = typicalDayCalories(rawDays.map { it.second }, targets.calories)
        val days = rawDays.map { (date, entries) -> buildDayLog(date, entries, typical, targets.calories, zone) }

        val lastWeek = days.take(7)
        val completeLastWeek = lastWeek.filter { it.status == DayLogStatus.COMPLETE }
        val loggedLastWeek = lastWeek.filter { it.status != DayLogStatus.BLANK }

        val findings = mutableListOf<CoachFinding>()
        val checked = mutableSetOf<String>()

        checkLogging(lastWeek, days.take(14), completeLastWeek.size, zone, findings)
        checkProtein(completeLastWeek, lastWeek, targets, weightKg, goal, zone, findings, checked)
        checkCalories(completeLastWeek, days.take(28), targets, maintenanceCalories, goal, findings, checked)
        checkWeight(today, weights, goal, targetWeightKg, zone, findings, checked)
        checkTreats(today, byDate, zone, goal, days.take(7).any { it.status == DayLogStatus.BLANK }, findings, checked)
        checkVegAndFruit(completeLastWeek, findings, checked)
        checkSteps(today, activity, weightKg, findings, checked)
        checkSleep(today, sleep, findings, checked)
        checkWorkouts(today, workouts, zone, findings, checked)

        val todayEntries = byDate[today].orEmpty()
        val proteinSoFar = todayEntries.sumOf { it.proteinG }
        val todaySnapshot = TodaySnapshot(
            caloriesSoFar = todayEntries.sumOf { it.calories }.roundToInt(),
            proteinSoFar = proteinSoFar.roundToInt(),
            proteinStillNeeded = (targets.proteinG - proteinSoFar).coerceAtLeast(0.0).roundToInt(),
            mealsLogged = todayEntries.map { slotOf(it.timestamp, zone) }.distinct().sorted(),
            treatsToday = todayEntries.mapNotNull { treatCategoryOf(it) }.distinct(),
            hour = now.hour
        )

        return CoachReport(
            findings = findings.sortedByDescending { it.priority },
            checkedAreas = checked,
            days = days,
            typicalDayCalories = typical.roundToInt(),
            completeDaysLastWeek = completeLastWeek.size,
            loggedDaysLastWeek = loggedLastWeek.size,
            today = todaySnapshot,
            targetMeanings = targetMeanings(targets, weightKg, goal, maintenanceCalories)
        )
    }

    // ---------------------------------------------------------------------------------------
    // Day completeness
    // ---------------------------------------------------------------------------------------

    fun slotOf(timestamp: Long, zone: ZoneId): MealSlot {
        val hour = Instant.ofEpochMilli(timestamp).atZone(zone).hour
        return when (hour) {
            in 4..10 -> MealSlot.BREAKFAST
            in 11..15 -> MealSlot.LUNCH
            in 16..17 -> MealSlot.SNACK
            else -> MealSlot.DINNER
        }
    }

    /**
     * What a normal, fully logged day looks like for this person. The upper-middle of their
     * logged days is used because days with a forgotten meal pull the plain average down.
     * Kept within a sensible band around the target so a few odd days can't skew it.
     */
    fun typicalDayCalories(loggedDays: List<List<NutritionEntryEntity>>, targetCalories: Double): Double {
        val totals = loggedDays.filter { it.isNotEmpty() }.map { day -> day.sumOf { it.calories } }.sorted()
        if (totals.size < 5) return targetCalories
        val upperMiddle = totals[((totals.size - 1) * 0.75).roundToInt()]
        return upperMiddle.coerceIn(targetCalories * 0.6, targetCalories * 1.3)
    }

    fun buildDayLog(
        date: LocalDate,
        entries: List<NutritionEntryEntity>,
        typicalCalories: Double,
        targetCalories: Double,
        zone: ZoneId
    ): DayLog {
        val calories = entries.sumOf { it.calories }
        val mainMeals = entries.map { slotOf(it.timestamp, zone) }.filter { it != MealSlot.SNACK }.toSet()
        val status = when {
            entries.isEmpty() -> DayLogStatus.BLANK
            // Every main meal logged: a lighter day is most likely a real light day.
            mainMeals.size == 3 -> if (calories >= typicalCalories * 0.6) DayLogStatus.COMPLETE else DayLogStatus.PARTIAL
            // A main meal is missing. Only trust the day if it still looks like a normal day's eating
            // (some people really do skip breakfast); otherwise the meal was most likely not logged.
            calories >= typicalCalories * 0.8 && calories >= targetCalories * 0.7 -> DayLogStatus.COMPLETE
            else -> DayLogStatus.PARTIAL
        }
        return DayLog(date, entries, calories, entries.sumOf { it.proteinG }, mainMeals, status)
    }

    // ---------------------------------------------------------------------------------------
    // Checks
    // ---------------------------------------------------------------------------------------

    private fun checkLogging(
        lastWeek: List<DayLog>,
        lastTwoWeeks: List<DayLog>,
        completeDays: Int,
        zone: ZoneId,
        out: MutableList<CoachFinding>
    ) {
        val blanks = lastWeek.filter { it.status == DayLogStatus.BLANK }
        val logged = lastWeek.size - blanks.size
        if (blanks.size >= 2 && logged >= 1) {
            out += CoachFinding(
                id = "logging_gaps", area = "logging", kind = FindingKind.LOGGING, priority = 40,
                fact = "No meals were logged on ${blanks.size} of the last 7 days (${blanks.take(3).joinToString(", ") { fmt(it.date) }}).",
                meaning = "Days without logs are left out rather than counted as not eating, so my advice is only as good as the days you log.",
                suggestion = "Log meals as you eat them today, even a rough one-line entry counts."
            )
        }

        // A main meal that's missing on most logged days: skipped on purpose, or eaten and not logged.
        val loggedDays = lastTwoWeeks.filter { it.status != DayLogStatus.BLANK }
        if (loggedDays.size >= 5) {
            val (slot, missing) = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)
                .map { s -> s to loggedDays.count { s !in it.mainMealsLogged } }
                .maxBy { it.second }
            if (missing >= loggedDays.size * 0.7) {
                val name = slot.name.lowercase()
                out += CoachFinding(
                    id = "logging_missing_$name", area = "logging", kind = FindingKind.LOGGING, priority = 30,
                    fact = "${name.replaceFirstChar { it.uppercase() }} is missing on $missing of your last ${loggedDays.size} logged days.",
                    meaning = "If you skip $name on purpose, that's fine. If you eat it and forget to log it, your daily totals look lower than they really are.",
                    suggestion = "If you have $name today, add a quick log for it."
                )
            }
        }

        if (completeDays < MIN_COMPLETE_DAYS && logged >= 2) {
            out += CoachFinding(
                id = "logging_few_complete", area = "logging", kind = FindingKind.LOGGING, priority = 35,
                fact = "Only $completeDays of the last 7 days look fully logged; the rest look like a meal or two was missed.",
                meaning = "I need about $MIN_COMPLETE_DAYS fully logged days in a week before I judge protein or calorie patterns, so for now I only comment on the meals you did log.",
                suggestion = "Try logging every meal today so I can give you a proper read on your week."
            )
        }
    }

    private fun checkProtein(
        completeDays: List<DayLog>,
        lastWeek: List<DayLog>,
        targets: MacroTargets,
        weightKg: Double,
        goal: String,
        zone: ZoneId,
        out: MutableList<CoachFinding>,
        checked: MutableSet<String>
    ) {
        val target = targets.proteinG
        val meaning = proteinMeaning(target, weightKg, goal)

        if (completeDays.size >= MIN_COMPLETE_DAYS) {
            checked += "protein"
            val recent = completeDays.take(5)
            val short = recent.filter { it.proteinG < target * 0.8 }
            if (short.size >= 3) {
                val avg = short.map { it.proteinG }.average().roundToInt()
                out += CoachFinding(
                    id = "protein_low", area = "protein", kind = FindingKind.CONCERN,
                    priority = if (goal == "LOSE_WEIGHT" || goal == "GAIN_MUSCLE") 92 else 85,
                    fact = "Protein came in under target on ${if (short.size == recent.size) "all" else short.size} of your last ${recent.size} fully logged days (about ${avg}g on those days vs your ${target.roundToInt()}g target).",
                    meaning = "One low day doesn't matter, but this is a pattern. $meaning",
                    suggestion = "Add a protein portion to your next meal, like eggs, paneer, chicken, curd or dal, about 20 to 30g."
                )
                return
            }
            val hit = completeDays.count { it.proteinG >= target * 0.9 }
            if (hit >= 5 || (hit == completeDays.size && hit >= 4)) {
                out += CoachFinding(
                    id = "protein_good", area = "protein", kind = FindingKind.WIN, priority = 50,
                    fact = "You hit your protein target on $hit of your last ${completeDays.size} fully logged days.",
                    meaning = meaning,
                    suggestion = "Keep the same protein habits going today."
                )
                return
            }
            // Enough complete days and no pattern either way: nothing to say about protein.
            return
        }

        // Works even with gaps in logging: judge each logged main meal on its own.
        val meals = lastWeek.flatMap { day ->
            day.entries.groupBy { slotOf(it.timestamp, zone) }
                .filterKeys { it != MealSlot.SNACK }
                .values.map { meal -> meal.sumOf { it.proteinG } }
        }
        if (meals.size >= 6) {
            checked += "protein"
            val light = meals.count { it < 15.0 }
            if (light >= meals.size * 0.6) {
                val perMeal = (target / 3).roundToInt()
                out += CoachFinding(
                    id = "protein_meals_light", area = "protein", kind = FindingKind.CONCERN, priority = 70,
                    fact = "$light of the ${meals.size} main meals you logged this week had less than 15g of protein.",
                    meaning = "To reach your ${target.roundToInt()}g a day, each main meal needs roughly ${perMeal}g. $meaning",
                    suggestion = "Build your next meal around a protein: eggs, paneer, chicken, fish, tofu, curd or a big bowl of dal."
                )
            }
        }
    }

    private fun checkCalories(
        completeDays: List<DayLog>,
        lastFourWeeks: List<DayLog>,
        targets: MacroTargets,
        maintenanceCalories: Double?,
        goal: String,
        out: MutableList<CoachFinding>,
        checked: MutableSet<String>
    ) {
        val target = targets.calories
        if (completeDays.size >= 4) {
            checked += "calories"
            val avg = completeDays.map { it.calories }.average()
            val biggest = completeDays.maxBy { it.calories }
            when {
                goal == "LOSE_WEIGHT" && avg > target * 1.1 -> {
                    val extra = (avg - target).roundToInt()
                    val deficit = maintenanceCalories?.let { it - target }
                    val impact = if (deficit != null && deficit > 0) {
                        val share = (extra / deficit * 100).roundToInt().coerceAtMost(100)
                        "That cancels about $share% of the deficit you planned, so weight loss will be slower than expected."
                    } else {
                        "Over a week that adds up to about ${num(extra * 7)} kcal, close to a full day's food."
                    }
                    out += CoachFinding(
                        id = "calories_high", area = "calories", kind = FindingKind.CONCERN, priority = 80,
                        fact = "On fully logged days you averaged ${num(avg.roundToInt())} kcal, about $extra kcal a day over your ${num(target.roundToInt())} target.",
                        meaning = impact,
                        suggestion = "Keep today's portions a little smaller, especially rice, roti and oil, rather than skipping a meal."
                    )
                }
                goal == "GAIN_MUSCLE" && avg < target * 0.9 -> out += CoachFinding(
                    id = "calories_low_for_gain", area = "calories", kind = FindingKind.CONCERN, priority = 75,
                    fact = "On fully logged days you averaged ${num(avg.roundToInt())} kcal, under your ${num(target.roundToInt())} target.",
                    meaning = "Building muscle needs a small surplus. Below target, your training has less to build with.",
                    suggestion = "Add one extra snack today, like a banana with peanut butter, or curd with nuts."
                )
                avg < target * 0.75 -> out += CoachFinding(
                    id = "calories_very_low", area = "calories", kind = FindingKind.CONCERN, priority = 78,
                    fact = "Even on days that look fully logged, you averaged ${num(avg.roundToInt())} kcal against a ${num(target.roundToInt())} target.",
                    meaning = "A gap this big makes it hard to hold on to muscle and usually leads to strong hunger later. If you're simply forgetting to log some food, that's worth fixing first.",
                    suggestion = "Make sure today has three proper meals, each with a protein."
                )
                abs(avg - target) <= target * 0.1 -> {
                    val note = if (biggest.calories > target * 1.25) {
                        " That includes a ${num(biggest.calories.roundToInt())} kcal day on ${fmt(biggest.date)}, and the rest of the week balanced it out."
                    } else ""
                    out += CoachFinding(
                        id = "calories_on_track", area = "calories", kind = FindingKind.WIN, priority = 45,
                        fact = "Your fully logged days averaged ${num(avg.roundToInt())} kcal, right around your ${num(target.roundToInt())} target.$note",
                        meaning = "What matters is the weekly average, not any single day. You're on course for your goal.",
                        suggestion = "Keep the same rhythm today."
                    )
                }
            }
        }

        // Weekend vs weekday, over a longer window so a single weekend can't trigger it.
        val complete = lastFourWeeks.filter { it.status == DayLogStatus.COMPLETE }
        val weekend = complete.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val weekday = complete - weekend.toSet()
        if (weekend.size >= 2 && weekday.size >= 3) {
            val weekendAvg = weekend.map { it.calories }.average()
            val weekdayAvg = weekday.map { it.calories }.average()
            if (weekendAvg > weekdayAvg * 1.25 && weekendAvg > target) {
                val extraPerWeek = ((weekendAvg - weekdayAvg) * 2).roundToInt()
                out += CoachFinding(
                    id = "calories_weekend", area = "calories", kind = FindingKind.CONCERN,
                    priority = if (goal == "LOSE_WEIGHT") 60 else 40,
                    fact = "Weekends average ${num(weekendAvg.roundToInt())} kcal vs ${num(weekdayAvg.roundToInt())} on weekdays.",
                    meaning = "The two weekend days add about ${num(extraPerWeek)} kcal a week on top of your weekdays, which can quietly undo the week's progress.",
                    suggestion = "Plan one weekend treat meal in advance and keep the other weekend meals like a normal weekday."
                )
            }
        }
    }

    /**
     * The scale, read as a trend: a straight line through the last 4 weeks of weigh-ins, so
     * day-to-day water swings don't count. Needs at least 3 weigh-ins spread over 2+ weeks.
     */
    private fun checkWeight(
        today: LocalDate,
        weights: List<WeightEntryEntity>,
        goal: String,
        targetWeightKg: Double?,
        zone: ZoneId,
        out: MutableList<CoachFinding>,
        checked: MutableSet<String>
    ) {
        if (weights.isEmpty()) return
        val readings = weights.map { dateOf(it.timestamp, zone) to it.weightKg }.sortedBy { it.first }
        val lastDate = readings.last().first
        val daysSinceLast = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today).toInt()
        val trendMeaning = "Weight jumps around by a kilo or more from day to day with water and food, so I go by the trend over weeks, not single readings."

        val recent = readings.filter { java.time.temporal.ChronoUnit.DAYS.between(it.first, today) <= 28 }
        val span = if (recent.size >= 2) java.time.temporal.ChronoUnit.DAYS.between(recent.first().first, recent.last().first).toInt() else 0
        if (recent.size < 3 || span < 14) {
            if (daysSinceLast >= 14) {
                out += CoachFinding(
                    id = "weight_stale", area = "weight", kind = FindingKind.LOGGING, priority = 25,
                    fact = "Your last weigh-in was $daysSinceLast days ago.",
                    meaning = "Weighing in once or twice a week, at the same time of day, is how I can tell whether the plan is working. $trendMeaning",
                    suggestion = "Weigh yourself tomorrow morning, before eating, and log it."
                )
            }
            return
        }
        checked += "weight"

        // Least-squares slope in kg per day.
        val xs = recent.map { java.time.temporal.ChronoUnit.DAYS.between(recent.first().first, it.first).toDouble() }
        val ys = recent.map { it.second }
        val xMean = xs.average()
        val yMean = ys.average()
        val denominator = xs.sumOf { (it - xMean) * (it - xMean) }
        if (denominator == 0.0) return
        val slopePerDay = xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) } / denominator
        val perWeek = slopePerDay * 7
        val current = yMean + slopePerDay * (xs.last() - xMean)
        val weeks = (span / 7.0).roundToInt().coerceAtLeast(2)
        val change = slopePerDay * span
        val onePercent = current * 0.01
        val start = fmtKg(recent.first().second)
        val end = fmtKg(recent.last().second)
        val toGo = targetWeightKg?.takeIf { it > 0 && abs(current - it) >= 0.5 }?.let {
            " About ${fmtKg(abs(current - it))} kg to go to your target of ${fmtKg(it)} kg."
        } ?: ""
        val caloriesOnTrack = out.any { it.id == "calories_on_track" }
        val caloriesHigh = out.any { it.id == "calories_high" }

        when (goal) {
            "LOSE_WEIGHT" -> when {
                perWeek > -0.1 -> out += CoachFinding(
                    id = "weight_stalled", area = "weight", kind = FindingKind.CONCERN, priority = 78,
                    fact = "Your weight has held at about ${fmtKg(current)} kg over the last $weeks weeks ($start kg to $end kg).",
                    meaning = when {
                        caloriesHigh -> "That fits with eating above your calorie target. Bringing the weekly average down is what will get the scale moving again."
                        caloriesOnTrack -> "Your logged calories look on target, so the gap is most likely food that isn't getting logged (cooking oil, ghee, drinks, nibbles) or a lower daily burn than estimated. Trimming about 150 to 200 kcal a day usually restarts progress."
                        else -> "To lose weight, the weekly calorie average has to sit below what you burn. $trendMeaning"
                    },
                    suggestion = if (caloriesOnTrack) "For the next few days, log everything, including oil, ghee, tea, drinks and small bites."
                    else "Aim for your calorie target today and log every meal, so we can see where the gap is."
                )
                perWeek < -onePercent -> out += CoachFinding(
                    id = "weight_fast", area = "weight", kind = FindingKind.CONCERN, priority = 70,
                    fact = "You're losing about ${fmtKg(-perWeek)} kg a week ($start kg to $end kg over $weeks weeks).",
                    meaning = "Faster than about 1% of body weight a week (${fmtKg(onePercent)} kg for you) tends to cost muscle and is hard to keep up. A steady 0.5 to 0.75 kg a week lasts.",
                    suggestion = "Eat up to your full calorie target today, with protein in every meal."
                )
                perWeek <= -0.2 -> out += CoachFinding(
                    id = "weight_on_track", area = "weight", kind = FindingKind.WIN, priority = 58,
                    fact = "You're down about ${fmtKg(-change)} kg over the last $weeks weeks, roughly ${fmtKg(-perWeek)} kg a week.$toGo",
                    meaning = "That's a healthy pace you can keep up, and the kind of loss that stays off.",
                    suggestion = "Keep the same routine today."
                )
            }
            "GAIN_MUSCLE" -> when {
                perWeek > current * 0.005 -> out += CoachFinding(
                    id = "weight_gain_fast", area = "weight", kind = FindingKind.CONCERN, priority = 60,
                    fact = "You're gaining about ${fmtKg(perWeek)} kg a week ($start kg to $end kg over $weeks weeks).",
                    meaning = "Muscle builds slowly, about 0.25 to 0.5% of body weight a week at most. Gaining faster than that is mostly fat.",
                    suggestion = "Trim your surplus a little: keep the protein, cut back on extras like sweets or fried snacks."
                )
                perWeek < 0.05 -> out += CoachFinding(
                    id = "weight_gain_stalled", area = "weight", kind = FindingKind.CONCERN, priority = 60,
                    fact = "Your weight has held at about ${fmtKg(current)} kg over the last $weeks weeks.",
                    meaning = "Building muscle needs a small, steady gain. If the scale isn't moving, your training doesn't have the extra fuel it needs.",
                    suggestion = "Add one extra snack today, like curd with nuts or a banana with peanut butter."
                )
                else -> out += CoachFinding(
                    id = "weight_gain_on_track", area = "weight", kind = FindingKind.WIN, priority = 50,
                    fact = "You're gaining about ${fmtKg(perWeek)} kg a week, a lean pace for building muscle.$toGo",
                    meaning = "Slow and steady keeps the gain mostly muscle.",
                    suggestion = "Keep the same routine today."
                )
            }
            else -> if (abs(change) >= 1.5) {
                val direction = if (change > 0) "up" else "down"
                out += CoachFinding(
                    id = "weight_drift", area = "weight", kind = FindingKind.CONCERN, priority = 50,
                    fact = "Your weight has drifted $direction about ${fmtKg(abs(change))} kg over the last $weeks weeks ($start kg to $end kg).",
                    meaning = "You're aiming to maintain, so a steady drift like this is worth correcting early while it's small.",
                    suggestion = if (change > 0) "Keep portions close to your calorie target today." else "Make sure today has three full meals."
                )
            }
        }
    }

    private fun checkTreats(
        today: LocalDate,
        byDate: Map<LocalDate, List<NutritionEntryEntity>>,
        zone: ZoneId,
        goal: String,
        hasBlankDays: Boolean,
        out: MutableList<CoachFinding>,
        checked: MutableSet<String>
    ) {
        // A "treat meal" is one meal sitting (day + meal time) with at least one treat food in it,
        // so a pizza and a beer at the same dinner count once.
        data class TreatMeal(val date: LocalDate, val categories: Set<String>)

        fun treatMeals(fromDaysAgo: Int, toDaysAgo: Int): List<TreatMeal> =
            (toDaysAgo..fromDaysAgo).flatMap { offset ->
                val date = today.minusDays(offset.toLong())
                byDate[date].orEmpty()
                    .groupBy { slotOf(it.timestamp, zone) }
                    .values.mapNotNull { meal ->
                        val cats = meal.mapNotNull { treatCategoryOf(it) }.toSet()
                        if (cats.isEmpty()) null else TreatMeal(date, cats)
                    }
            }

        val anyLogs = (0..27).any { byDate[today.minusDays(it.toLong())].orEmpty().isNotEmpty() }
        if (!anyLogs) return
        checked += "treats"

        // Weeks include today, because treats you've logged are facts even if the day isn't finished.
        val thisWeek = treatMeals(6, 0)
        val previousWeeks = listOf(treatMeals(13, 7), treatMeals(20, 14), treatMeals(27, 21))
        val minimumNote = if (hasBlankDays) " That's only what was logged, so the real number may be higher." else ""

        // The same treat turning into a habit.
        val lastTwoWeeks = treatMeals(13, 0)
        val habit = lastTwoWeeks.flatMap { meal -> meal.categories.map { it to meal.date } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.distinct().sortedDescending() }
            .filter { (_, dates) -> dates.size >= 3 }
            .maxByOrNull { it.value.size }
        if (habit != null) {
            val (category, dates) = habit
            val span = java.time.temporal.ChronoUnit.DAYS.between(dates.last(), today).toInt() + 1
            out += CoachFinding(
                id = "treats_habit", area = "treats", kind = FindingKind.CONCERN, priority = 82,
                fact = "${plural(category).replaceFirstChar { it.uppercase() }} showed up on ${dates.size} days in the last $span days (${dates.take(4).sorted().joinToString(", ") { fmt(it) }}).$minimumNote",
                meaning = "Once in a while is a treat. This often, it's becoming a habit, and habits are what move your results${goalPhrase(goal)}.",
                suggestion = "Keep ${plural(category)} to once a week and pick a lighter swap for the other times."
            )
        } else {
            // A category that turns up every single week for the last three weeks.
            val everyWeek = thisWeek.flatMap { it.categories }.distinct().firstOrNull { cat ->
                (listOf(thisWeek) + previousWeeks.take(2)).all { week -> week.any { cat in it.categories } }
            }
            if (everyWeek != null) {
                out += CoachFinding(
                    id = "treats_every_week", area = "treats", kind = FindingKind.CONCERN, priority = 55,
                    fact = "${plural(everyWeek).replaceFirstChar { it.uppercase() }} came up every week for the last 3 weeks.",
                    meaning = "Once a week is fine on its own, but it's worth noticing that it has become a fixed part of your week.",
                    suggestion = "If it's a planned weekly treat, enjoy it. If it's a default, try a swap one week."
                )
            }
        }

        if (thisWeek.size > WEEKLY_TREAT_ALLOWANCE) {
            val counts = thisWeek.flatMap { it.categories }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .joinToString(", ") { (cat, n) -> if (n > 1) "$cat ×$n" else cat }
            val prev = previousWeeks.map { it.size }
            out += CoachFinding(
                id = "treats_high", area = "treats", kind = FindingKind.CONCERN,
                priority = if (thisWeek.size >= 4) 84 else 72,
                fact = "${thisWeek.size} treat meals in the last 7 days ($counts). The three weeks before had ${prev.joinToString(", ")}.$minimumNote",
                meaning = "Up to $WEEKLY_TREAT_ALLOWANCE treat meals a week fit easily into your plan. Beyond that they start to show${goalPhrase(goal)}.",
                suggestion = "Keep the rest of this week treat-free and you're back within your $WEEKLY_TREAT_ALLOWANCE."
            )
        } else if (thisWeek.isEmpty() && previousWeeks[0].size > WEEKLY_TREAT_ALLOWANCE) {
            out += CoachFinding(
                id = "treats_down", area = "treats", kind = FindingKind.WIN, priority = 55,
                fact = "No treat meals logged in the last 7 days, down from ${previousWeeks[0].size} the week before.",
                meaning = "Cutting back like this is one of the changes that shows up fastest on the scale and in energy levels.",
                suggestion = "Plan your next treat on purpose so it feels like a reward."
            )
        }
    }

    private fun checkVegAndFruit(completeDays: List<DayLog>, out: MutableList<CoachFinding>, checked: MutableSet<String>) {
        if (completeDays.size < MIN_COMPLETE_DAYS) return
        checked += "veg"
        val withVeg = completeDays.count { day -> day.entries.any { hasVegOrFruit(it) } }
        if (withVeg * 2 < completeDays.size) {
            out += CoachFinding(
                id = "veg_low", area = "veg", kind = FindingKind.CONCERN, priority = 60,
                fact = "Vegetables or fruit showed up on only $withVeg of your last ${completeDays.size} fully logged days.",
                meaning = "Vegetables and fruit bring the fibre and vitamins that keep you full, help digestion and support recovery. Most days should have some.",
                suggestion = "Add a bowl of sabzi, a salad or a piece of fruit to one meal today."
            )
        } else if (withVeg == completeDays.size && withVeg >= 4) {
            out += CoachFinding(
                id = "veg_good", area = "veg", kind = FindingKind.WIN, priority = 35,
                fact = "Every one of your last $withVeg fully logged days had vegetables or fruit.",
                meaning = "That's a strong base for fibre, vitamins and staying full.",
                suggestion = "Keep it up today."
            )
        }
    }

    private fun checkSteps(
        today: LocalDate,
        activity: List<ActivityTelemetryEntity>,
        weightKg: Double,
        out: MutableList<CoachFinding>,
        checked: MutableSet<String>
    ) {
        val stepsByDate = activity.mapNotNull { a -> parseDate(a.date)?.let { it to a.steps } }.toMap()
        fun window(from: Int, to: Int) = (from..to).mapNotNull { stepsByDate[today.minusDays(it.toLong())] }.filter { it > 0 }

        val recent = window(1, 7)
        if (recent.size < 4) return
        checked += "steps"
        val recentAvg = recent.average()
        val baseline = window(8, 35)
        val meaning = "Most of the health benefit of walking comes up to about 7,000 to 10,000 steps a day, and it's one of the biggest drivers of calories burned outside workouts."

        if (baseline.size >= 7) {
            val baseAvg = baseline.average()
            if (baseAvg >= 3000 && recentAvg < baseAvg * 0.75) {
                val kcal = ((baseAvg - recentAvg) * 0.04 * weightKg / 70).roundToInt()
                out += CoachFinding(
                    id = "steps_down", area = "steps", kind = FindingKind.CONCERN, priority = 50,
                    fact = "Steps averaged ${num(recentAvg.roundToInt())} a day this week vs your usual ${num(baseAvg.roundToInt())}.",
                    meaning = "That drop is roughly $kcal kcal a day less burned. $meaning",
                    suggestion = "Fit in a 20-minute walk today, for example after lunch or dinner."
                )
                return
            }
            if (recentAvg > baseAvg * 1.2 && recentAvg >= 5000) {
                out += CoachFinding(
                    id = "steps_up", area = "steps", kind = FindingKind.WIN, priority = 42,
                    fact = "Steps averaged ${num(recentAvg.roundToInt())} a day this week, up from your usual ${num(baseAvg.roundToInt())}.",
                    meaning = meaning,
                    suggestion = "Keep the walks going today."
                )
                return
            }
        } else if (recentAvg < 5000 && recent.size >= 5) {
            out += CoachFinding(
                id = "steps_low", area = "steps", kind = FindingKind.CONCERN, priority = 45,
                fact = "Steps averaged ${num(recentAvg.roundToInt())} a day this week.",
                meaning = meaning,
                suggestion = "Add a 15 to 20-minute walk today."
            )
            return
        }

        // Weekends consistently much quieter than weekdays.
        val month = (1..28).mapNotNull { offset ->
            val date = today.minusDays(offset.toLong())
            stepsByDate[date]?.takeIf { it > 0 }?.let { date to it }
        }
        val weekend = month.filter { it.first.dayOfWeek == DayOfWeek.SATURDAY || it.first.dayOfWeek == DayOfWeek.SUNDAY }.map { it.second }
        val weekday = month.filter { it.first.dayOfWeek != DayOfWeek.SATURDAY && it.first.dayOfWeek != DayOfWeek.SUNDAY }.map { it.second }
        if (weekend.size >= 3 && weekday.size >= 8 && weekend.average() < weekday.average() * 0.6) {
            out += CoachFinding(
                id = "steps_weekend", area = "steps", kind = FindingKind.CONCERN, priority = 35,
                fact = "Weekends average ${num(weekend.average().roundToInt())} steps vs ${num(weekday.average().roundToInt())} on weekdays.",
                meaning = meaning,
                suggestion = "Plan one active thing for the weekend, like a long walk or a game."
            )
        }
    }

    private fun checkSleep(today: LocalDate, sleep: List<SleepTelemetryEntity>, out: MutableList<CoachFinding>, checked: MutableSet<String>) {
        val byDate = sleep.mapNotNull { s -> parseDate(s.date)?.let { it to s.sleepDurationSeconds } }.toMap()
        // Sleep is stored against the morning it ended, so last night belongs to today.
        val nights = (0..6).mapNotNull { byDate[today.minusDays(it.toLong())] }.filter { it > 0 }.map { it / 3600.0 }
        if (nights.size < 4) return
        checked += "sleep"
        val avg = nights.average()
        val meaning = "7 to 9 hours is the healthy range for adults. Regularly under about 6.5 hours makes hunger and cravings stronger and slows recovery from training."
        if (avg < 6.5) {
            out += CoachFinding(
                id = "sleep_low", area = "sleep", kind = FindingKind.CONCERN, priority = 65,
                fact = "You've averaged ${"%.1f".format(Locale.US, avg)} hours of sleep over the last ${nights.size} nights.",
                meaning = meaning,
                suggestion = "Aim to be in bed 30 minutes earlier tonight, and keep screens away for the last half hour."
            )
        } else if (avg >= 7.0 && nights.size >= 5) {
            out += CoachFinding(
                id = "sleep_good", area = "sleep", kind = FindingKind.WIN, priority = 30,
                fact = "You've averaged ${"%.1f".format(Locale.US, avg)} hours of sleep over the last ${nights.size} nights.",
                meaning = meaning,
                suggestion = "Keep the same bedtime tonight."
            )
        }
    }

    private fun checkWorkouts(
        today: LocalDate,
        workouts: List<WorkoutEntryEntity>,
        zone: ZoneId,
        out: MutableList<CoachFinding>,
        checked: MutableSet<String>
    ) {
        if (workouts.isEmpty()) return
        val dates = workouts.map { dateOf(it.timestamp, zone) }
        fun sessions(from: Int, to: Int): Int {
            val range = (to..from).map { today.minusDays(it.toLong()) }.toSet()
            return dates.filter { it in range }.distinct().size
        }
        val thisWeek = sessions(6, 0)
        val lastWeek = sessions(13, 7)
        val usual = (sessions(20, 14) + sessions(27, 21) + sessions(34, 28)) / 3.0
        if (usual < 1.0) return
        checked += "workouts"
        if (usual >= 2.0 && thisWeek < usual * 0.5 && lastWeek < usual * 0.5) {
            out += CoachFinding(
                id = "workouts_down", area = "workouts", kind = FindingKind.CONCERN, priority = 55,
                fact = "You've trained on $thisWeek days this week and $lastWeek last week, down from about ${usual.roundToInt()} a week before that.",
                meaning = "Two quieter weeks in a row is when a break starts turning into a habit. Even short sessions keep the routine alive.",
                suggestion = "Do a short 20-minute session today, it doesn't need to be a full workout."
            )
        } else if (thisWeek >= usual + 1) {
            out += CoachFinding(
                id = "workouts_up", area = "workouts", kind = FindingKind.WIN, priority = 40,
                fact = "You've trained on $thisWeek days this week, more than your usual ${usual.roundToInt()}.",
                meaning = "Consistency like this is what builds results over months.",
                suggestion = "Balance the extra training with good sleep and protein today."
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Meaning of the numbers
    // ---------------------------------------------------------------------------------------

    fun proteinMeaning(target: Double, weightKg: Double, goal: String): String {
        val perKg = if (weightKg > 0) target / weightKg else 0.0
        val why = when (goal) {
            "LOSE_WEIGHT" -> "while eating less, this is what keeps the weight you lose coming from fat rather than muscle, and it keeps you fuller"
            "GAIN_MUSCLE" -> "this gives your training the building blocks to add muscle"
            else -> "this keeps your muscles and recovery well supported"
        }
        return "Your ${target.roundToInt()}g target is about ${"%.1f".format(Locale.US, perKg)}g per kg of body weight; $why."
    }

    fun targetMeanings(targets: MacroTargets, weightKg: Double, goal: String, maintenanceCalories: Double?): List<String> {
        val lines = mutableListOf(
            proteinMeaning(targets.proteinG, weightKg, goal),
            "Protein guide: 3 eggs ≈ 18g, 100g paneer ≈ 18g, 100g chicken breast ≈ 31g, 1 cup cooked dal ≈ 15g, 1 cup curd ≈ 10g."
        )
        val target = targets.calories.roundToInt()
        if (maintenanceCalories != null) {
            val gap = (maintenanceCalories - targets.calories).roundToInt()
            lines += when {
                gap > 100 -> "Calorie target ${num(target)} kcal is about ${num(gap)} kcal below what you burn (~${num(maintenanceCalories.roundToInt())}), roughly ${"%.1f".format(Locale.US, gap * 7 / 7700.0)} kg of fat loss a week if the weekly average stays on target."
                gap < -100 -> "Calorie target ${num(target)} kcal is about ${num(-gap)} kcal above what you burn (~${num(maintenanceCalories.roundToInt())}), a small surplus for building muscle."
                else -> "Calorie target ${num(target)} kcal is about what you burn, so your weight should hold steady."
            }
        } else {
            lines += "Calorie target ${num(target)} kcal (set by you)."
        }
        lines += "Treat meals: up to $WEEKLY_TREAT_ALLOWANCE a week fit easily into the plan."
        return lines
    }

    // ---------------------------------------------------------------------------------------
    // Food matching
    // ---------------------------------------------------------------------------------------

    private fun words(pattern: String) = Regex("\\b($pattern)\\b", RegexOption.IGNORE_CASE)

    /** Treat foods by category. Matching is on whole words so "eggplant" never reads as "egg". */
    private val treatCategories: List<Pair<String, Regex>> = listOf(
        "burger" to words("burgers?"),
        "pizza" to words("pizzas?"),
        "fries" to words("(french )?fries|wedges|hash ?browns?|tater tots?"),
        "fried snack" to words("samosas?|pakoras?|pakodas?|kachoris?|vadas?|bhature|bhaturas?|puris?|pooris?|bondas?"),
        "fried chicken" to words("fried chicken|nuggets?|kfc|popcorn chicken|chicken tenders?"),
        "dessert" to words("cakes?|pastry|pastries|donuts?|doughnuts?|brownies?|ice ?creams?|sundaes?|cookies?|gulab jamuns?|jalebis?|rasgullas?|rasmalai|halwa|ladoos?|laddoos?|barfi|burfi|kheer|mithai|sweets|candy|chocolates?|desserts?|muffins?"),
        "sugary drink" to words("soda|coke|pepsi|sprite|fanta|thums up|cold ?drinks?|soft drinks?|milkshakes?|frappuccinos?|energy drinks?"),
        "alcohol" to words("beers?|wines?|whiske?y|vodka|rum|gin|cocktails?|tequila|alcohol|pints?"),
        "instant noodles" to words("maggi|ramen|instant noodles|cup noodles"),
        "packaged snack" to words("chips|crisps|nachos|doritos|kurkure|lays|namkeen|bhujia")
    )

    private val notATreat = Regex("\\b(protein|sugar[- ]free|diet|zero|dark chocolate|sweet potato)\\b", RegexOption.IGNORE_CASE)

    fun treatCategory(foodText: String): String? {
        if (notATreat.containsMatchIn(foodText)) return null
        return treatCategories.firstOrNull { it.second.containsMatchIn(foodText) }?.first
    }

    private val vegOrFruit = words(
        "salads?|sabzi|sabji|subzi|vegetables?|veggies?|spinach|palak|methi|bhindi|okra|gobi|cauliflower|broccoli|cabbage|" +
            "carrots?|beans|peas|matar|tomato(es)?|cucumbers?|kakdi|lauki|tinda|karela|baingan|eggplant|brinjal|capsicum|peppers?|" +
            "mushrooms?|zucchini|kale|lettuce|beetroot|pumpkin|sambar|avial|poriyal|thoran|kootu|stir ?fry|soup|" +
            "fruits?|apples?|bananas?|oranges?|mangoe?s?|papaya|guavas?|grapes?|berries|strawberr(y|ies)|blueberr(y|ies)|" +
            "watermelon|melon|pineapple|pomegranate|kiwis?|pears?|peach(es)?|plums?|cherr(y|ies)|chikoo|chiku|litchis?|lychees?|mosambi"
    )

    fun isVegOrFruit(foodText: String): Boolean = vegOrFruit.containsMatchIn(foodText)

    /** Treat category of a logged meal, using the AI's tags when the meal has them. */
    fun treatCategoryOf(entry: NutritionEntryEntity): String? {
        val tags = MealTags.decode(entry.qualityTags) ?: return treatCategory(entry.foodText)
        if (!tags.treat) return null
        return treatCategory(entry.foodText) ?: tags.foodType.ifBlank { "treat" }
    }

    fun hasVegOrFruit(entry: NutritionEntryEntity): Boolean =
        MealTags.decode(entry.qualityTags)?.let { it.vegFruitServings >= 0.5 } ?: isVegOrFruit(entry.foodText)

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    private fun fmt(date: LocalDate): String = date.format(dayFormat)

    private fun dateOf(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun fmtKg(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun num(value: Int): String = String.format(Locale.US, "%,d", value)

    private fun plural(category: String): String = when (category) {
        "fries", "dessert", "alcohol", "instant noodles" -> category
        "fried chicken" -> category
        else -> "${category}s"
    }

    private fun goalPhrase(goal: String): String = when (goal) {
        "LOSE_WEIGHT" -> " on the scale"
        "GAIN_MUSCLE" -> " in how lean your gains are"
        else -> ""
    }
}
