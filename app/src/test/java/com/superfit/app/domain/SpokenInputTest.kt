package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SpokenInputTest {

    @Test
    fun `best guess first, other versions after`() {
        val heard = SpokenInput.fromGuesses(listOf("jaipuri chane", "cheh poori aur chane", "che puri aur chane"))
        assertEquals("jaipuri chane", heard?.text)
        assertEquals(listOf("cheh poori aur chane", "che puri aur chane"), heard?.otherVersions)
    }

    @Test
    fun `stretches after a pause join into whole sentences`() {
        val heard = SpokenInput.fromStretches(
            listOf(
                listOf("two roti", "do roti"),
                listOf("and dal")
            )
        )
        assertEquals("two roti and dal", heard?.text)
        assertEquals(listOf("do roti and dal"), heard?.otherVersions)
    }

    @Test
    fun `repeats, blanks and extra guesses are dropped`() {
        val heard = SpokenInput.fromGuesses(listOf("pão de queijo", "Pão de queijo", " ", "pao de queijo", "a", "b", "c"))
        assertEquals("pão de queijo", heard?.text)
        assertEquals(listOf("pao de queijo", "a", "b"), heard?.otherVersions)
    }

    @Test
    fun `nothing heard is null`() {
        assertNull(SpokenInput.fromGuesses(null))
        assertNull(SpokenInput.fromGuesses(listOf(" ")))
        assertNull(SpokenInput.fromStretches(emptyList()))
    }

    @Test
    fun `English phone in India listens as Indian English, other phone languages offered`() {
        val choice = SpeechLanguages.choose(listOf(Locale.US, Locale.forLanguageTag("hi-IN")), "in")
        assertEquals("en-IN", choice?.primary)
        assertEquals(listOf("hi-IN"), choice?.others)
    }

    @Test
    fun `Portuguese phones keep their own language and region`() {
        assertEquals("pt-BR", SpeechLanguages.choose(listOf(Locale.forLanguageTag("pt-BR")), "br")?.primary)
        assertEquals("pt-PT", SpeechLanguages.choose(listOf(Locale.forLanguageTag("pt-PT")), "pt")?.primary)
        // A Brazilian travelling still listens in Brazilian Portuguese.
        assertEquals("pt-BR", SpeechLanguages.choose(listOf(Locale.forLanguageTag("pt-BR")), "us")?.primary)
    }

    @Test
    fun `no SIM keeps the phone's language as set`() {
        val choice = SpeechLanguages.choose(listOf(Locale.UK, Locale.UK), null)
        assertEquals("en-GB", choice?.primary)
        assertEquals(emptyList<String>(), choice?.others)
        assertEquals("en-US", SpeechLanguages.choose(listOf(Locale.US), "")?.primary)
    }

    @Test
    fun `no phone language means no hint`() {
        assertNull(SpeechLanguages.choose(emptyList(), "in"))
    }
}
