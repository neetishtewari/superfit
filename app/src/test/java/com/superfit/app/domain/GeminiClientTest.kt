package com.superfit.app.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so android.util.Log calls inside GeminiClient work.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GeminiClientTest {

    // Stand-ins named like the SDK's exception types, which classifyError matches by name.
    private class ServerException(message: String) : Exception(message)
    private class QuotaExceededException(message: String) : Exception(message)
    private class InvalidAPIKeyException(message: String) : Exception(message)

    private val models = listOf("model-a", "model-b")

    @Test
    fun `overloaded model is retried and succeeds without switching`() = runTest {
        val calls = mutableListOf<String>()
        val result = GeminiClient.runWithFailover(models) { model ->
            calls += model
            if (calls.size < 3) throw ServerException("The model is overloaded. Please try again later.")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("model-a", "model-a", "model-a"), calls)
    }

    @Test
    fun `rate limit moves to the next model`() = runTest {
        val calls = mutableListOf<String>()
        val result = GeminiClient.runWithFailover(models) { model ->
            calls += model
            if (model == "model-a") throw QuotaExceededException("Resource has been exhausted")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("model-a", "model-b"), calls)
    }

    @Test
    fun `invalid key stops immediately with a friendly message`() = runTest {
        var callCount = 0
        try {
            GeminiClient.runWithFailover(models) {
                callCount++
                throw InvalidAPIKeyException("API key not valid. Please pass a valid API key.")
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("Invalid API Key. Please update your API key in Settings.", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `slow call times out and falls through to a busy message`() = runTest {
        var callCount = 0
        try {
            GeminiClient.runWithFailover(models, timeoutMs = 1_000L, totalBudgetMs = 10_000L) {
                callCount++
                delay(5_000L)
                "too late"
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("The AI took too long to respond. Please check your connection and try again.", e.message)
        }
        assertTrue("budget should cap attempts, got $callCount", callCount in 2..6)
    }

    @Test
    fun `classifies typical Gemini errors`() {
        assertEquals(GeminiClient.ErrorKind.BUSY, GeminiClient.classifyError(ServerException("This model is currently experiencing high demand.")))
        assertEquals(GeminiClient.ErrorKind.RATE_LIMITED, GeminiClient.classifyError(QuotaExceededException("Quota exceeded")))
        assertEquals(GeminiClient.ErrorKind.MODEL_UNAVAILABLE, GeminiClient.classifyError(Exception("models/x is not found for API version v1beta")))
        assertEquals(GeminiClient.ErrorKind.FATAL, GeminiClient.classifyError(InvalidAPIKeyException("bad key")))
    }
}
