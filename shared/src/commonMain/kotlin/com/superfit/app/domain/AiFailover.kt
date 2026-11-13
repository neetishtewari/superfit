package com.superfit.app.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How both apps call Gemini through Firebase AI Logic: which models, in which order, how often to
 * retry, and how failures are worded for people. Each app only makes the single call ([run]'s
 * `call`) with its own Firebase SDK, so Android and iPhone behave the same when Gemini is busy.
 */
object AiFailover {

    private const val TAG = "AiFailover"

    // Built-in failover cascade, prioritized for lowest latency. All four are current stable
    // models in Firebase AI Logic (gemini-2.0-flash shut down 2026-06-01 and gemini-2.5-flash is
    // deprecated, so both were replaced):
    // 1. gemini-3.1-flash-lite: ultra-low latency, no thinking overhead
    // 2. gemini-3.5-flash-lite: newer lite model, same speed class
    // 3. gemini-3.5-flash: frontier multimodal fallback
    // 4. gemini-3.8-flash: newest flash model, last resort
    val BUILT_IN_MODELS = listOf(
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.8-flash"
    )

    /** Firebase Remote Config parameter with the models to try first, e.g. "gemini-4-flash-lite, gemini-4-flash". */
    const val REMOTE_CONFIG_KEY = "ai_models"

    /** Most models taken from Remote Config, so a typo can't make every call walk a long list. */
    private const val MAX_REMOTE_MODELS = 6

    private val MODEL_NAME = Regex("^gemini-[a-z0-9.-]+$")

    @kotlin.concurrent.Volatile
    private var remoteModels: List<String> = emptyList()

    /**
     * The models each call tries, in order: the list from Firebase Remote Config (so a retired
     * model can be replaced without an app update on either store), then the built-in list as a
     * safety net if the remote one is empty, mistyped or all of it fails.
     */
    val MODEL_CASCADE: List<String> get() = (remoteModels + BUILT_IN_MODELS).distinct()

    /** Takes the Remote Config value; blank or unreadable leaves only the built-in list. */
    fun setRemoteModels(raw: String?) {
        remoteModels = parseModelList(raw)
        if (remoteModels.isNotEmpty()) AppLog.d(TAG, "Models from Remote Config: $remoteModels")
    }

    /** Model names from a comma- or space-separated list, keeping only names that look like Gemini models. */
    fun parseModelList(raw: String?): List<String> =
        raw.orEmpty()
            .split(',', ' ', '\n', '\t')
            .map { it.trim().lowercase() }
            .filter { MODEL_NAME.matches(it) }
            .distinct()
            .take(MAX_REMOTE_MODELS)

    // Per-attempt timeout. Long prompts (coaching with full history) can legitimately take
    // well over 5s, so a short limit turned slow answers into "busy" errors.
    const val PER_ATTEMPT_TIMEOUT_MS = 20_000L

    // Attempts per model when Gemini says it is overloaded (503) or the call times out.
    private const val ATTEMPTS_PER_MODEL = 3

    // Backoff before the 2nd and 3rd attempt on the same model, as Google recommends for 503s.
    private val RETRY_DELAYS_MS = listOf(1_000L, 2_000L)

    // Short pause before moving to the next model after a rate limit, so the cascade
    // doesn't burn through every model's per-minute quota in the same second.
    private const val MODEL_SWITCH_DELAY_MS = 1_000L

    // Overall budget across all models and retries, so a bad spell never leaves the user
    // staring at a spinner for minutes.
    const val TOTAL_BUDGET_MS = 45_000L

    enum class ErrorKind {
        /** Server overloaded / unavailable or timed out: wait and retry the same model. */
        BUSY,

        /** Rate limit or quota hit on this model: move on to the next model. */
        RATE_LIMITED,

        /** Model name not found or retired: move on to the next model. */
        MODEL_UNAVAILABLE,

        /** Bad key, bad request, blocked prompt: other models won't help, stop. */
        FATAL,

        /** The project's prepaid Gemini credit is used up: every model will refuse, stop. */
        OUT_OF_CREDIT,

        /** Anything else: try the next model once. */
        OTHER
    }

    /** Reported to crash reports when the Gemini credit runs out, named so it stands out. */
    class OutOfCreditException(cause: Throwable) : Exception("Gemini prepaid credit is used up", cause)

    /**
     * Runs [call] against each model in [models] until one returns non-blank text.
     *
     * Busy/timeout errors are retried on the same model with backoff; rate limits and
     * missing models move on to the next model; fatal errors (bad key, bad request)
     * stop immediately. Throws an exception carrying a user-friendly message if all fail.
     * [onOutOfCredit] lets the app report an empty Gemini balance.
     */
    suspend fun run(
        models: List<String> = MODEL_CASCADE,
        timeoutMs: Long = PER_ATTEMPT_TIMEOUT_MS,
        totalBudgetMs: Long = TOTAL_BUDGET_MS,
        onOutOfCredit: (Throwable) -> Unit = {},
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
                    AppLog.d(TAG, "Retrying $modelName in ${wait}ms (attempt ${attempt + 1}/$ATTEMPTS_PER_MODEL)")
                    delay(wait)
                    elapsedMs += wait
                }
                if (elapsedMs >= totalBudgetMs) break@cascade

