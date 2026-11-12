package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class OpenFoodFactsTest {
    private val oats = """{"status":1,"product":{"product_name":"Rolled oats","nutriments":{"energy-kcal_100g":380,"proteins_100g":13}}}"""

    @Test
    fun upcA_triesTheEanFormWhenTheFirstIsUnknown() = runTest {
        val asked = mutableListOf<String>()
        val food = OpenFoodFacts.lookup("012345678905") { url ->
            asked += url
            if (url.contains("/0012345678905.json")) HttpReply(200, oats) else HttpReply(404, "")
        }
        assertEquals("Rolled oats", food?.name)
        assertEquals(380.0, food?.per100g?.kcal)
        assertEquals(2, asked.size)
    }

    @Test
    fun notABarcode_sendsNothing() = runTest {
        var asked = 0
        assertNull(
            OpenFoodFacts.lookup("12ab") {
                asked++
                HttpReply(200, oats)
            }
        )
        assertEquals(0, asked)
    }

    @Test
    fun unknownProduct_isNull_butAServerErrorThrows() = runTest {
        assertNull(OpenFoodFacts.lookup("40000000") { HttpReply(404, "") })
        assertFailsWith<OpenFoodFactsException> { OpenFoodFacts.lookup("40000000") { HttpReply(503, "") } }
    }
}
