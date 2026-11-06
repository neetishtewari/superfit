package com.superfit.app.domain

/**
 * How shared code asks the AI for text. Each app plugs in its own client at start-up: Android uses
 * Firebase AI Logic (GeminiClient), the iPhone app will use Firebase's iPhone SDK. Prompts and the
 * reading of answers stay here, so both apps ask and understand the AI the same way.
 */
fun interface AiText {
    suspend fun generate(prompt: String, systemInstructionText: String?, isJson: Boolean): String
}

/** A photo handed to the AI: a Bitmap on Android, a UIImage on iPhone. Never stored. */
expect class AiPhoto

/** Asks the AI about a photo, with the same retries as text. */
fun interface AiPhotoText {
    suspend fun generate(photo: AiPhoto, prompt: String, systemInstructionText: String?, isJson: Boolean): String
}

/** One message in a coach conversation. */
data class AiChatTurn(val fromUser: Boolean, val text: String)

/** Sends [message] in a conversation that already holds [history]. */
fun interface AiChat {
    suspend fun send(systemInstructionText: String, history: List<AiChatTurn>, message: String): String
}

object Ai {
    /** Set once when the app starts, before any AI call. */
    lateinit var text: AiText
    lateinit var photoText: AiPhotoText
    lateinit var chatText: AiChat

    /** Turns an AI failure into a short message people can read. Each app sets its own at start-up. */
    var friendlyError: (Throwable) -> String = { "The AI coach couldn't be reached. Please try again shortly." }

    suspend fun generate(prompt: String, systemInstructionText: String? = null, isJson: Boolean = true): String =
        text.generate(prompt, systemInstructionText, isJson)

    suspend fun generateWithPhoto(photo: AiPhoto, prompt: String, systemInstructionText: String? = null, isJson: Boolean = true): String =
        photoText.generate(photo, prompt, systemInstructionText, isJson)

    suspend fun chat(systemInstructionText: String, history: List<AiChatTurn>, message: String): String =
        chatText.send(systemInstructionText, history, message)
}
