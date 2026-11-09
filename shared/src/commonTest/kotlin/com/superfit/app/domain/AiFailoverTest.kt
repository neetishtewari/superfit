package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class AiFailoverTest {

    // Stand-ins named like the SDK's exception types, which classifyError matches by name.
    private class ServerException(message: String) : Exception(message)
    private class QuotaExceededException(message: String) : Exception(message)
    private class InvalidAPIKeyException(message: String) : Exception(message)
    private class ServiceDisabledException(message: String) : Exception(message)

    private val models = listOf("model-a", "model-b")

    @Test
    fun overloadedModelIsRetriedAndSucceedsWithoutSwitching() = runTest {
        val calls = mutableListOf<String>()
        val result = AiFailover.run(models) { model ->
            calls += model
            if (calls.size < 3) throw ServerException("The model is overloaded. Please try again later.")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("model-a", "model-a", "model-a"), calls)
    }

    @Test
    fun rateLimitMovesToTheNextModel() = runTest {
        val calls = mutableListOf<String>()
        val result = AiFailover.run(models) { model ->
            calls += model
            if (model == "model-a") throw QuotaExceededException("Resource has been exhausted")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("model-a", "model-b"), calls)
    }

    @Test
    fun usedUpCreditStopsAtOnce() = runTest {
        var callCount = 0
        try {
            AiFailover.run(models) {
                callCount++
                throw QuotaExceededException("429 RESOURCE_EXHAUSTED: Your prepayment credits are depleted.")
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("The AI assistant is unavailable right now. Please try again later.", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun refusedKeyStopsImmediatelyWithAFriendlyMessage() = runTest {
        var callCount = 0
        try {
            AiFailover.run(models) {
                callCount++
                throw InvalidAPIKeyException("API key not valid. Please pass a valid API key.")
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("The AI assistant is unavailable right now. Please try again later.", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun slowCallTimesOutAndFallsThroughToABusyMessage() = runTest {
        var callCount = 0
        try {
            AiFailover.run(models, timeoutMs = 1_000L, totalBudgetMs = 10_000L) {
                callCount++
                delay(5_000L)
                "too late"
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("The AI took too long to respond. Please check your connection and try again.", e.message)
        }
        assertTrue(callCount in 2..6, "budget should cap attempts, got $callCount")
    }

    @Test
    fun classifiesTypicalGeminiErrors() {
        assertEquals(AiFailover.ErrorKind.BUSY, AiFailover.classifyError(ServerException("This model is currently experiencing high demand.")))
        assertEquals(AiFailover.ErrorKind.RATE_LIMITED, AiFailover.classifyError(QuotaExceededException("Quota exceeded")))
        assertEquals(AiFailover.ErrorKind.MODEL_UNAVAILABLE, AiFailover.classifyError(Exception("models/x is not found for API version v1beta")))
        assertEquals(AiFailover.ErrorKind.FATAL, AiFailover.classifyError(InvalidAPIKeyException("bad key")))
    }

    @Test
    fun firebaseAiSwitchedOffStopsAtOnce() = runTest {
        var callCount = 0
        try {
            AiFailover.run(models) {
                callCount++
                throw ServiceDisabledException("Firebase AI Logic API has not been used in project superfit before or it is disabled.")
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("The AI assistant is unavailable right now. Please try again later.", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun rejectedAppCheckTokenStopsAtOnce() = runTest {
        var callCount = 0
        try {
            AiFailover.run(models) {
                callCount++
                throw ServerException("Firebase App Check token is invalid.")
            }
            fail("expected an exception")
        } catch (e: Exception) {
            assertEquals("The AI assistant is unavailable right now. Please try again later.", e.message)
        }
        assertEquals(1, callCount)
    }

    @Test
    fun photosAreShrunkToTheLongEdgeLimit() {
        assertEquals(1024 to 768, AiFailover.scaledSize(4000, 3000))
        assertEquals(768 to 1024, AiFailover.scaledSize(3000, 4000))
        assertEquals(800 to 600, AiFailover.scaledSize(800, 600))
    }
}
