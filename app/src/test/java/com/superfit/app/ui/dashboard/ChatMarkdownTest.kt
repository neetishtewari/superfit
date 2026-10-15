package com.superfit.app.ui.dashboard

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkdownTest {

    private fun boldParts(text: String) = chatMarkdown(text).let { s ->
        s.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { s.text.substring(it.start, it.end) }
    }

    @Test
    fun boldMarkersBecomeBoldText() {
        val reply = "The four treat meals are: **fried snack, biscuits, tea, and kabab**. Log daily."
        assertEquals("The four treat meals are: fried snack, biscuits, tea, and kabab. Log daily.", chatMarkdown(reply).text)
        assertEquals(listOf("fried snack, biscuits, tea, and kabab"), boldParts(reply))
    }

    @Test
    fun bulletsAndHeadingsLoseTheirSymbols() {
        val reply = "## Your week\n* **Protein** was low\n- Sleep was fine"
        assertEquals("Your week\n• Protein was low\n• Sleep was fine", chatMarkdown(reply).text)
        assertEquals(listOf("Your week", "Protein"), boldParts(reply))
    }

    @Test
    fun loneMarkerIsDropped() {
        assertEquals("Eat more veg", chatMarkdown("Eat more** veg").text)
    }
}
