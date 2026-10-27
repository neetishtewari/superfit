package com.superfit.app.domain

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/** The words on one recap card: a short title above the big number and a line under it. */
data class RecapWords(val title: String, val line: String)

/**
 * Writes the recap's words in the user's coach style with one AI call a month. The app has already
 * worked out every number; the AI only phrases them. Without the AI, plain wording is used.
 */
object RecapWriter {

    private val etaFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

    val SYSTEM_INSTRUCTION = """
        You write the words on the monthly recap cards inside Superfit, a fitness and nutrition app. It is like Spotify Wrapped for the user's month: each card is one highlight they may share as an Instagram story.
        For each card you get its facts. The card already shows its big number; you write:
        - "title": 2 to 6 words, a punchy heading.
        - "line": one or two sentences, at most 30 words, saying what the facts mean for the user and, where it helps, one thing to try next month.
        Rules:
        1. Only use the numbers you are given. Never invent or recalculate numbers.
        2. Days without logs are unknown, not days without food. Never say or imply the user ate too little.
        3. Never praise eating less or skipping meals, and never comment on the user's body, shape or looks.
        4. Be honest: an off-goal habit is named as working against the goal, kindly or with humour depending on the tone, never called fine.
        5. Plain everyday words, no jargon. No hashtags, no emoji. Never use the word "roast".
        6. Make it delightful and specific, the kind of line people want to share.
        Reply with JSON only: {"cards": {"<card id>": {"title": "...", "line": "..."}}} with one entry for every card id given.
    """.trimIndent()

    fun prompt(recap: MonthRecap): String = buildString {
        append("Month: ${recap.month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)} ${recap.month.year}. ")
        append("Fully logged days: ${recap.loggedDays}.\n\nCards:\n")
        recap.cards.filter { it !is RecapCard.Setup }.forEach { append("- ${it.id}: ${facts(it)}\n") }
    }

    /** The facts behind a card, in words the AI can't misread. */
    fun facts(card: RecapCard): String = when (card) {
        is RecapCard.Goal -> listOfNotNull(
            if (card.reached) "Goal reached." else "${fmt(card.kgDone)} of ${fmt(card.kgTotal)} kg ${if (card.lose) "lost" else "gained"} towards the goal so far.",
            card.monthKg?.let { if (it >= 0) "${fmt(it)} kg of that this month." else "Moved ${fmt(-it)} kg the wrong way this month." },
            card.eta?.let { "At this pace the goal is reached around ${it.format(etaFormat)}." }
        ).joinToString(" ")
        is RecapCard.Needle -> listOfNotNull(
            card.helped.takeIf { it.isNotEmpty() }?.joinToString("; ", "Helped: ") { "${it.title} (${it.detail})" },
            card.held.takeIf { it.isNotEmpty() }?.joinToString("; ", "Held back: ") { "${it.title} (${it.detail})" }
        ).joinToString(". ")
        is RecapCard.OnRepeat ->
            "Most-logged food: ${card.food}, in ${card.times} meals." +
                (card.verdict?.let { " Usually rated by the app's nutritionist as: ${it.label}." } ?: "")
        is RecapCard.Macros -> listOfNotNull(
            "Protein averaged ${card.proteinPct}% of the daily goal on logged days.",
            if (card.weekdayProteinPct != null && card.weekendProteinPct != null) "Weekdays ${card.weekdayProteinPct}%, weekends ${card.weekendProteinPct}%." else null,
            card.carbsPct?.let { "Carbs were $it% of calories." },
            card.fibreG?.let { "Fibre about $it g a day (aim for 25 to 30 g)." }
        ).joinToString(" ")
        is RecapCard.Choices -> listOfNotNull(
            card.good.takeIf { it.isNotEmpty() }?.joinToString(", ", "Best-rated meals: "),
            card.rethink.takeIf { it.isNotEmpty() }?.joinToString(", ", "Lowest-rated meals: ")
        ).joinToString(". ")
        is RecapCard.Faster -> "Average ${card.activity} pace ${MonthlyRecap.pace(card.paceNow)} per km, down from ${MonthlyRecap.pace(card.pacePrev)} last month (${card.pacePrev - card.paceNow} seconds faster per km)."
        is RecapCard.Moving -> listOfNotNull(
            "Active on ${card.activeDays.size} of ${card.daysInMonth} days" + (card.prevActiveDays?.let { " (last month $it)" } ?: "") + ".",
            if (card.km >= 1) "${fmt(card.km)} km of workouts." else null,
            if (card.avgSteps > 0) "${String.format(Locale.US, "%,d", card.avgSteps)} steps a day on average." else null
        ).joinToString(" ")
        is RecapCard.Strength -> if (card.sessions == 0) {
            "No strength sessions logged this month" + (if (card.km >= 1) ", while doing ${fmt(card.km)} km of cardio" else "") +
                ". Strength training twice a week helps keep muscle. They may already lift and just not log it: they can say it into the mic."
        } else {
            "${card.sessions} strength sessions this month" + (if (card.prevSessions > 0) ", ${card.prevSessions} last month." else ".")
        }
        is RecapCard.Setup -> ""
    }

