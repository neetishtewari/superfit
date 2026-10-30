package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One fact on the "What moved the needle" card, e.g. "Protein on target" / "19 of 24 logged days". */
data class RecapFact(val title: String, val detail: String)

/** Something missing that would make next month's recap (and the app) better. */
enum class SetupItem { HEALTH_CONNECT, MIC, WIDGET, LOG_DAILY }

/**
 * One story card of the monthly recap. The app works out every number here; the AI only writes
 * the words around them (see [RecapWriter]). A card with nothing to say is never made.
 */
sealed interface RecapCard {
    val id: String

    /** Goal progress: [monthKg] towards the goal this month (negative = the wrong way), null without weigh-ins. */
    data class Goal(val monthKg: Double?, val kgDone: Double, val kgTotal: Double, val eta: LocalDate?, val reached: Boolean, val lose: Boolean) : RecapCard {
        override val id = "goal"
    }

    data class Needle(val helped: List<RecapFact>, val held: List<RecapFact>) : RecapCard {
        override val id = "needle"
    }

    /** The most-logged food. [verdict] is how good a choice it usually was, when the AI rated those meals. */
    data class OnRepeat(val food: String, val times: Int, val verdict: FoodVerdict?) : RecapCard {
        override val id = "repeat"
    }

    /** Average share of the protein goal (0-100) overall, Mon-Fri and Sat-Sun; carbs as % of calories; fibre a day. */
    data class Macros(val proteinPct: Int, val weekdayProteinPct: Int?, val weekendProteinPct: Int?, val carbsPct: Int?, val fibreG: Int?) : RecapCard {
        override val id = "macros"
    }

    data class Choices(val good: List<String>, val rethink: List<String>) : RecapCard {
        override val id = "choices"
    }

    /** Average pace in seconds per km for [activity] ("run" or "walk"), this month and last. */
    data class Faster(val activity: String, val paceNow: Long, val pacePrev: Long) : RecapCard {
        override val id = "faster"
    }

    /** [activeDays] are the days of the month with a workout or 7,500+ steps. */
    data class Moving(val km: Double, val avgSteps: Int, val activeDays: Set<Int>, val daysInMonth: Int, val prevActiveDays: Int?) : RecapCard {
        override val id = "moving"
    }

    data class Strength(val sessions: Int, val prevSessions: Int, val km: Double) : RecapCard {
        override val id = "strength"
    }

    /** Not shareable: only what's missing, each with a one-tap fix in the app. */
    data class Setup(val items: List<SetupItem>, val loggedDays: Int, val daysInMonth: Int) : RecapCard {
        override val id = "setup"
    }
}

data class MonthRecap(val month: YearMonth, val loggedDays: Int, val cards: List<RecapCard>) {
    /** Fewer than 10 fully logged days: a shorter recap that leans on Health Connect. */
    val partial: Boolean get() = loggedDays < MonthlyRecap.FULL_DAYS
}

/** What the user's setup is missing, read on the phone when the recap opens. */
data class RecapSetup(val healthConnect: Boolean, val mic: Boolean, val widget: Boolean)

/**
 * Builds a month's recap from the logs, on the phone. Cards are chosen from what happened: a card
 * with no data is left out, so there is no fixed template. Blank days are unknown, never "ate less".
 */
object MonthlyRecap {

    const val FULL_DAYS = 10
    private const val MIN_DAYS = 3
    private const val MEAL_DAYS = 5

    /** Story order: goal first, then meals, then activity; "Get more from Superfit" always last. */
    val ORDER = listOf("goal", "needle", "repeat", "macros", "choices", "faster", "moving", "strength", "setup")

