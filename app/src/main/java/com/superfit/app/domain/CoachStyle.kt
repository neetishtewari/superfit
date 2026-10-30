package com.superfit.app.domain

/**
 * How the AI coach talks: the user picks it in Settings. Only the tone changes; the facts, numbers
 * and advice stay the same. Added to the daily tip's and the chat's instructions.
 */
enum class CoachStyle(val label: String, val blurb: String, private val voice: String) {
    SUPPORTIVE(
        label = "Supportive",
        blurb = "Warm and encouraging.",
        voice = ""
    ),
    STRAIGHT(
        label = "Straight talk",
        blurb = "Direct and blunt, no sugar-coating.",
        voice = """
            The user chose STRAIGHT TALK. Be blunt and to the point, like a no-nonsense trainer. Skip the cheerleading and
            softening phrases; name the problem plainly and say exactly what to do. Still respectful, never rude.
        """
    ),
    ROAST(
        label = "Roast me",
        blurb = "Short, cheeky jokes. Same good advice.",
        voice = """
            The user chose ROAST ME. Tease them like a quick-witted friend who is also their coach. Be genuinely funny and
            specific: roast their choices and habits from the logs (the third biscuit, the walk that never happened, pizza at
            11 PM), and be playfully impressed by wins.
            Keep it punchy, like an Instagram caption people screenshot:
            - Short sentences, 8 words or fewer each. No long winding sentences, no build-up, no "and also".
            - Use about two thirds of any word limit given above, except a limit written just for roasts: keep to that one. Shorter is funnier.
            - One clear joke per point, then the fix in plain words. Cut every word the joke works without.
            - Lead with the punchline. Don't explain the joke.
            - The joke must be obvious on first read. No layered sarcasm, double meanings or irony that could be taken
              literally; if a line could be misread as a real criticism, make it plainer.
            Too long: "Your protein intake has been so low this week that it's practically become a mythical creature, so maybe add some curd at dinner tonight."
            Right length: "Protein's gone missing again. Curd at dinner, today."
            Hard limits, never broken:
            - Roast choices and habits, never the person. Never joke about their body, shape, weight, looks or identity.
            - No swearing, slurs or genuinely hurtful insults.
            - Never joke that they eat too little or skip meals. A short log means they haven't finished logging.
            - If they mention an injury, pain, illness, low mood, stress, or anything that sounds like disordered eating,
              drop the act completely for that and answer kindly and seriously.
            - The numbers and the advice must stay accurate, safe and useful. The joke is the wrapping, not the content.
        """
    );

    /** One example line, shown when the user picks a style. */
    val sample: String
        get() = when (this) {
            SUPPORTIVE -> "Protein's been short a few days. Dal and a bowl of curd tonight gets you most of the way."
            STRAIGHT -> "Protein short 4 of 5 days. Add curd at dinner. Every day."
            ROAST -> "Your protein filed a missing person report. Curd at dinner."
        }

    /** Title of the evening notification that carries the AI coach's daily tip. */
    fun tipTitle(): String = when (this) {
        ROAST -> "🔥 Your AI coach has notes"
        else -> "⚡ Your AI coach"
    }

    /** The evening reminder when today's log looks unfinished. [day] picks one of a few lines so it doesn't repeat. */
    fun logReminder(logged: Int, target: Int, day: Int): Pair<String, String> = when (this) {
        SUPPORTIVE ->
            "📝 Complete your food log?" to
                "You've logged $logged / $target kcal today. Did you forget to log a meal?"
        STRAIGHT ->
            "📝 Your log is unfinished" to
                "$logged / $target kcal logged. Log the rest of today's meals now, it takes a minute."
        ROAST -> listOf(
            "🔥 Your food log is half-written" to
                "$logged / $target kcal logged. I'm a coach, not a psychic. Log the rest.",
            "🔥 Your food log called" to
                "Ghosted at $logged / $target kcal. Snacks don't log themselves.",
            "🔥 Plot hole in your log" to
                "Cancelled mid-season at $logged / $target kcal. Finish the story."
        )[Math.floorMod(day, 3)]
    }

    /** Shown when the day's log is complete but the AI tip couldn't be written. */
    fun completeDayFallback(logged: Int): Pair<String, String> = when (this) {
        ROAST ->
            "🔥 Look who logged everything" to
                "All $logged kcal logged. I'm almost proud. Now shock me: sleep on time."
        else ->
            "⚡ Daily log complete" to
                "You've logged your whole day ($logged kcal). Prioritise sleep tonight for recovery."
    }

    /** Lines added to an AI coach instruction; empty for the default style. */
    fun instruction(): String = voice.trimIndent().takeIf { it.isNotEmpty() }
        ?.let { "\nTONE (this overrides any other tone described above):\n$it" }
        .orEmpty()

    companion object {
        /** The user's choice, loaded at start-up (see CoachStyleStore). */
        @Volatile var current: CoachStyle = SUPPORTIVE

        fun fromName(name: String?): CoachStyle = entries.firstOrNull { it.name == name } ?: SUPPORTIVE
    }
}
