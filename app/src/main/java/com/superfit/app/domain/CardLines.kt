package com.superfit.app.domain

import java.util.Locale

/**
 * The AI-written lines a workout card can carry: a coach line in the user's style, or a roast.
 * The app hands the AI only the facts it already knows, and a line that brings in a number the app
 * didn't give is thrown away, so a card never shows a made-up stat.
 */
object CardLines {

    const val COACH_SYSTEM = """
        You are the user's AI fitness coach. Write ONE line for a share card the user will post with their workout.
        - Up to 22 words, one or two sentences. Friendly and a little chatty, about this workout and how it fits their week.
        - The user is posting this publicly: celebrate what they did. Never say it wasn't enough, never tell them to do more,
          and never compare it to what they should have done.
        - Use only the facts given. Any number you use must be one of the numbers in the facts; using none is fine.
        - Never mention food, calories eaten, weight, body shape or looks, and never talk about earning or burning off food.
        - No hashtags, no emojis, no quotes, no lists. Plain text only, no markdown.
    """

    const val ROAST_SYSTEM = """
        You roast the user's workout for a share card they will post. Write the roast only.
        - Two short sentences, at most 16 words in total. Lead with the punchline.
        - Roast the workout and the choices (the timing, the short distance, the gentle pace, skipping legs, sitting between sets),
          never the person. Never joke about their body, shape, weight, looks or identity. No swearing.
        - Never joke that they eat too little, and never mention food as something to earn or burn off.
        - If the facts mention pain, injury or illness, don't roast: write one kind line instead.
        - Use only the facts given. Any number you use must be one of the numbers in the facts; using none is fine.
        - No hashtags, no emojis, no quotes. Plain text only, no markdown.
    """

    /** The facts the AI may use, one per line. Only what the coach already gets, plus effort minutes. */
    fun facts(card: ShareCard.Activity, weekCount: Int?): String = buildList {
        val w = card.workout
        val v = card.voice
        if (w != null) {
            add("Workout: ${w.title}, ${w.minutes} minutes")
            w.distanceKm?.let { add(String.format(Locale.US, "Distance: %.2f km", it)) }
            w.paceSecondsPerKm?.let { add("Pace: ${it / 60}:${"%02d".format(it % 60)} per km") }
            w.avgHeartRate?.let { add("Average heart rate: $it bpm") }
        } else if (v != null) {
            add("Workout they logged by voice: \"${v.description.take(200)}\"")
            card.strength?.reps?.let { add("Reps in total: $it") }
            card.strength?.minutes?.let { add("Minutes: $it") }
            card.strength?.let { s ->
                if (s.today.isNotEmpty()) add("Worked today: ${s.today.joinToString(", ") { it.label }}")
                val skipped = StrengthCardDetail.SHOWN - s.today - s.thisWeek
                if (skipped.isNotEmpty()) add("Not trained in the last 7 days: ${skipped.joinToString(", ") { it.label }}")
            }
        }
        card.heart?.effortMinutes?.takeIf { it.isNotEmpty() }?.let { m ->
            add("Effort minutes: " + m.entries.joinToString(", ") { "${it.key.label.lowercase(Locale.US)} ${it.value}" })
        }
        weekCount?.takeIf { it > 0 }?.let { add("Workouts this week so far, this one included (walks not counted): $it") }
        card.moment?.let { add("Stand-out: $it") }
        add("Time of day: ${card.dateLine}")
    }.joinToString("\n")

    fun prompt(facts: String): String = "Facts:\n$facts"

    /** The plain line, or null when it's empty, too long or uses a number that isn't in [facts]. */
    fun accept(raw: String?, facts: String, maxWords: Int): String? {
        val line = raw?.let(::clean)?.takeIf { it.isNotBlank() } ?: return null
        if (line.split(Regex("\\s+")).size > maxWords) return null
        val allowed = numbers(facts)
        if (numbers(line).any { it !in allowed }) return null
        return line
    }

    /** Numbers written in [text], so "6:00" gives 6 and 00 and "5.21 km" gives 5.21. */
    fun numbers(text: String): Set<String> =
        Regex("\\d+(?:\\.\\d+)?").findAll(text).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()

    /** Strips markdown, quotes and line breaks the AI sometimes adds. */
    fun clean(raw: String): String = raw
        .replace("**", "")
        .replace("*", "")
        .replace("#", "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('"', '“', '”', '\'')
        .trim()

    /** The one fair, true line every roast ends with. */
    fun fairLine(moment: String?, weekCount: Int?): String = when {
        moment != null -> "Still: ${moment.replaceFirstChar { it.lowercase(Locale.getDefault()) }}."
        weekCount != null && weekCount >= 2 -> "Still: ${ordinal(weekCount)} workout this week."
        else -> "Still showed up. That counts."
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) {
            "th"
        } else {
            when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$n$suffix"
    }

    /** Word limits: the coach can be a little chatty, the roast stays short. */
    const val COACH_MAX_WORDS = 26
    const val ROAST_MAX_WORDS = 20

    /** The style the coach line is written in: Roast me users get the supportive voice, since Roast is its own chip. */
    fun coachStyle(current: CoachStyle): CoachStyle = if (current == CoachStyle.ROAST) CoachStyle.SUPPORTIVE else current
}
