package com.superfit.app.domain

import com.superfit.app.json.JSONObject
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.isoDayNumber

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
        is RecapCard.Calories -> calorieFacts(card.sources)
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
            "${card.sessions} strength sessions this month" + (if (card.prevSessions > 0) ", ${card.prevSessions} last month." else ".") +
                (card.detail?.let { strengthFacts(it) } ?: "")
        }
        is RecapCard.Body -> bodyFacts(card)
        is RecapCard.Heart -> card.change.let { h ->
            "On ${h.activity}s, average pace ${MonthlyRecap.pace(h.paceNow)} per km (last month ${MonthlyRecap.pace(h.pacePrev)}), " +
                "average heart rate ${h.bpmNow} bpm, down from ${h.bpmPrev} last month: ${h.bpmDrop} beats a minute lower at the same pace or faster, a sign of getting fitter."
        }
        is RecapCard.Reps -> card.totals.let { r ->
            "${num(r.total)} reps of exercises logged by voice this month" + (if (r.prev > 0) " (last month ${num(r.prev)})." else ".") +
                (r.top?.let { " Most of them: ${num(it.second)} ${it.first}." } ?: "")
        }
        is RecapCard.Rhythm -> card.rhythm.let { r ->
            listOfNotNull(
                r.hour?.let { "${r.workoutsNearHour} of ${r.workoutsTimed} workouts started around ${ActivityPatterns.hourLabel(it)}." },
                r.bestDay?.let { "${dayName(it)} is their biggest step day: ${num(r.bestDaySteps)} steps on average, against ${num(r.avgSteps)} on a usual day." }
            ).joinToString(" ")
        }
        is RecapCard.Setup -> ""
    }

    /** Start and end of the month from the smart scale; only what it measured. */
    fun bodyFacts(card: RecapCard.Body): String {
        val c = card.change
        val measured = listOfNotNull(
            c.fat?.let { "body fat ${fmt(c.fatFrom!!)}% to ${fmt(c.fatTo!!)}% (${signed(it)} points)" },
            c.lean?.let { "muscle (lean mass) ${fmt(c.leanFrom!!)} kg to ${fmt(c.leanTo!!)} kg (${signed(it)} kg)" },
            c.bmr?.let { "resting burn ${num(c.bmrFrom!!.roundToInt())} to ${num(c.bmrTo!!.roundToInt())} kcal a day" },
            c.weight?.let { "weight ${signed(it)} kg" }
        )
        return "From their smart scale, start of the month against the end (averages, so one odd reading doesn't count): " +
            measured.joinToString(", ") + ". Goal: ${if (card.lose) "lose fat" else "build or keep muscle"}. " +
            "Losing fat while keeping or adding muscle is the good outcome; muscle going down while losing weight means more protein and strength work. A small dip in resting burn while weight comes down is normal, never a problem."
    }

    fun bodyWords(card: RecapCard.Body): RecapWords {
        val c = card.change
        val fat = c.fat?.takeIf { c.fatMoved }
        val lean = c.lean?.takeIf { c.leanMoved }
        return when {
            lean != null && lean < 0 && card.lose -> RecapWords("Some of it was muscle", "Muscle down ${fmt(-lean)} kg. More protein and two strength sessions a week help keep it.")
            fat != null && fat < 0 -> RecapWords("Body fat down ${fmt(-fat)} points", if (lean == null || lean > 0) "Fat went, muscle stayed. That's the good kind of change." else "Your scale saw the fat come down.")
            lean != null && lean > 0 -> RecapWords("Muscle up ${fmt(lean)} kg", "More muscle burns more, even at rest.")
            fat != null -> RecapWords("Body fat up ${fmt(fat)} points", "A month to steady it: protein at every meal and a bit more moving.")
            (c.bmr ?: 0.0) > 0 -> RecapWords("Resting burn up", "Your body burns a little more at rest than it did.")
            else -> RecapWords("Your resting burn", "It dips a little as weight comes down. That's normal; muscle keeps it up.")
        }
    }

    private fun signed(v: Double) = (if (v > 0) "+" else "−") + fmt(abs(v))

    /** The top 5 by calories and the treat rule, said so the AI can't turn it around. */
    fun calorieFacts(c: CalorieSources): String = buildString {
        append("Top foods by calories added this month (not by how often): ")
        append(c.top.mapIndexed { i, f -> "${i + 1}. ${f.name} ${num(f.kcal)} kcal (${f.times} times, ${f.pct}% of calories${if (f.isTreat) ", a treat" else ""})" }.joinToString("; "))
        append(". ")
        val b = c.biggestTreat
        when {
            b == null && c.surelyNoTreats -> append("No treat foods this month: good news.")
            b == null -> Unit
            c.treatRank != null -> {
                append("Biggest treat food: ${b.name}, ${num(b.treatKcal)} kcal in ${b.times} sittings, and it IS in the top 5 at number ${c.treatRank}")
                c.outweighs?.let { append(", more calories than ${it.name} logged ${it.times} times") }
                append(". Be honest that this works against the goal.")
                c.swap?.let { append(" A swap idea: ${it.alternative}.") }
            }
            else -> append("Biggest treat food: ${b.name}, ${num(b.treatKcal)} kcal, and it is NOT in the top 5: good news, everyday food made up most calories.")
        }
        c.mostLogged?.takeIf { m -> c.top.firstOrNull()?.key != m.key }?.let { append(" Most-logged food: ${it.name}, ${it.times} times.") }
    }

    /** How often, how long and what was trained, only from what the user said when logging. */
    fun strengthFacts(d: StrengthDetail): String = listOfNotNull(
        " That is ${d.perWeekText()}.",
        if (d.timedSessions > 0) " ${d.minutes} minutes in total across the ${d.timedSessions} sessions with a time logged." else null,
        if (d.areas.isNotEmpty()) " Of the ${d.taggedSessions} sessions that said what was trained: ${StrengthSessions.areasText(d)}." else null,
        d.missing?.let { " No ${it.label} sessions were logged: suggest adding one a week for balance, or logging it if they already train it." }
    ).joinToString("")

    private fun num(v: Int) = String.format(Locale.US, "%,d", v)

    private fun dayName(d: kotlinx.datetime.DayOfWeek) = java.time.DayOfWeek.of(d.isoDayNumber).getDisplayName(java.time.format.TextStyle.FULL, Locale.US) + "s"

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
        is RecapCard.Calories -> card.sources.let { c ->
            val b = c.biggestTreat
            RecapWords(
                "Where your calories came from",
                when {
                    b != null && c.treatRank != null -> "${b.name} made your top 5, at number ${c.treatRank}. " + (c.swap?.let { "Try ${it.alternative.lowercase(Locale.getDefault())} next month." } ?: "An easy win for next month.")
                    b != null -> "Your biggest treat, ${b.name.lowercase(Locale.getDefault())}, stayed out of your top 5. Everyday food did the heavy lifting."
                    c.surelyNoTreats -> "No treat foods this month. Everyday food did all the work."
                    else -> "Your five biggest calorie foods this month."
                }
            )
        }
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
            RecapWords(
                "Strength sessions",
                card.detail?.missing?.let { "Only ${if (it == BodyArea.LOWER) "upper" else "lower"} body logged. Add a ${if (it == BodyArea.LOWER) "leg" else "upper body"} day, or say it when you log it." }
                    ?: "Keep it going: strength work keeps muscle while you lose fat."
            )
        }
        is RecapCard.Body -> bodyWords(card)
        is RecapCard.Heart -> RecapWords("Fitter heart", "Same pace or faster, ${card.change.bpmDrop} beats a minute lower than last month.")
        is RecapCard.Reps -> RecapWords("Reps this month", if (card.totals.prev > 0 && card.totals.total > card.totals.prev) "Up from ${num(card.totals.prev)} last month." else "Every rep you said out loud, added up.")
        is RecapCard.Rhythm -> RecapWords("Your moving rhythm", card.rhythm.bestDay?.let { "${dayName(it)} are your big step days." } ?: "You've got a routine going.")
        is RecapCard.Setup -> RecapWords("Make next month's recap smarter", "Only what's missing is listed.")
    }

    fun fmt(v: Double): String = if (abs(v - v.roundToInt()) < 0.05) "${v.roundToInt()}" else String.format(Locale.US, "%.1f", v)
}
