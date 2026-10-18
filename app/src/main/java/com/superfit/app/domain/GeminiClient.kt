package com.superfit.app.domain

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import java.util.concurrent.ConcurrentHashMap

/**
 * All Gemini calls go through here.
 *
 * With no key of the user's own (the normal case), requests go through Firebase AI Logic and are
 * billed to Superfit's Firebase project, protected by App Check. If someone has added their own
 * Gemini key in Settings, requests go straight to Google with that key instead.
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    // Multi-model failover cascade prioritized for lowest latency:
    // 1. gemini-3.1-flash-lite: Production-proven ultra-low latency model (<1s, zero thinking latency overhead)
    // 2. gemini-2.5-flash: High-performance balanced fallback
    // 3. gemini-3.5-flash: Frontier multimodal fallback
    // 4. gemini-2.0-flash: Universal base fallback
    val MODEL_CASCADE = listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-2.0-flash"
    )

    // Per-attempt timeout. Long prompts (coaching with full history) can legitimately take
    // well over 5s, so a short limit turned slow answers into "busy" errors.
    private const val PER_ATTEMPT_TIMEOUT_MS = 20_000L

    // Attempts per model when Gemini says it is overloaded (503) or the call times out.
    private const val ATTEMPTS_PER_MODEL = 3

    // Backoff before the 2nd and 3rd attempt on the same model, as Google recommends for 503s.
    private val RETRY_DELAYS_MS = listOf(1_000L, 2_000L)

    // Short pause before moving to the next model after a rate limit, so the cascade
    // doesn't burn through every model's per-minute quota in the same second.
    private const val MODEL_SWITCH_DELAY_MS = 1_000L

    // Overall budget across all models and retries, so a bad spell never leaves the user
    // staring at a spinner for minutes.
    private const val TOTAL_BUDGET_MS = 45_000L

    // Cache GenerativeModel instances to maintain warm HTTP/2 & TLS connection pools.
    // The key includes the API key so a changed key takes effect immediately.
    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()
    private val firebaseModelCache = ConcurrentHashMap<String, com.google.firebase.ai.GenerativeModel>()

    /** One message in a coach conversation, for [chat]. */
    data class ChatTurn(val fromUser: Boolean, val text: String)

    enum class ErrorKind {
        /** Server overloaded / unavailable or timed out: wait and retry the same model. */
        BUSY,
        /** Rate limit or quota hit on this model: move on to the next model. */
        RATE_LIMITED,
        /** Model name not found or retired: move on to the next model. */
        MODEL_UNAVAILABLE,
        /** Bad key, bad request, blocked prompt: other models won't help, stop. */
        FATAL,
        /** Anything else: try the next model once. */
        OTHER
    }

    private fun getOrCreateModel(
        modelName: String,
        apiKey: String,
        systemInstructionText: String?,
        isJson: Boolean
    ): GenerativeModel {
        val cacheKey = "$modelName:${apiKey.hashCode()}:${systemInstructionText?.hashCode() ?: 0}:$isJson"
        return modelCache.getOrPut(cacheKey) {
            GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    if (isJson) {
                        responseMimeType = "application/json"
                    }
                },
                systemInstruction = if (systemInstructionText != null) {
                    content { text(systemInstructionText) }
                } else null
            )
        }
    }

    private fun getOrCreateFirebaseModel(
        modelName: String,
        systemInstructionText: String?,
        isJson: Boolean
    ): com.google.firebase.ai.GenerativeModel {
        val cacheKey = "$modelName:${systemInstructionText?.hashCode() ?: 0}:$isJson"
        return firebaseModelCache.getOrPut(cacheKey) {
            buildFirebaseModel(modelName, systemInstructionText, isJson)
        }
    }

    private fun buildFirebaseModel(
        modelName: String,
        systemInstructionText: String?,
        isJson: Boolean
    ): com.google.firebase.ai.GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = com.google.firebase.ai.type.generationConfig {
                if (isJson) {
                    responseMimeType = "application/json"
                }
            },
            systemInstruction = if (systemInstructionText != null) {
                com.google.firebase.ai.type.content { text(systemInstructionText) }
            } else null
        )

    /**
     * Executes generation with retries and an automatic multi-model failover cascade.
     * A blank [apiKey] means "use Superfit's own AI access through Firebase".
     */
    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstructionText: String? = null,
        isJson: Boolean = true
    ): String = withFirebaseWording(apiKey) {
        runWithFailover { modelName ->
            if (apiKey.isBlank()) {
                getOrCreateFirebaseModel(modelName, systemInstructionText, isJson).generateContent(prompt).text
            } else {
                getOrCreateModel(modelName, apiKey, systemInstructionText, isJson).generateContent(prompt).text
            }
        }
    }

    /**
     * Sends [message] in a coach conversation that already contains [history], with the same
     * retries and failover as [generateContent]. A blank [apiKey] uses Firebase.
     */
    suspend fun chat(
        apiKey: String,
        systemInstructionText: String,
        history: List<ChatTurn>,
        message: String
    ): String = withFirebaseWording(apiKey) {
        runWithFailover { modelName ->
            if (apiKey.isBlank()) {
                // Not cached: the coach's instructions carry today's live numbers, so they change often.
                val model = buildFirebaseModel(modelName, systemInstructionText, isJson = false)
                val firebaseHistory = history.map { turn ->
                    com.google.firebase.ai.type.content(role = if (turn.fromUser) "user" else "model") { text(turn.text) }
                }
                model.startChat(history = firebaseHistory).sendMessage(message).text
            } else {
                val model = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    systemInstruction = content { text(systemInstructionText) }
                )
                val keyHistory = history.map { turn ->
                    content(role = if (turn.fromUser) "user" else "model") { text(turn.text) }
                }
                model.startChat(history = keyHistory).sendMessage(message).text
            }
        }
    }

    /**
     * People using Superfit's own AI access have no key to fix, so a refused request
     * (for example an App Check rejection) must not tell them to update their API key.
     */
    private suspend fun withFirebaseWording(apiKey: String, block: suspend () -> String): String {
        if (apiKey.isNotBlank()) return block()
        return try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message == INVALID_KEY_MESSAGE) throw Exception(AI_UNAVAILABLE_MESSAGE, e) else throw e
        }
    }

    /**
     * Runs [call] against each model in [models] until one returns non-blank text.
     *
     * Busy/timeout errors are retried on the same model with backoff; rate limits and
     * missing models move on to the next model; fatal errors (bad key, bad request)
     * stop immediately. Throws an exception carrying a user-friendly message if all fail.
     */
    suspend fun runWithFailover(
        models: List<String> = MODEL_CASCADE,
        timeoutMs: Long = PER_ATTEMPT_TIMEOUT_MS,
        totalBudgetMs: Long = TOTAL_BUDGET_MS,
        call: suspend (modelName: String) -> String?
    ): String {
        var lastException: Exception? = null
        var elapsedMs = 0L

        cascade@ for ((index, modelName) in models.withIndex()) {
            if (index > 0 && lastException != null && classifyError(lastException) == ErrorKind.RATE_LIMITED) {
                delay(MODEL_SWITCH_DELAY_MS)
                elapsedMs += MODEL_SWITCH_DELAY_MS
            }

            for (attempt in 0 until ATTEMPTS_PER_MODEL) {
                if (attempt > 0) {
                    val wait = RETRY_DELAYS_MS[minOf(attempt - 1, RETRY_DELAYS_MS.lastIndex)]
                    Log.d(TAG, "Retrying $modelName in ${wait}ms (attempt ${attempt + 1}/$ATTEMPTS_PER_MODEL)")
                    delay(wait)
                    elapsedMs += wait
                }
                if (elapsedMs >= totalBudgetMs) break@cascade

                val startTime = System.currentTimeMillis()
                val outcome = try {
                    // withTimeoutOrNull returns null on timeout, so wrap the result to tell
                    // "timed out" apart from "model returned null text".
                    withTimeoutOrNull(timeoutMs) { Result.success(call(modelName)) }
                        ?: Result.failure(Exception("Model $modelName timed out after ${timeoutMs}ms"))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }

                val text = outcome.getOrNull()
                elapsedMs += if (outcome.exceptionOrNull()?.message?.contains("timed out") == true) {
                    timeoutMs
                } else {
                    System.currentTimeMillis() - startTime
                }
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "Generated content using $modelName in ${System.currentTimeMillis() - startTime}ms")
                    return text
                }

                val error = outcome.exceptionOrNull() as? Exception
                    ?: Exception("Model $modelName returned an empty response")
                lastException = error
                val kind = classifyError(error)
                Log.w(TAG, "Model $modelName attempt ${attempt + 1} failed ($kind): ${error.message}")

                when (kind) {
                    ErrorKind.BUSY -> continue
                    ErrorKind.FATAL -> throw Exception(sanitizeError(error), error)
                    ErrorKind.RATE_LIMITED, ErrorKind.MODEL_UNAVAILABLE, ErrorKind.OTHER -> break
                }
            }
        }

        throw Exception(sanitizeError(lastException), lastException)
    }

    fun classifyError(e: Throwable): ErrorKind {
        // The SDK reports most failures as typed exceptions whose message is just Google's
        // text (e.g. "The model is overloaded."), so check the type names along the cause chain too.
        val chain = generateSequence(e) { it.cause }.take(5).toList()
        val types = chain.joinToString(" ") { it.javaClass.simpleName }
        val msg = chain.joinToString(" ") { it.message ?: "" }

        fun has(vararg needles: String) = needles.any { msg.contains(it, ignoreCase = true) }

        return when {
            types.contains("InvalidAPIKeyException") ||
            types.contains("PromptBlockedException") ||
            types.contains("UnsupportedUserLocationException") ||
            types.contains("ServiceDisabledException") ||
            has("App Check", "attestation") ||
            has("API key not valid", "API_KEY_INVALID", "PERMISSION_DENIED", "INVALID_ARGUMENT") -> ErrorKind.FATAL

            types.contains("QuotaExceededException") ||
            has("429", "RESOURCE_EXHAUSTED", "quota", "rate limit") -> ErrorKind.RATE_LIMITED

            has("404", "NOT_FOUND", "is not found", "no longer available") -> ErrorKind.MODEL_UNAVAILABLE

            types.contains("RequestTimeoutException") ||
            has("503", "UNAVAILABLE", "overloaded", "high demand", "try again later",
                "INTERNAL", "timed out", "timeout") -> ErrorKind.BUSY

            types.contains("ServerException") -> ErrorKind.BUSY

            else -> ErrorKind.OTHER
        }
    }

    fun sanitizeError(e: Throwable?): String {
        if (e == null) return "AI service is currently busy. Please try again shortly."
        val msg = e.localizedMessage ?: e.message ?: ""

        // Already a friendly message from runWithFailover: pass it through unchanged.
        if (msg in FRIENDLY_MESSAGES) return msg

        return when (classifyError(e)) {
            ErrorKind.BUSY -> {
                if (msg.contains("timed out", ignoreCase = true) || msg.contains("timeout", ignoreCase = true)) {
                    "The AI took too long to respond. Please check your connection and try again."
                } else {
                    "AI servers are currently experiencing high demand. Please try again shortly."
                }
            }
            ErrorKind.RATE_LIMITED -> "Too many AI requests right now. Please wait a minute and try again."
            ErrorKind.FATAL -> {
                val chain = generateSequence(e) { it.cause }.take(5).toList()
                val isKeyProblem = chain.any {
                    it.javaClass.simpleName == "InvalidAPIKeyException" ||
                        (it.message ?: "").contains("API key", ignoreCase = true) ||
                        (it.message ?: "").contains("API_KEY", ignoreCase = true) ||
                        (it.message ?: "").contains("PERMISSION_DENIED", ignoreCase = true)
                }
                // A disabled Firebase AI service or a rejected App Check token: the user can't fix
                // either by rephrasing or retrying.
                val isServiceProblem = chain.any {
                    it.javaClass.simpleName == "ServiceDisabledException" ||
                        (it.message ?: "").contains("App Check", ignoreCase = true) ||
                        (it.message ?: "").contains("attestation", ignoreCase = true)
                }
                when {
                    isServiceProblem -> AI_UNAVAILABLE_MESSAGE
                    isKeyProblem -> INVALID_KEY_MESSAGE
                    else -> "The AI couldn't process this request. Please rephrase and try again."
                }
            }
            else -> when {
                msg.contains("network", ignoreCase = true) ||
                msg.contains("connect", ignoreCase = true) ||
                msg.contains("host", ignoreCase = true) -> "Connection problem. Please check your internet connection."
                else -> "AI service is temporarily busy. Please try again."
            }
        }
    }

    private const val INVALID_KEY_MESSAGE = "Your own Gemini key in Settings isn't working. Remove it there to use Superfit's built-in AI."
    private const val AI_UNAVAILABLE_MESSAGE = "The AI assistant is unavailable right now. Please try again later."

    private val FRIENDLY_MESSAGES = setOf(
        AI_UNAVAILABLE_MESSAGE,
        "AI servers are currently experiencing high demand. Please try again shortly.",
        "The AI took too long to respond. Please check your connection and try again.",
        "Too many AI requests right now. Please wait a minute and try again.",
        INVALID_KEY_MESSAGE,
        "The AI couldn't process this request. Please rephrase and try again.",
        "Connection problem. Please check your internet connection.",
        "AI service is temporarily busy. Please try again.",
        "AI service is currently busy. Please try again shortly."
    )
}