    fun build(
        month: YearMonth,
        days: Map<LocalDate, DayTotals>,
        meals: List<NutritionEntryEntity>,
        strengthDates: List<LocalDate>,
        weighIns: List<Pair<LocalDate, Double>>,
        journey: JourneyForecast?,
        goalSet: Boolean,
        setup: RecapSetup,
        zone: ZoneId = ZoneId.systemDefault()
    ): MonthRecap? {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val monthDays = InsightsEngine.range(days, first, last)
        val prev = InsightsEngine.range(days, first.minusMonths(1), first.minusDays(1))
        val logged = monthDays.filter { it.logged }
        val active = monthDays.filter { it.active }
        if (logged.size < MIN_DAYS && active.size < MIN_DAYS) return null

        val monthMeals = meals.filter { dateOf(it.timestamp, zone).let { d -> !d.isBefore(first) && !d.isAfter(last) } }
        val loggedDates = logged.map { it.date }.toSet()
        val lose = journey?.let { it.targetKg < it.startKg } ?: true
        val strengthNow = strengthDates.count { !it.isBefore(first) && !it.isAfter(last) }
        val strengthPrev = strengthDates.count { !it.isBefore(first.minusMonths(1)) && it.isBefore(first) }

        val cards = buildList {
            if (goalSet && journey != null) add(goal(journey, weighIns, first, last))
            needle(monthDays, prev, lose)?.let { add(it) }
            if (logged.size >= MEAL_DAYS) {
                onRepeat(monthMeals)?.let { add(it) }
                macros(logged, monthMeals.filter { dateOf(it.timestamp, zone) in loggedDates })?.let { add(it) }
                choices(monthMeals)?.let { add(it) }
            }
            faster(monthDays, prev)?.let { add(it) }
            moving(monthDays, prev, month)?.let { add(it) }
            if (strengthNow > 0 || active.isNotEmpty()) add(RecapCard.Strength(strengthNow, strengthPrev, monthDays.sumOf { it.workoutKm }))
            setup(setup, logged.size, month.lengthOfMonth())?.let { add(it) }
        }
        return MonthRecap(month, logged.size, cards.sortedBy { ORDER.indexOf(it.id) })
    }

    fun goal(journey: JourneyForecast, weighIns: List<Pair<LocalDate, Double>>, first: LocalDate, last: LocalDate): RecapCard.Goal {
        val dir = if (journey.targetKg < journey.startKg) -1.0 else 1.0
        val sorted = weighIns.sortedBy { it.first }
        val before = sorted.lastOrNull { it.first.isBefore(first) } ?: sorted.firstOrNull { !it.first.isAfter(last) }
        val end = sorted.lastOrNull { !it.first.isAfter(last) }
        val monthKg = if (before != null && end != null && before.first != end.first) (end.second - before.second) * dir else null
        return RecapCard.Goal(
            monthKg = monthKg?.let { round1(it) },
            kgDone = round1(journey.kgDone),
            kgTotal = round1(abs(journey.targetKg - journey.startKg)),
            eta = journey.forecastDate,
            reached = journey.status == ForecastStatus.REACHED,
            lose = dir < 0
        )
    }