    /** Reads the AI's words; any card it skipped or got wrong keeps the plain wording. */
    fun parse(json: String, recap: MonthRecap): Map<String, RecapWords> {
        val cards = runCatching { JSONObject(json.trim().removePrefix("```json").removeSuffix("```")).getJSONObject("cards") }.getOrNull()
        return recap.cards.associate { card ->
            val ai = cards?.optJSONObject(card.id)?.let { o ->
                val title = o.optString("title").trim()
                val line = o.optString("line").trim()
                if (title.isNotEmpty() && line.isNotEmpty() && title.length <= 60 && line.length <= 260) RecapWords(title, line) else null
            }
            card.id to (ai ?: plain(card))
        }
    }

    /** Wording that needs no AI, used for the setup card and whenever the AI is unavailable. */
    fun plain(card: RecapCard): RecapWords = when (card) {
        is RecapCard.Goal -> when {
            card.reached -> RecapWords("Goal reached", "You got there. Time to set the next one.")
            (card.monthKg ?: 0.0) > 0 -> RecapWords("This month you ${if (card.lose) "lost" else "gained"}", "${fmt(card.kgDone)} of ${fmt(card.kgTotal)} kg done." + (card.eta?.let { " At this pace you get there around ${it.format(etaFormat)}." } ?: ""))
            else -> RecapWords("Your goal", "${fmt(card.kgDone)} of ${fmt(card.kgTotal)} kg done so far." + (card.eta?.let { " At this pace you get there around ${it.format(etaFormat)}." } ?: ""))
        }
        is RecapCard.Needle -> RecapWords("What moved the needle", (card.helped.map { it.title } + card.held.map { it.title }).joinToString(". ") + ".")
        is RecapCard.OnRepeat -> RecapWords("On repeat", "Your most-logged food this month." + (card.verdict?.let { " Usually: ${it.label.lowercase(Locale.getDefault())}." } ?: ""))
        is RecapCard.Macros -> RecapWords(
            "Your macros",
            if (card.weekdayProteinPct != null && card.weekendProteinPct != null && abs(card.weekdayProteinPct - card.weekendProteinPct) >= 15) {
                "Protein was ${card.weekdayProteinPct}% of goal on weekdays and ${card.weekendProteinPct}% at weekends."
            } else {
                "Protein averaged ${card.proteinPct}% of your goal on logged days."
            }
        )
        is RecapCard.Choices -> RecapWords("Choices that stood out", "Your best-rated meals, and a couple worth a rethink.")
        is RecapCard.Faster -> RecapWords("You got faster", "${card.pacePrev - card.paceNow} seconds a km quicker than last month.")
        is RecapCard.Moving -> RecapWords("You kept moving", "Active on ${card.activeDays.size} of ${card.daysInMonth} days.")
        is RecapCard.Strength -> if (card.sessions == 0) {
            RecapWords("Strength sessions logged", "Two short strength sessions a week help keep muscle while you lose fat.")
        } else {
            RecapWords("Strength sessions", "Keep it going: strength work keeps muscle while you lose fat.")
        }
        is RecapCard.Setup -> RecapWords("Make next month's recap smarter", "Only what's missing is listed.")
    }

    fun fmt(v: Double): String = if (abs(v - v.roundToInt()) < 0.05) "${v.roundToInt()}" else String.format(Locale.US, "%.1f", v)
}
