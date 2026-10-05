package com.superfit.app.domain

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Executes generation with retries and an automatic multi-model failover cascade.
     */
    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstructionText: String? = null,
        isJson: Boolean = true
    ): String = runWithFailover { modelName ->
        val model = getOrCreateModel(modelName, apiKey, systemInstructionText, isJson)
        model.generateContent(prompt).text
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
            ErrorKind.RATE_LIMITED -> "You've hit your Gemini key's usage limit. Please wait a minute and try again."
            ErrorKind.FATAL -> {
                val chain = generateSequence(e) { it.cause }.take(5).toList()
                val isKeyProblem = chain.any {
                    it.javaClass.simpleName == "InvalidAPIKeyException" ||
                        (it.message ?: "").contains("API key", ignoreCase = true) ||
                        (it.message ?: "").contains("API_KEY", ignoreCase = true) ||
                        (it.message ?: "").contains("PERMISSION_DENIED", ignoreCase = true)
                }
                if (isKeyProblem) "Invalid API Key. Please update your API key in Settings."
                else "The AI couldn't process this request. Please rephrase and try again."
            }
            else -> when {
                msg.contains("network", ignoreCase = true) ||
                msg.contains("connect", ignoreCase = true) ||
                msg.contains("host", ignoreCase = true) -> "Connection problem. Please check your internet connection."
                else -> "AI service is temporarily busy. Please try again."
            }
        }
    }

    private val FRIENDLY_MESSAGES = setOf(
        "AI servers are currently experiencing high demand. Please try again shortly.",
        "The AI took too long to respond. Please check your connection and try again.",
        "You've hit your Gemini key's usage limit. Please wait a minute and try again.",
        "Invalid API Key. Please update your API key in Settings.",
        "The AI couldn't process this request. Please rephrase and try again.",
        "Connection problem. Please check your internet connection.",
        "AI service is temporarily busy. Please try again.",
        "AI service is currently busy. Please try again shortly."
    )
}
