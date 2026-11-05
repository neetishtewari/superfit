package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One workout as Insights needs it, from a Health Connect import or a voice log. */
data class ActivityFact(
    val title: String,
    val minutes: Long,
    val distanceKm: Double?,
    val kcal: Double,
    val paceSecondsPerKm: Long?,
    val isRun: Boolean,
    /** Average heart rate, when a watch or phone recorded it. */
    val avgHeartRate: Long? = null,
    /** Hour of day the workout started (0-23); null for voice logs, whose time is when they were said. */
    val startHour: Int? = null,
    /** Total reps of a voice-logged workout, 0 when none were given. */
    val reps: Int = 0
) {
    val isWalk: Boolean get() = title == "Walk"

    companion object {
        fun of(view: WorkoutView, zone: ZoneId = ZoneId.systemDefault()): ActivityFact = ActivityFact(
            title = view.title,
            minutes = view.minutes,
            distanceKm = view.distanceKm,
            kcal = view.exerciseKcal ?: 0.0,
            paceSecondsPerKm = view.paceSecondsPerKm,
            isRun = view.title == "Run",
            avgHeartRate = view.avgHeartRate?.takeIf { it in 40..220 },
            startHour = runCatching { Instant.ofEpochMilli(view.startTime).atZone(zone).hour }.getOrNull()
        )
    }
}

/**
 * One day of logs, the input to Insights, Journey and "vs your usual". A day counts as logged
 * (its eating numbers are trusted) only with 2+ meals; blank days are unknown, never "ate nothing".
 */
data class DayTotals(
    val date: LocalDate,
    val kcalEaten: Double,
    val kcalTarget: Double,
    val meals: Int,
    val proteinG: Double,
    val proteinTargetG: Double,
    val steps: Int,
    val workouts: List<ActivityFact>,
    /** Average of the AI's 1-10 meal ratings that day; null when no meal was rated. */
    val mealRating: Double? = null
) {
    val logged: Boolean get() = meals >= 2
    val net: Double get() = kcalEaten - kcalTarget
    val proteinHit: Boolean get() = logged && proteinTargetG > 0 && proteinG >= proteinTargetG * 0.9

    /** Share of the protein goal eaten, capped at 1 so one big day can't hide short ones; null if not counted. */
    val proteinShare: Double? get() = if (logged && proteinTargetG > 0) (proteinG / proteinTargetG).coerceAtMost(1.0) else null
    val moveMinutes: Long get() = workouts.sumOf { it.minutes }
    val workoutKcal: Double get() = workouts.sumOf { it.kcal }
    val workoutKm: Double get() = workouts.sumOf { it.distanceKm ?: 0.0 }
    val active: Boolean get() = workouts.isNotEmpty() || steps >= ACTIVE_STEPS

    companion object {
        const val ACTIVE_STEPS = 7500
    }
}

data class WeekStats(
    val moveMinutes: Long,
    val distanceKm: Double,
    val workouts: Int,
    val activeDays: Int,
    val loggedDays: Int,
    val netKcal: Double,
    val proteinDaysHit: Int,
    /** Average share of the protein goal over logged days, 0-100; null when nothing was logged. */
    val proteinPct: Int?
)

data class WeekReport(
    val from: LocalDate,
    val to: LocalDate,
    val thisWeek: WeekStats,
    val lastWeek: WeekStats,
    val verdict: String,
    val working: List<Insight>,
    val watch: List<Insight>,
    /** Eaten minus target for each of the 7 days; null on days that aren't fully logged. */
    val dailyNet: List<Pair<LocalDate, Double?>>,
    /** The foods that added the most calories over the 7 days; null with too little logged. */
    val calories: CalorieSources? = null
)

/**
 * The Insights screen's numbers and findings, worked out on the phone from the last 7 full days
 * (today is left out because it isn't over). No AI.
 */
object InsightsEngine {