                val start = TimeSource.Monotonic.markNow()
                val outcome = try {
                    // withTimeoutOrNull returns null on timeout, so wrap the result to tell
                    // "timed out" apart from "model returned null text".
                    withTimeoutOrNull(timeoutMs) { Result.success(call(modelName)) }
                        ?: Result.failure(Exception("Model $modelName timed out after ${timeoutMs}ms"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }

                val text = outcome.getOrNull()
                val tookMs = start.elapsedNow().inWholeMilliseconds
                elapsedMs += if (outcome.exceptionOrNull()?.message?.contains("timed out") == true) timeoutMs else tookMs
                if (!text.isNullOrBlank()) {
                    AppLog.d(TAG, "Generated content using $modelName in ${tookMs}ms")
                    return text
                }

                val error = outcome.exceptionOrNull() as? Exception
                    ?: Exception("Model $modelName returned an empty response")
                lastException = error
                val kind = classifyError(error)
                AppLog.w(TAG, "Model $modelName attempt ${attempt + 1} failed ($kind): ${error.message}")

                when (kind) {
                    ErrorKind.BUSY -> continue
                    ErrorKind.FATAL -> throw Exception(sanitizeError(error), error)
                    ErrorKind.OUT_OF_CREDIT -> {
                        onOutOfCredit(OutOfCreditException(error))
                        throw Exception(sanitizeError(error), error)
                    }
                    ErrorKind.RATE_LIMITED, ErrorKind.MODEL_UNAVAILABLE, ErrorKind.OTHER -> break
                }
            }
        }

        throw Exception(sanitizeError(lastException), lastException)
    }

    fun classifyError(e: Throwable): ErrorKind {
        // The SDKs report most failures as typed exceptions whose message is just Google's
        // text (e.g. "The model is overloaded."), so check the type names along the cause chain too.
        val chain = generateSequence(e) { it.cause }.take(5).toList()
        val types = chain.joinToString(" ") { it::class.simpleName.orEmpty() }
        val msg = chain.joinToString(" ") { it.message ?: "" }

        fun has(vararg needles: String) = needles.any { msg.contains(it, ignoreCase = true) }

        return when {
            // Google sends this as a 429, so it must be checked before the rate-limit case.
            has("prepayment credit", "credits are depleted", "credit balance") -> ErrorKind.OUT_OF_CREDIT

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
                has(
                    "503",
                    "UNAVAILABLE",
                    "overloaded",
                    "high demand",
                    "try again later",
                    "INTERNAL",
                    "timed out",
                    "timeout"
                ) -> ErrorKind.BUSY

            types.contains("ServerException") -> ErrorKind.BUSY

            else -> ErrorKind.OTHER
        }
    }

    fun sanitizeError(e: Throwable?): String {
        if (e == null) return "AI service is currently busy. Please try again shortly."
        val msg = e.message ?: ""

        // Already a friendly message from run: pass it through unchanged.
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
            ErrorKind.OUT_OF_CREDIT -> AI_UNAVAILABLE_MESSAGE
            ErrorKind.FATAL -> {
                val chain = generateSequence(e) { it.cause }.take(5).toList()
                // A disabled Firebase AI service, a rejected App Check token or a refused project
                // key: the user can't fix any of these by rephrasing or retrying.
                val isServiceProblem = chain.any {
                    val name = it::class.simpleName.orEmpty()
                    val text = it.message ?: ""
                    name == "InvalidAPIKeyException" ||
                        text.contains("API key", ignoreCase = true) ||
                        text.contains("API_KEY", ignoreCase = true) ||
                        text.contains("PERMISSION_DENIED", ignoreCase = true) ||
                        name == "ServiceDisabledException" ||
                        text.contains("App Check", ignoreCase = true) ||
                        text.contains("attestation", ignoreCase = true)
                }
                when {
                    isServiceProblem -> AI_UNAVAILABLE_MESSAGE
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

    /** Width and height that fit within [maxEdge] on the long side, keeping the shape. Never enlarges. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int = 1024): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (longEdge <= maxEdge) return width to height
        val factor = maxEdge.toDouble() / longEdge
        return maxOf(1, (width * factor).roundToInt()) to maxOf(1, (height * factor).roundToInt())
    }

    private const val AI_UNAVAILABLE_MESSAGE = "The AI assistant is unavailable right now. Please try again later."

    private val FRIENDLY_MESSAGES = setOf(
        AI_UNAVAILABLE_MESSAGE,
        "AI servers are currently experiencing high demand. Please try again shortly.",
        "The AI took too long to respond. Please check your connection and try again.",
        "Too many AI requests right now. Please wait a minute and try again.",
        "The AI couldn't process this request. Please rephrase and try again.",
        "Connection problem. Please check your internet connection.",
        "AI service is temporarily busy. Please try again.",
        "AI service is currently busy. Please try again shortly."
    )
}
