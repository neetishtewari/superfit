package com.superfit.app.domain

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiTest {

    /** Runs a suspend call that never actually suspends (the fake AI answers at once). */
    private fun <T> now(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
        return result!!.getOrThrow()
    }

    @Test
    fun sharedCodeAsksThroughWhateverClientTheAppPlugsIn() {
        var asked: Triple<String, String?, Boolean>? = null
        Ai.text = AiText { prompt, system, isJson ->
            asked = Triple(prompt, system, isJson)
            """```json
            {"workoutText": "20 push-ups", "caloriesBurned": 12.5, "workoutType": "Strength", "setsCount": 2, "repsCount": 20}
            ```"""
        }
        val result = now { WorkoutParser().parseWorkoutInput("20 pushups") }
        assertEquals("20 push-ups", result.workoutText)
        assertEquals(12.5, result.caloriesBurned)
        assertEquals("Strength", result.workoutType)
        assertEquals(20, result.repsCount)
        assertEquals("Workout input: \"20 pushups\"", asked!!.first)
        assertTrue(asked!!.second!!.contains("exercise physiology"))
        assertTrue(asked!!.third)
    }
}