    fun weekReport(
        days: Map<LocalDate, DayTotals>,
        today: LocalDate,
        loseGoal: Boolean = true,
        meals: List<NutritionEntryEntity> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        body: BodyChange? = null
    ): WeekReport {
        val to = today.minusDays(1)
        val from = to.minusDays(6)
        val week = range(days, from, to)
        val prev = range(days, from.minusDays(7), from.minusDays(1))
        val thisWeek = stats(week)
        val lastWeek = stats(prev)

        val weekMeals = meals.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().let { d -> !d.isBefore(from) && !d.isAfter(to) } }
        val calories = if (thisWeek.loggedDays >= 3) CalorieSourcesEngine.build(weekMeals) else null
        val treat = calories?.let { treatFinding(it) }
        // Smart scale: only when it measures body fat, muscle or resting burn and one of them moved.
        val bodyLine = body?.takeIf { it.notable }?.let { BodyTrend.finding(it, loseGoal) }
        val bodyWatch = bodyLine?.takeIf { BodyTrend.isWatch(it) }

        // The treat line, the body line and the activity patterns join the usual findings; good news is never crowded out.
        val extras = listOfNotNull(treat?.takeIf { it.id != "treat_top" }, bodyLine?.takeIf { bodyWatch == null }) + activityFindings(days, week, prev, today)
        val working = workingFindings(days, week, thisWeek, lastWeek, today, loseGoal).take(2) + extras.take(2)
        val watch = (listOfNotNull(treat?.takeIf { it.id == "treat_top" }, bodyWatch) + watchFindings(days, week, thisWeek, today, loseGoal)).take(2)

