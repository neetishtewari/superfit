package com.superfit.app.domain

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay

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

    // Per-model timeout (5s) to guarantee fast failover if a model queues or hangs
    private const val PER_MODEL_TIMEOUT_MS = 5000L

    // Cache GenerativeModel instances to maintain warm HTTP/2 & TLS connection pools
    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun getOrCreateModel(
        modelName: String,
        apiKey: String,
        systemInstructionText: String?,
        isJson: Boolean
    ): GenerativeModel {
        val cacheKey = "$modelName:${systemInstructionText?.hashCode() ?: 0}:$isJson"
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
     * Executes generation with an automatic multi-model failover cascade.
     * Prioritizes sub-second latency with warm connection pooling and rapid failover.
     */
    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstructionText: String? = null,
        isJson: Boolean = true
    ): String {
        var lastException: Exception? = null

        for (modelName in MODEL_CASCADE) {
            try {
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "Attempting generation with model: $modelName")

                val model = getOrCreateModel(modelName, apiKey, systemInstructionText, isJson)
                val response = kotlinx.coroutines.withTimeout(PER_MODEL_TIMEOUT_MS) {
                    model.generateContent(prompt)
                }
                val text = response.text

                val elapsed = System.currentTimeMillis() - startTime
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "Successfully generated content using $modelName in ${elapsed}ms")
                    return text
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastException = Exception("Model $modelName timed out after ${PER_MODEL_TIMEOUT_MS}ms")
                Log.w(TAG, "Model $modelName timed out after ${PER_MODEL_TIMEOUT_MS}ms. Failing over immediately to next model in cascade...")
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Model $modelName failed: ${e.message}. Failing over immediately to next model in cascade...")
            }
        }

        // If all models in the cascade failed, throw a sanitized exception
        val sanitizedMessage = sanitizeError(lastException)
        throw Exception(sanitizedMessage, lastException)
    }

    private fun isRecoverableError(e: Exception): Boolean {
        val msg = e.message ?: ""
        // Non-recoverable on the same model - failover immediately to next model
        if (msg.contains("404", ignoreCase = true) ||
            msg.contains("NOT_FOUND", ignoreCase = true) ||
            msg.contains("no longer available", ignoreCase = true)) {
            return false
        }
        return msg.contains("503", ignoreCase = true) ||
                msg.contains("UNAVAILABLE", ignoreCase = true) ||
                msg.contains("high demand", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true)
    }

    fun sanitizeError(e: Throwable?): String {
        if (e == null) return "AI service is currently busy. Please try again shortly."
        val msg = e.localizedMessage ?: e.message ?: ""

        return when {
            msg.contains("503", ignoreCase = true) ||
            msg.contains("UNAVAILABLE", ignoreCase = true) ||
            msg.contains("high demand", ignoreCase = true) -> {
                "AI servers are currently experiencing high demand. Please try again shortly."
            }
            msg.contains("429", ignoreCase = true) ||
            msg.contains("quota", ignoreCase = true) ||
            msg.contains("exhausted", ignoreCase = true) -> {
                "Quota exceeded. Please check your Gemini API key in Settings."
            }
            msg.contains("API key", ignoreCase = true) ||
            msg.contains("invalid", ignoreCase = true) ||
            msg.contains("400", ignoreCase = true) -> {
                "Invalid API Key. Please update your API key in Settings."
            }
            msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("connect", ignoreCase = true) -> {
                "Connection timeout. Please check your internet connection."
            }
            else -> {
                "AI service is temporarily busy. Please try again."
            }
        }
    }
}
