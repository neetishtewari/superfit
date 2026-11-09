package com.superfit.app.domain

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All Gemini calls go through here. Requests go through Firebase AI Logic and are billed to
 * Superfit's Firebase project, protected by App Check.
 */
object GeminiClient {

    val MODEL_CASCADE get() = AiFailover.MODEL_CASCADE

    // Cache GenerativeModel instances to maintain warm HTTP/2 & TLS connection pools.
    private val firebaseModelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun getOrCreateFirebaseModel(
        modelName: String,
        systemInstructionText: String?,
        isJson: Boolean
    ): GenerativeModel {
        val cacheKey = "$modelName:${systemInstructionText?.hashCode() ?: 0}:$isJson"
        return firebaseModelCache.getOrPut(cacheKey) {
            buildFirebaseModel(modelName, systemInstructionText, isJson)
        }
    }

    private fun buildFirebaseModel(
        modelName: String,
        systemInstructionText: String?,
        isJson: Boolean
    ): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                if (isJson) {
                    responseMimeType = "application/json"
                }
            },
            systemInstruction = if (systemInstructionText != null) {
                content { text(systemInstructionText) }
            } else {
                null
            }
        )

    /** Executes generation with retries and an automatic multi-model failover cascade. */
    suspend fun generateContent(
        prompt: String,
        systemInstructionText: String? = null,
        isJson: Boolean = true
    ): String = runWithFailover { modelName ->
        getOrCreateFirebaseModel(modelName, systemInstructionText, isJson).generateContent(prompt).text
    }

    /**
     * Like [generateContent], with a photo sent alongside [prompt]. The photo is shrunk to
     * 1024 px on its long edge first; the SDK sends it as JPEG. It is never saved.
     */
    suspend fun generateContentWithImage(
        image: Bitmap,
        prompt: String,
        systemInstructionText: String?,
        isJson: Boolean = true
    ): String {
        val scaled = withContext(Dispatchers.Default) {
            val (w, h) = AiFailover.scaledSize(image.width, image.height)
            if (w == image.width && h == image.height) image else Bitmap.createScaledBitmap(image, w, h, true)
        }
        return runWithFailover { modelName ->
            val input = content {
                image(scaled)
                text(prompt)
            }
            getOrCreateFirebaseModel(modelName, systemInstructionText, isJson).generateContent(input).text
        }
    }

    /**
     * Sends [message] in a coach conversation that already contains [history], with the same
     * retries and failover as [generateContent].
     */
    suspend fun chat(
        systemInstructionText: String,
        history: List<AiChatTurn>,
        message: String
    ): String = runWithFailover { modelName ->
        // Not cached: the coach's instructions carry today's live numbers, so they change often.
        val model = buildFirebaseModel(modelName, systemInstructionText, isJson = false)
        val firebaseHistory = history.map { turn ->
            content(role = if (turn.fromUser) "user" else "model") { text(turn.text) }
        }
        model.startChat(history = firebaseHistory).sendMessage(message).text
    }

    /** The shared retries and model failover (AiFailover), reporting an empty Gemini balance to Crashlytics. */
    suspend fun runWithFailover(
        models: List<String> = AiFailover.MODEL_CASCADE,
        timeoutMs: Long = AiFailover.PER_ATTEMPT_TIMEOUT_MS,
        totalBudgetMs: Long = AiFailover.TOTAL_BUDGET_MS,
        call: suspend (modelName: String) -> String?
    ): String = AiFailover.run(models, timeoutMs, totalBudgetMs, { com.superfit.app.data.AppAnalytics.recordError(it) }, call)

    fun sanitizeError(e: Throwable?): String = AiFailover.sanitizeError(e)
}