        return WeekReport(
            from = from,
            to = to,
            thisWeek = thisWeek,
            lastWeek = lastWeek,
            verdict = verdict(working.firstOrNull(), watch.firstOrNull()),
            working = working,
            watch = watch,
            dailyNet = week.map { it.date to (if (it.logged) it.net else null) },
            calories = calories?.takeIf { it.top.isNotEmpty() }
        )
    }

    /**
     * The biggest treat against the top 5 calorie foods: good news when it stayed out of the top 5,
     * an honest heads-up with the swap idea when it's in. Null when there's nothing to say.
     */
    fun treatFinding(c: CalorieSources, period: String = "week"): Insight? {
        val b = c.biggestTreat
        if (b == null) {
            return if (c.surelyNoTreats) Insight("treat_none", "No treat foods this $period", "Everything you logged was everyday food.", "treats stayed off the plate") else null
        }
        val name = b.name
        val rank = c.treatRank
            ?: return Insight(
                "treat_small",
                "Treats stayed small",
                "Your biggest treat, ${name.lowercase(Locale.getDefault())} (${kcal(b.treatKcal.toDouble())} kcal), wasn't in your top 5 calorie foods.",
                "treats stayed small"
            )
        val detail = buildString {
            append("${kcal(b.treatKcal.toDouble())} kcal in ${b.times} ${if (b.times == 1) "sitting" else "sittings"}")
            c.outweighs?.let { append(", more than all your ${it.name.lowercase(Locale.getDefault())}") }
            append(".")
            c.swap?.let { sw ->
                append(" Swap idea: ${sw.alternative.lowercase(Locale.getDefault())}")
                if (sw.kcalSaved >= 30) append(", about ${sw.kcalSaved} kcal less each time")
                append(".")
            }
        }
        return Insight("treat_top", "$name is your #$rank calorie food", detail, "${name.lowercase(Locale.getDefault())} made your top 5 calorie foods")
    }

    /** Fitter heart, rep totals and better meals on move days, when the data clearly shows them. */
    private fun activityFindings(days: Map<LocalDate, DayTotals>, week: List<DayTotals>, prev: List<DayTotals>, today: LocalDate): List<Insight> = buildList {
        val first = week.first().date
        val before = range(days, first.minusDays(28), first.minusDays(1))
        ActivityPatterns.heart(week.flatMap { it.workouts }, before.flatMap { it.workouts })?.let { h ->
            add(
                Insight(
                    "heart",
                    "Fitter heart",
                    "${cap(h.activity)}s at ${pace(h.paceNow)} /km (was ${pace(h.pacePrev)}) with your heart ${h.bpmDrop} beats a minute lower than the 4 weeks before.",
                    "your heart is getting fitter"
                )
            )
        }
        ActivityPatterns.reps(week.flatMap { it.workouts }, prev.flatMap { it.workouts })?.let { r ->
            if (r.prev == 0 || r.total > r.prev) {
                val title = r.top?.let { "${String.format(Locale.US, "%,d", it.second)} ${it.first}" } ?: "${String.format(Locale.US, "%,d", r.total)} reps"
                val detail = when {
                    r.prev > 0 && r.top != null -> "${String.format(Locale.US, "%,d", r.total)} reps in all, up from ${String.format(Locale.US, "%,d", r.prev)} last week."
                    r.prev > 0 -> "Up from ${String.format(Locale.US, "%,d", r.prev)} last week."
                    else -> "Logged by voice this week."
                }
                add(Insight("reps", title, detail, "you put in ${String.format(Locale.US, "%,d", r.total)} reps"))
            }
        }
        ActivityPatterns.moveDayPlates(range(days, today.minusDays(28), today.minusDays(1)))?.let { m ->
            val protein = if (m.activeProteinPct != null && m.restProteinPct != null && m.activeProteinPct - m.restProteinPct >= 10) {
                " Protein ${m.activeProteinPct}% of goal vs ${m.restProteinPct}%."
            } else {
                ""
            }
            add(Insight("plates", "Better meals on move days", "Meals rated ${m.activeRating} on active days vs ${m.restRating} on rest days, last 4 weeks.$protein", "you ate better on days you moved"))
        }
    }

    fun stats(days: List<DayTotals>): WeekStats = WeekStats(
        moveMinutes = days.sumOf { it.moveMinutes },
        distanceKm = days.sumOf { it.workoutKm },
        workouts = days.sumOf { it.workouts.size },
        activeDays = days.count { it.active },
        loggedDays = days.count { it.logged },
        netKcal = days.filter { it.logged }.sumOf { it.net },
        proteinDaysHit = days.count { it.proteinHit },
        proteinPct = days.mapNotNull { it.proteinShare }.takeIf { it.isNotEmpty() }?.let { (it.average() * 100).roundToInt() }
    )

    /** Every day from [from] to [to], with empty days filled in. */
    fun range(days: Map<LocalDate, DayTotals>, from: LocalDate, to: LocalDate): List<DayTotals> {
        val out = mutableListOf<DayTotals>()
        var d = from
        while (!d.isAfter(to)) {
            out += days[d] ?: DayTotals(d, 0.0, 0.0, 0, 0.0, 0.0, 0, emptyList())
            d = d.plusDays(1)
        }
        return out
    }

    private fun workingFindings(
        days: Map<LocalDate, DayTotals>,
        week: List<DayTotals>,
        s: WeekStats,
        last: WeekStats,
        today: LocalDate,
        loseGoal: Boolean
    ): List<Insight> = buildList {
        if (s.activeDays >= 5) {
            val month = range(days, today.minusDays(28), today.minusDays(1)).count { it.active }
            add(Insight("active", "Active ${s.activeDays} of 7 days", "$month of the last 28 days.", "you were active ${s.activeDays} of 7 days"))
        }
        paceChange(days, today)?.let { (now, faster) ->
            if (faster >= 10) {
                add(
                    Insight("pace", "Runs are getting faster", "Average pace ${pace(now)} /km, $faster s faster than the 4 weeks before.", "your runs are getting faster")
                )
            }
        }
        if (s.loggedDays >= 4 && (s.proteinPct ?: 0) >= 90) {
            add(Insight("protein", "Protein on target", "Averaging ${s.proteinPct}% of your goal, reached on ${s.proteinDaysHit} of ${s.loggedDays} logged days.", "you got close to your protein goal"))
        }
        if (loseGoal && s.loggedDays >= 4 && s.netKcal <= -1000) {
            add(Insight("deficit", "Steady calorie deficit", "About ${kcal(-s.netKcal)} kcal under target across logged days.", "you kept a steady deficit"))
        }
        if (!loseGoal && s.loggedDays >= 4 && s.netKcal >= 0 && (s.proteinPct ?: 0) >= 80) {
            add(Insight("fuel", "Eating enough to build", "At or above target on logged days, with protein at ${s.proteinPct}% of goal.", "you ate enough to build"))
        }
        val moreMin = s.moveMinutes - last.moveMinutes
        if (moreMin >= 60 && s.moveMinutes >= last.moveMinutes * 1.25) {
            add(Insight("move", "More movement than last week", "+${minutes(moreMin)} of workouts vs the week before.", "you moved more than last week"))
        }
    }

    private fun watchFindings(
        days: Map<LocalDate, DayTotals>,
        week: List<DayTotals>,
        s: WeekStats,
        today: LocalDate,
        loseGoal: Boolean
    ): List<Insight> = buildList {
        if (s.loggedDays < 3) {
            add(Insight("logging", "Only ${s.loggedDays} of 7 days fully logged", "Log at least two meals a day so these insights stay accurate.", if (s.loggedDays == 1) "only 1 day was fully logged" else "only ${s.loggedDays} days were fully logged"))
        }
        if (loseGoal) {
            weekendSplit(week)?.let { (weekendAvg, share) ->
                if (weekendAvg >= 300 && share >= 0.3) {
                    add(
                        Insight(
                            "weekend",
                            "Weekends: ${kcal(weekendAvg)} kcal over target",
                            "Saturday and Sunday undid ${(share.coerceAtMost(1.0) * 100).roundToInt()}% of the weekday deficit.",
                            "weekend eating cancelled ${if (share >= 0.75) "most" else "much"} of the deficit"
                        )
                    )
                }
            }
        }
        underFuelled(week)?.let { add(it) }
        val earlier = (1..3).map { w -> stats(range(days, today.minusDays(7L * w + 7), today.minusDays(7L * w + 1))).workouts }
        val usual = earlier.average()
        if (usual >= 2 && s.workouts <= usual * 0.6) {
            add(Insight("fewer", "Fewer workouts", "${s.workouts} this week, down from about ${usual.roundToInt()} a week.", "you did fewer workouts"))
        }
        if (s.loggedDays >= 3 && (s.proteinPct ?: 100) < 70) {
            add(Insight("protein_low", "Protein running short", "Averaging ${s.proteinPct}% of your goal on logged days.", "protein ran short"))
        }
        if (loseGoal && s.loggedDays >= 4 && s.netKcal > 0 && none { it.id == "weekend" }) {
            add(Insight("over", "Above target this week", "About ${kcal(s.netKcal)} kcal over across logged days.", "you ate above target"))
        }
    }

    /** Weekend average over target, and how much of the weekday deficit it cancelled. */
    fun weekendSplit(days: List<DayTotals>): Pair<Double, Double>? {
        val logged = days.filter { it.logged }
        val weekend = logged.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val weekday = logged - weekend.toSet()
        if (weekend.isEmpty() || weekday.isEmpty()) return null
        val weekendSurplus = weekend.sumOf { it.net }
        val weekdayDeficit = -weekday.sumOf { it.net }
        if (weekendSurplus <= 0 || weekdayDeficit <= 0) return null
        return weekendSurplus / weekend.size to weekendSurplus / weekdayDeficit
    }

    /** A big workout day (a 5 km+ run or 400+ kcal) with protein well short. */
    private fun underFuelled(week: List<DayTotals>): Insight? {
        val day = week.filter { d ->
            d.logged && d.proteinTargetG > 0 && d.proteinG < d.proteinTargetG * 0.7 &&
                d.workouts.any { (it.isRun && (it.distanceKm ?: 0.0) >= 5.0) || it.kcal >= 400 }
        }.maxByOrNull { d -> d.workoutKcal } ?: return null
        val w = day.workouts.maxByOrNull { it.kcal }!!
        val what = w.distanceKm?.let { String.format(Locale.US, "%s %.1f km", w.title.lowercase(Locale.getDefault()), it) }
            ?: w.title.lowercase(Locale.getDefault())
        val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return Insight(
            "fuel_low",
            "Under-fuelled after a big workout",
            "$dayName's $what: ${day.proteinG.roundToInt()} g protein against ${day.proteinTargetG.roundToInt()} g.",
            "recovery meals fell short"
        )
    }

    /** Average pace of 3 km+ runs in the last 4 weeks, and seconds per km faster than the 4 weeks before. */
    fun paceChange(days: Map<LocalDate, DayTotals>, today: LocalDate): Pair<Long, Long>? {
        fun avgPace(from: LocalDate, to: LocalDate): Long? {
            val runs = range(days, from, to).flatMap { it.workouts }
                .filter { it.isRun && (it.distanceKm ?: 0.0) >= 3.0 && it.paceSecondsPerKm != null }
            if (runs.size < 2) return null
            val km = runs.sumOf { it.distanceKm!! }
            return (runs.sumOf { it.paceSecondsPerKm!! * it.distanceKm!! } / km).roundToLong()
        }
        val now = avgPace(today.minusDays(28), today.minusDays(1)) ?: return null
        val before = avgPace(today.minusDays(56), today.minusDays(29)) ?: return null
        return now to (before - now)
    }

    private fun verdict(working: Insight?, watch: Insight?): String = when {
        working != null && watch != null -> cap("${working.phrase}, but ${watch.phrase}.")
        working != null -> cap("${working.phrase}. Keep it going.")
        watch != null -> cap("${watch.phrase}.")
        else -> "A steady week. Keep logging to see more here."
    }

    private fun cap(s: String) = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    fun pace(seconds: Long) = "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)

    fun kcal(v: Double) = String.format(Locale.US, "%,d", abs(v).roundToInt())

    fun minutes(m: Long) = if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)} min" else "$m min"
}