    fun needle(month: List<DayTotals>, prev: List<DayTotals>, lose: Boolean): RecapCard.Needle? {
        val logged = month.filter { it.logged }
        val helped = mutableListOf<RecapFact>()
        val held = mutableListOf<RecapFact>()
        val proteinDays = logged.count { it.proteinHit }
        if (logged.size >= MEAL_DAYS) {
            if (proteinDays * 2 >= logged.size) {
                helped += RecapFact("Protein on target", "$proteinDays of ${logged.size} logged days")
            } else if (proteinDays * 3 < logged.size) {
                held += RecapFact("Protein fell short", "on target only $proteinDays of ${logged.size} logged days")
            }
        }
        if (logged.size >= 20) helped += RecapFact("Logging every day", "${logged.size} of ${month.size} days fully logged")
        val activeNow = month.count { it.active }
        val activePrev = prev.count { it.active }
        if (activeNow >= 15 || (activePrev > 0 && activeNow >= activePrev + 4)) {
            helped += RecapFact("Moving most days", if (activePrev > 0 && activeNow > activePrev) "$activeNow active days, up from $activePrev" else "$activeNow active days")
        }
        if (lose && logged.size >= MEAL_DAYS) {
            val weekdays = logged.filter { it.date.dayOfWeek.value <= 5 }
            InsightsEngine.weekendSplit(month)?.let { (weekendAvg, share) ->
                if (weekendAvg >= 300 && share >= 0.3) {
                    held += RecapFact("Weekends", "${InsightsEngine.kcal(weekendAvg)} kcal over target on Sat and Sun, after a weekday average of ${InsightsEngine.kcal(-weekdays.map { it.net }.average())} under")
                }
            }
            val under = logged.count { it.net <= 0 }
            if (under * 3 >= logged.size * 2) helped += RecapFact("Calories in range", "$under of ${logged.size} logged days at or under target")
            if (logged.count { it.net > 0 } * 2 > logged.size && held.none { it.title == "Weekends" }) {
                held += RecapFact("Over target most days", "${logged.count { it.net > 0 }} of ${logged.size} logged days above target")
            }
        }
        if (logged.size in MIN_DAYS until FULL_DAYS) held += RecapFact("Gaps in the log", "${logged.size} of ${month.size} days fully logged")
        if (helped.isEmpty() && held.isEmpty()) return null
        return RecapCard.Needle(helped.take(2), held.take(2))
    }

