package com.superfit.app.domain

import java.util.Locale

/**
 * What the phone heard: its best guess plus the other ways it might have heard the same words.
 * The AI picks the version that makes sense as food or exercise, so "jaipuri chane" can lose to
 * "cheh poori aur chane" when the phone offers both.
 */
data class SpokenInput(val text: String, val otherVersions: List<String> = emptyList()) {

    companion object {
        /** Most versions passed on to the AI, best guess included. */
        const val MAX_VERSIONS = 5

        /**
         * Joins each stretch of speech (the session restarts listening after a pause) into whole
         * sentences. Version n uses each stretch's n-th guess, or its best guess when it has fewer.
         */
        fun fromStretches(stretches: List<List<String>>): SpokenInput? {
            val heard = stretches.map { guesses -> guesses.map { it.trim() }.filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
            if (heard.isEmpty()) return null
            val deepest = heard.maxOf { it.size }.coerceAtMost(MAX_VERSIONS)
            val versions = (0 until deepest)
                .map { n -> heard.joinToString(" ") { it.getOrElse(n) { _ -> it.first() } } }
                .distinctBy { it.lowercase() }
            return SpokenInput(versions.first(), versions.drop(1))
        }

        /** What the phone's voice-typing box returned: best guess first. */
        fun fromGuesses(guesses: List<String>?): SpokenInput? = fromStretches(listOfNotNull(guesses))
    }
}

/**
 * Which languages to listen in. Works for any language: it follows the phone's own language
 * settings, with the region taken from the SIM when the phone language has none of its own
 * (English set up on a phone in India listens as Indian English, which copes far better with Hindi
 * words and numbers mixed in). The phone's other languages are offered for automatic detection.
 */
object SpeechLanguages {

    data class Choice(val primary: String, val others: List<String>)

    fun choose(phoneLocales: List<Locale>, simCountry: String?): Choice? {
        val first = phoneLocales.firstOrNull { it.language.isNotBlank() } ?: return null
        val country = simCountry?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        // A phone set to "English (United States)" in India still gets Indian English. The SIM says
        // where the person is; the phone's region is often just the default that came with it.
        val primary = if (country != null && first.language == "en" && first.country != country) {
            Locale.Builder().setLanguage(first.language).setRegion(country).build()
        } else {
            first
        }
        val primaryTag = primary.toLanguageTag()
        val others = phoneLocales.drop(1)
            .filter { it.language.isNotBlank() }
            .map { it.toLanguageTag() }
            .filter { it != primaryTag }
            .distinct()
        return Choice(primaryTag, others)
    }
}
