package com.superfit.app.domain

import java.net.URLDecoder
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLinkTest {

    @Test
    fun newCodesAreValidAndAvoidLookalikes() {
        val random = Random(42)
        repeat(200) {
            val code = InviteLink.newCode(random)
            assertTrue(code, InviteLink.isValidCode(code))
            assertFalse(code, code.any { it in "01OIL" })
        }
    }

    @Test
    fun linkCarriesTheCodeThroughPlayReferrer() {
        val url = InviteLink.url("ABCD2345")
        assertTrue(url.startsWith(InviteLink.PLAY_URL + "&referrer="))
        val referrer = URLDecoder.decode(url.substringAfter("&referrer="), "UTF-8")
        assertEquals("ABCD2345", InviteLink.codeFromReferrer(referrer))
    }

    @Test
    fun noCodeGivesThePlainLink() {
        assertEquals(InviteLink.PLAY_URL, InviteLink.url(null))
        assertEquals(InviteLink.PLAY_URL, InviteLink.url("bad"))
    }

    @Test
    fun organicAndOtherCampaignInstallsHaveNoCode() {
        assertNull(InviteLink.codeFromReferrer(null))
        assertNull(InviteLink.codeFromReferrer(""))
        assertNull(InviteLink.codeFromReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(InviteLink.codeFromReferrer("utm_source=other&utm_content=ABCD2345"))
        assertNull(InviteLink.codeFromReferrer("utm_source=superfit_invite&utm_content=not-a-code"))
        assertNull(InviteLink.codeFromReferrer("garbage"))
    }

    @Test
    fun parameterOrderDoesNotMatter() {
        assertEquals("ABCD2345", InviteLink.codeFromReferrer("utm_content=ABCD2345&utm_source=superfit_invite"))
    }

    @Test
    fun messagesContainThePersonalLink() {
        assertTrue(InviteLink.inviteMessage("ABCD2345").endsWith(InviteLink.url("ABCD2345")))
        assertTrue(InviteLink.shareCardText("ABCD2345").startsWith("Tracked with Superfit https://"))
    }
}
