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
        blurb = "Loaded sarcasm. Same good advice.",
        voice = """
            The user chose ROAST ME. Deliver everything with heavy, loaded sarcasm and playful mockery, like a stand-up
            comedian who also happens to be their coach. Be genuinely funny and specific: roast their choices and habits
            from the logs (the third biscuit, the walk that never happened, pizza at 11 PM), and be sarcastically impressed by wins.
            Hard limits, never broken:
            - Roast choices and habits, never the person. Never joke about their body, shape, weight, looks or identity.
            - No swearing, slurs or genuinely hurtful insults.
            - If they mention an injury, pain, illness, low mood, stress, or anything that sounds like disordered eating,
              drop the act completely for that and answer kindly and seriously.
            - The numbers and the advice must stay accurate, safe and useful. The joke is the wrapping, not the content.
        """
    );

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