    /** The food logged in the most meals, by the meal's own item names (not a list of foods). */
    fun onRepeat(meals: List<NutritionEntryEntity>): RecapCard.OnRepeat? {
        val byFood = meals.flatMap { meal -> foodNames(meal).distinct().map { it to meal } }.groupBy({ it.first }, { it.second })
        val (food, withIt) = byFood.maxByOrNull { it.value.size } ?: return null
        if (withIt.size < 3) return null
        val verdicts = withIt.mapNotNull { m -> MealTags.decode(m.qualityTags)?.let { FoodVerdict.of(it, DietScoreEngine.mealRating(m, it), PersonalNutrition.isApproved(m, it), Treats.kcalOf(m, it)) } }
        val verdict = verdicts.groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value * 2 > verdicts.size }?.key
        return RecapCard.OnRepeat(food.replaceFirstChar { it.titlecase(Locale.getDefault()) }, withIt.size, verdict)
    }

    fun macros(logged: List<DayTotals>, meals: List<NutritionEntryEntity>): RecapCard.Macros? {
        val shares = logged.mapNotNull { d -> d.proteinShare?.let { d to it } }
        if (shares.size < MEAL_DAYS) return null
        fun pct(list: List<Pair<DayTotals, Double>>) = list.takeIf { it.size >= 2 }?.let { l -> (l.map { it.second }.average() * 100).roundToInt() }
        val weekend = shares.filter { it.first.date.dayOfWeek == DayOfWeek.SATURDAY || it.first.date.dayOfWeek == DayOfWeek.SUNDAY }
        val kcal = meals.sumOf { it.calories }
        val fibreDays = meals.mapNotNull { m -> fibreOf(m)?.let { m.timestamp to it } }
        val fibre = if (fibreDays.size * 2 >= meals.size && meals.isNotEmpty()) (fibreDays.sumOf { it.second } / logged.size).roundToInt() else null
        return RecapCard.Macros(
            proteinPct = pct(shares) ?: return null,
            weekdayProteinPct = pct(shares - weekend.toSet()),
            weekendProteinPct = pct(weekend),
            carbsPct = if (kcal > 0) (meals.sumOf { it.carbsG } * 4 / kcal * 100).roundToInt() else null,
            fibreG = fibre
        )
    }

    /** The best-rated meals and the ones worth a rethink, by name, from the AI's meal ratings. */
    fun choices(meals: List<NutritionEntryEntity>): RecapCard.Choices? {
        val rated = meals.mapNotNull { m -> MealTags.decode(m.qualityTags)?.takeIf { !it.estimated }?.let { m to DietScoreEngine.mealRating(m, it) } }
        fun names(list: List<Pair<NutritionEntryEntity, Int>>) = list.map { mealName(it.first) }.distinctBy { it.lowercase() }
        val good = names(rated.filter { it.second >= 8 }.sortedByDescending { it.second }).take(3)
        val rethink = names(rated.filter { it.second <= 4 }.sortedBy { it.second }).take(2)
        if (good.isEmpty() && rethink.isEmpty()) return null
        return RecapCard.Choices(good, rethink)
    }

    /** Runs first, else walks: a faster average pace than last month, with at least two of each. */
    fun faster(month: List<DayTotals>, prev: List<DayTotals>): RecapCard.Faster? {
        fun pace(days: List<DayTotals>, run: Boolean): Long? {
            val list = days.flatMap { it.workouts }.filter { (if (run) it.isRun else it.title == "Walk") && it.paceSecondsPerKm != null && (it.distanceKm ?: 0.0) >= 1.0 }
            if (list.size < 2) return null
            val km = list.sumOf { it.distanceKm!! }
            return (list.sumOf { it.paceSecondsPerKm!! * it.distanceKm!! } / km).toLong()
        }
        for (run in listOf(true, false)) {
            val now = pace(month, run) ?: continue
            val before = pace(prev, run) ?: continue
            if (before - now >= 5) return RecapCard.Faster(if (run) "run" else "walk", now, before)
        }
        return null
    }

    fun moving(month: List<DayTotals>, prev: List<DayTotals>, ym: YearMonth): RecapCard.Moving? {
        val active = month.filter { it.active }.map { it.date.dayOfMonth }.toSet()
        val km = month.sumOf { it.workoutKm }
        val stepDays = month.filter { it.steps > 0 }
        if (active.isEmpty() && km < 1) return null
        return RecapCard.Moving(
            km = round1(km),
            avgSteps = if (stepDays.isEmpty()) 0 else stepDays.map { it.steps }.average().roundToInt(),
            activeDays = active,
            daysInMonth = ym.lengthOfMonth(),
            prevActiveDays = prev.count { it.active }.takeIf { it > 0 }
        )
    }

    fun setup(setup: RecapSetup, loggedDays: Int, daysInMonth: Int): RecapCard.Setup? {
        val items = buildList {
            if (!setup.healthConnect) add(SetupItem.HEALTH_CONNECT)
            if (!setup.mic) add(SetupItem.MIC)
            if (!setup.widget) add(SetupItem.WIDGET)
            if (loggedDays * 2 < daysInMonth) add(SetupItem.LOG_DAILY)
        }
        return if (items.isEmpty()) null else RecapCard.Setup(items, loggedDays, daysInMonth)
    }

    /** "6:42" for 402 seconds a km. */
    fun pace(seconds: Long): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    private fun foodNames(meal: NutritionEntryEntity): List<String> {
        val items = MealItems.decode(meal.items)?.items.orEmpty().map { it.name.trim().lowercase(Locale.getDefault()) }.filter { it.isNotEmpty() }
        if (items.isNotEmpty()) return items
        val type = MealTags.decode(meal.qualityTags)?.foodType?.trim().orEmpty()
        return if (type.isNotEmpty()) listOf(type.lowercase(Locale.getDefault())) else emptyList()
    }

    private fun fibreOf(meal: NutritionEntryEntity): Double? =
        MealItems.decode(meal.items)?.let { mi -> mi.items.sumOf { it.fiber * it.qty } }?.takeIf { it > 0 }
            ?: MealTags.decode(meal.qualityTags)?.takeIf { !it.estimated }?.fiberG

    private fun mealName(meal: NutritionEntryEntity): String {
        val name = meal.foodText.trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return if (name.length <= 40) name else name.take(39).substringBeforeLast(' ').trimEnd(',', ' ') + "…"
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
