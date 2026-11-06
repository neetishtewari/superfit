package com.superfit.app.domain

/**
 * How shared code asks the AI for text. Each app plugs in its own client at start-up: Android uses
 * Firebase AI Logic (GeminiClient), the iPhone app will use Firebase's iPhone SDK. Prompts and the
 * reading of answers stay here, so both apps ask and understand the AI the same way.
 */
fun interface AiText {
    suspend fun generate(prompt: String, systemInstructionText: String?, isJson: Boolean): String
}

object Ai {
    /** Set once when the app starts, before any AI call. */
    lateinit var text: AiText

    /** Turns an AI failure into a short message people can read. Each app sets its own at start-up. */
    var friendlyError: (Throwable) -> String = { "The AI coach couldn't be reached. Please try again shortly." }

    suspend fun generate(prompt: String, systemInstructionText: String? = null, isJson: Boolean = true): String =
        text.generate(prompt, systemInstructionText, isJson)
}