/** "Your usual" for today: the same weekday's average over the last 4 weeks. */
data class UsualDay(val steps: Int, val distanceKm: Double, val moveMinutes: Long, val workoutKcal: Double, val samples: Int) {
    companion object {
        /** Null until there are at least 2 earlier same-weekday days with steps. */
        fun forDay(days: Map<LocalDate, DayTotals>, today: LocalDate): UsualDay? {
            val same = (1..4).mapNotNull { days[today.minusWeeks(it.toLong())] }.filter { it.steps > 0 || it.workouts.isNotEmpty() }
            if (same.size < 2) return null
            return UsualDay(
                steps = same.map { it.steps }.average().roundToInt(),
                distanceKm = same.map { it.steps * STEP_KM }.average(),
                moveMinutes = same.map { it.moveMinutes }.average().roundToLong(),
                workoutKcal = same.map { it.workoutKcal }.average(),
                samples = same.size
            )
        }

        /** The same rough stride Home already uses for "estimated distance". */
        const val STEP_KM = 0.00075
    }
}

/** Today's activity next to the same weekday's usual, for the Home tiles and the coach. */
data class TodayVsUsual(
    val steps: Int,
    val distanceKm: Double,
    val moveMinutes: Long,
    val workoutKcal: Double,
    val usual: UsualDay?,
    val weekday: String
) {
    /** A short line for Home, or null when there's nothing worth saying yet. */
    fun coachLine(hour: Int): String? {
        val u = usual ?: return null
        val ahead = buildList {
            if (u.steps > 0 && steps >= u.steps) add("steps")
            if (u.moveMinutes > 0 && moveMinutes >= u.moveMinutes) add("move time")
        }
        return when {
            ahead.size == 2 -> "Already past your usual $weekday on steps and move time."
            ahead.size == 1 -> "Already past your usual $weekday on ${ahead[0]}."
            // Late in the day only, so a quiet morning never reads as falling behind.
            hour >= 18 && u.steps > 0 && steps < u.steps * 0.7 ->
                "About ${String.format(Locale.US, "%,d", u.steps - steps)} steps short of a usual $weekday. A short walk would close most of it."
            else -> null
        }
    }

    /** One line for the coach chat. */
    fun chatLine(): String {
        val u = usual ?: return "Today's activity: $steps steps, $moveMinutes min of workouts, ~${workoutKcal.roundToInt()} kcal in workouts (not enough history yet for a usual $weekday)."
        return "Today vs a usual $weekday (average of the last ${u.samples} ${weekday}s; today is still in progress): " +
            "steps $steps vs ${u.steps}, workout time $moveMinutes vs ${u.moveMinutes} min, " +
            "workout kcal ${workoutKcal.roundToInt()} vs ${u.workoutKcal.roundToInt()}."
    }

    companion object {
        fun of(today: LocalDate, steps: Int, workouts: List<ActivityFact>, usual: UsualDay?) = TodayVsUsual(
            steps = steps,
            distanceKm = steps * UsualDay.STEP_KM,
            moveMinutes = workouts.sumOf { it.minutes },
            workoutKcal = workouts.sumOf { it.kcal },
            usual = usual,
            weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        )
    }
}
