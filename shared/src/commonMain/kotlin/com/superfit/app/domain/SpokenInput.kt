package com.superfit.app.domain

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
