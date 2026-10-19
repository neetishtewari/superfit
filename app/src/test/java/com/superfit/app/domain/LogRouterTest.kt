package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric so org.json works.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LogRouterTest {

    @Test
    fun `food only`() {
        assertEquals(RoutedLog("2 roti and dal", ""), LogRouter.parse("""{"food":"2 roti and dal","exercise":""}"""))
    }

    @Test
    fun `exercise only`() {
        assertEquals(RoutedLog("", "ran 5k"), LogRouter.parse("""{"food":"","exercise":"ran 5k"}"""))
    }

    @Test
    fun `both parts`() {
        val routed = LogRouter.parse("""{"food":" had poha ","exercise":"walked 30 minutes"}""")
        assertEquals("had poha", routed.food)
        assertEquals("walked 30 minutes", routed.exercise)
    }

    @Test
    fun `blank, missing or broken means nothing to log`() {
        assertTrue(LogRouter.parse("""{"food":"  ","exercise":""}""").isEmpty)
        assertTrue(LogRouter.parse("""{"food":null}""").isEmpty)
        assertTrue(LogRouter.parse("not json").isEmpty)
    }

    @Test
    fun `code fences are ignored`() {
        assertEquals(RoutedLog("chai", ""), LogRouter.parse("```json\n{\"food\":\"chai\",\"exercise\":\"\"}\n```"))
    }
}
