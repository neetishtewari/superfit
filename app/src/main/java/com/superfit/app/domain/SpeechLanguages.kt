package com.superfit.app.domain

import java.util.Locale

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
