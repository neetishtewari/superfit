package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the coach chat knows about the user when it answers: their own meals, days, workouts,
 * goal, notes and recent conversation. The app gathers and counts everything here; the AI only
 * puts it into words.
 */
object CoachChatContext {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    val RULES = """
        Rules:
        1. CRITICAL: Distinguish between incomplete logging vs actual low calorie intake. If logged calories are low or missing, assume the user did not finish logging, never starvation, extreme dieting or 'feast or famine' behaviour.
        2. Answer for this user, not people in general. Use their logged foods, their numbers, their goal and what you know about them. Name the actual foods from their log when it helps. Don't give generic advice when their own data answers the question.
        3. Only use numbers given here. Never invent or recalculate numbers. If what they ask about isn't in their logs, say so plainly.
        4. If they logged only a few days this week, say briefly that your read is rough.
        5. Respect what you know about them: never suggest something an injury, dislike or diet day rules out. Their own nutritionist's advice wins over everything.
        6. Refer back to earlier conversations when it's relevant, naturally ("You mentioned your knee yesterday").
        7. Suggest foods they already eat where you can.
        8. Keep answers short: 2 to 3 sentences, under 75 words. Only when they ask you to tell more or explain, you may use up to 150 words.
        9. No greetings, pleasantries or sign-offs. Use markdown (bold, bullets) sparingly.
        10. When they ask whether a number is good or bad (like "is 50g of protein enough?"), compare it with their target and goal, and look at the pattern over days rather than one day.
        11. Don't diagnose. For pain that is bad or lasts more than a few days, briefly suggest seeing a doctor.
        12. When you suggest one specific meal or snack they could eat next, end with one extra line exactly like this: LOG: <that food with amounts, e.g. 2 roti with paneer bhurji and 1 bowl curd>. Only for food, at most one LOG line, and leave it out otherwise.
        13. Be honest about food that works against their goal (a samosa, fried snacks, sweets, a big takeaway). Never wave it off with "it's fine", "no stress" or "enjoy it". Say plainly, and kindly, that it goes against their goal and roughly what it costs them, then give one practical way to limit the damage: a smaller portion, a better swap, or a lighter rest of the day. The only exception is a food their nutritionist approved or they marked "Fine for me".
        14. When they ask "Roast my day", reply with one roast of today's log only: at most 2 sentences and 35 words, using its real foods and numbers, with no LOG line. Today's log is often unfinished, so never say or hint that they ate too little, are under-fuelled or are starving; roast the foods that ARE logged, or the half-written log itself. If today has almost nothing logged, say you can't roast thin air and ask them to log first.
    """.trimIndent()

    /** The user's own meals, days, workouts, goal and notes, for the coach chat's instructions. */
    fun personal(
        today: LocalDate,
        now: LocalTime,
        todayMeals: List<NutritionEntryEntity>,
        report: CoachReport,
        workouts: List<WorkoutEntryEntity>,
        targetWeightKg: Double?,
        goalDate: LocalDate?,
        commonFoods: String,
        notes: List<CoachNote>,
        todayTip: CoachInsight?,
        earlierTopics: List<String>,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = buildString {
        appendLine("Today is ${today.format(dayFormat)}, ${now.format(timeFormat)}.")
        if (targetWeightKg != null) {
            appendLine(
                "Goal weight: ${fmt(targetWeightKg)} kg" + (goalDate?.let { ", by ${it.format(dayFormat)} ${it.year}" } ?: "") + "."
            )
        }
        appendLine()
        appendLine("Today's meals so far:")
        appendLine(mealLines(todayMeals, zone).ifBlank { "- none logged yet" })
        appendLine()
        appendLine("The last 7 days (newest first):")
        appendLine(dayLines(report.days.take(7)))
        appendLine()
        appendLine("Workouts in the last 14 days:")
        appendLine(workoutLines(workouts, today, zone).ifBlank { "- none logged" })
        appendLine()
        appendLine("Foods they often eat: ${commonFoods.ifBlank { "not enough logs yet" }}")
        appendLine()
        appendLine("What you know about them (from earlier chats and their own notes; respect these):")
        appendLine(CoachNotes.promptLines(notes, today).ifBlank { "- nothing yet" })
        if (todayTip != null) {
            appendLine()
            appendLine("Today's tip on their home screen, from you: ${todayTip.headline} ${todayTip.action}".trim())
        }
        if (earlierTopics.isNotEmpty()) {
            appendLine()
            appendLine("Things they asked you about before the recent conversation (newest first):")
            earlierTopics.forEach { appendLine("- $it") }
        }
    }.trim()

    fun mealLines(meals: List<NutritionEntryEntity>, zone: ZoneId): String =
        meals.sortedBy { it.timestamp }.joinToString("\n") { e ->
            val time = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalTime().format(timeFormat)
            val foods = MealItems.decode(e.items)?.items?.takeIf { it.isNotEmpty() }?.joinToString { it.label() } ?: e.foodText
            "- $time: $foods (${e.calories.roundToInt()} kcal, ${e.proteinG.roundToInt()} g protein)"
        }

    fun dayLines(days: List<DayLog>): String = days.joinToString("\n") { d ->
        val day = d.date.format(dayFormat)
        when (d.status) {
            DayLogStatus.BLANK -> "- $day: nothing logged (unknown, not a day without food)"
            else -> {
                val how = if (d.status == DayLogStatus.COMPLETE) "looks fully logged" else "partly logged, so totals are low"
                val meals = d.entries.size
                "- $day: ${d.calories.roundToInt()} kcal, ${d.proteinG.roundToInt()} g protein, $meals ${if (meals == 1) "meal" else "meals"} ($how)"
            }
        }
    }

    fun workoutLines(workouts: List<WorkoutEntryEntity>, today: LocalDate, zone: ZoneId, limit: Int = 15): String {
        val since = today.minusDays(13)
        return workouts
            .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() to it }
            .filter { (date, _) -> date >= since && date <= today }
            .sortedByDescending { it.second.timestamp }
            .take(limit)
            .joinToString("\n") { (date, w) -> "- ${date.format(dayFormat)}: ${w.description} (~${w.caloriesBurned.roundToInt()} kcal)" }
    }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
}

/** Ready-made questions above the chat box that fit the moment. */
object CoachQuestions {

    const val ABOUT_TODAYS_TIP = "Tell me more about today's tip"

    fun forNow(
        hour: Int,
        day: DayOfWeek,
        mealsToday: Int,
        proteinStillNeeded: Int,
        workedOutToday: Boolean,
        hasTodayTip: Boolean,
        limit: Int = 3
    ): List<String> {
        val options = buildList {
            if (hasTodayTip) add(ABOUT_TODAYS_TIP)
            if (hour >= 16 && proteinStillNeeded >= 20) add("What should I eat for dinner to hit my protein?")
            if (hour < 11 && mealsToday == 0) add("What's a good breakfast for my goal?")
            if (hour in 11..15) add("What's a good lunch for my goal?")
            if (workedOutToday) add("What should I eat after my workout?")
            if (day == DayOfWeek.SUNDAY || day == DayOfWeek.MONDAY) add("How did my week go?")
            add("Is my diet okay?")
            add("What should I focus on this week?")
        }
        return options.distinct().take(limit)
    }
}
