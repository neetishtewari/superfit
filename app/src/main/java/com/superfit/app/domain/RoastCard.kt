package com.superfit.app.domain

/** What the user can put on a "Roast my day" card. Roast and Numbers start on; one of them always stays on. */
enum class DayChip(val key: String, val label: String) {
    ROAST("roast", "Roast"),
    NUMBERS("numbers", "Numbers"),
    JOURNEY("journey", "Journey");

    companion object {
        val DEFAULTS: Set<DayChip> = setOf(ROAST, NUMBERS)

        /** Turns [chip] on or off, but never leaves a card with neither the roast nor the numbers. */
        fun toggle(chips: Set<DayChip>, chip: DayChip): Set<DayChip> {
            val next = if (chip in chips) chips - chip else chips + chip
            return if (ROAST !in next && NUMBERS !in next) chips else next
        }
    }
}

/**
 * Which of the day's numbers back up a roast, and the AI prompts for a roast card. The numbers are
 * picked by what the roast talks about (sleep, steps, workouts, protein, how they ate), worked out
 * here without asking the AI. No food names are listed: only words about sleep, moving and meals.
 */
object RoastCard {

    /** At most this many numbers sit under a roast, so the joke stays the star. */
    const val MAX_TILES = 2

    /** A short roast for a photo card, small enough to read over a picture. */
    const val SHORT_MAX_WORDS = 20

    /** A roast longer than this gets a short version when it goes on a photo. */
    const val PHOTO_MAX_CHARS = 130

    /** Fresh roasts a day from "Try another". */
    const val AGAIN_PER_DAY = 3

    private val topics: List<Pair<String, List<String>>> = listOf(
        "SLEEP" to listOf("sleep", "slept", "asleep", "snooz", "nap", "bed", "pillow", "alarm", "woke", "wake", "doze", "lie-in", "lie in"),
        "STEPS" to listOf("step", "walk", "couch", "sofa", "stroll", "sitting", "sat ", "sedentary", "chair"),
        "WORKOUT" to listOf("workout", "gym", "run", "ran ", "jog", "lift", "squat", "push-up", "pushup", "pull-up", "plank", "cardio", "exercise", "training", "sweat", "reps"),
        "PROTEIN" to listOf("protein", "whey"),
        "DIET QUALITY" to listOf(
            "veg", "fruit", "fibre", "fiber", "junk", "treat", "sugar", "fried", "dessert", "sweet", "takeaway",
            "ate ", "eat", "snack", "meal", "breakfast", "lunch", "dinner", "food", "plate"
        )
    )

    /**
     * The numbers from [pool] that the roast is about, in the order it brings them up, at most
     * [MAX_TILES]. A number the roast quotes (8,432 steps) counts as well as its words. None when
     * the roast talks about nothing the day has a number for.
     */
    fun matching(roast: String?, pool: List<CardStat>): List<CardStat> {
        if (roast.isNullOrBlank()) return emptyList()
        val text = " " + roast.lowercase() + " "
        val quoted = bigNumbers(roast)
        return pool.mapNotNull { stat ->
            val words = topics.firstOrNull { it.first == stat.label }?.second.orEmpty()
            val at = words.mapNotNull { w -> Regex("\\b" + Regex.escape(w)).find(text)?.range?.first }.minOrNull()
                ?: if (bigNumbers(stat.value).any { it in quoted }) text.length else null
            at?.let { it to stat }
        }.sortedBy { it.first }.map { it.second }.take(MAX_TILES)
    }

    /** Numbers of three digits or more, with thousands commas dropped, so "8,432" and "8432" match. */
    private fun bigNumbers(text: String): Set<String> =
        Regex("\\d[\\d,]*").findAll(text).map { it.value.replace(",", "") }.filter { it.length >= 3 }.toSet()

    /** The day's numbers the AI may use, one per line. */
    fun facts(pool: List<CardStat>): String = pool.joinToString("\n") { s ->
        listOfNotNull(s.label.lowercase().replaceFirstChar { it.uppercase() } + ": " + s.value, s.unit, s.note).joinToString(" ")
    }

    const val SHORT_SYSTEM = """
        You shorten a roast for a share card that sits over the user's photo. Write the short roast only.
        - At most 20 words, one or two short sentences. Keep the punchline and the same joke; don't add a new one.
        - Use only numbers that are in the roast or the facts; using none is fine.
        - Roast the choices, never the person. Never joke about body, shape, weight, looks or identity. No swearing.
        - Never say or hint that they ate too little or are under-fuelled.
        - No hashtags, no emojis, no quotes. Plain text only, no markdown.
    """

    const val AGAIN_SYSTEM = """
        You roast the user's day for a share card. The user didn't love the last roast, so write a different one. Write the roast only.
        - Two short punchy sentences, at most 16 words in total: the joke, then the fix.
        - A different joke from the last roast. You may pick another fact from the list.
        - Roast the choices, never the person. Never joke about body, shape, weight, looks or identity. No swearing.
        - Today's log is often unfinished: never say or hint that they ate too little, are under-fuelled or starving.
        - Use only numbers that are in the facts or the last roast; using none is fine.
        - No hashtags, no emojis, no quotes. Plain text only, no markdown.
    """

    const val PICK_SYSTEM = """
        A roast of the user's day is going on a share card with up to 2 of today's numbers under it.
        Pick the numbers the roast's joke is about, so they back it up. Food or meals in the roast
        (any dish, snack, sweet or drink) relate to PROTEIN and DIET QUALITY; sleeping in or staying up
        to SLEEP; sitting, walking or not moving to STEPS; exercise to WORKOUT.
        Reply with the labels only, most relevant first, separated by commas, exactly as written in the list.
        Reply NONE when the roast is about nothing on the list.
    """

    fun pickPrompt(roast: String, pool: List<CardStat>): String =
        "Roast:\n$roast\n\nToday's numbers:\n" + pool.joinToString("\n") { "${it.label}: ${it.value}${it.unit?.let { u -> " $u" }.orEmpty()}" }

    /** The labels from the AI's answer that are on [pool], at most [MAX_TILES]; empty for NONE; null for an answer that can't be read. */
    fun parsePick(raw: String?, pool: List<CardStat>): List<String>? {
        val text = raw?.trim()?.uppercase() ?: return null
        if (text.startsWith("NONE")) return emptyList()
        val labels = pool.map { it.label }
        val found = text.split(',', '\n').map { it.trim().trim('.', '*', '"', '-', ' ') }.filter { it in labels }.distinct()
        return found.take(MAX_TILES).ifEmpty { null }
    }

    fun shortPrompt(roast: String, facts: String): String = "Roast:\n$roast\n\nFacts:\n$facts"

    fun againPrompt(roast: String, facts: String): String = "Last roast:\n$roast\n\nFacts about today:\n$facts"
}
