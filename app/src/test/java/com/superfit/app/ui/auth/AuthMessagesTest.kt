package com.superfit.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthMessagesTest {

    @Test
    fun developerTextNeverReachesPeople() {
        val msg = AuthMessages.forFirebase(null, "An internal error has occurred. [ API key not valid. Please pass a valid API key. ]")
        assertEquals(AuthMessages.NOT_SET_UP, msg)
        assertFalse(msg.contains("google-services"))
    }

    @Test
    fun commonFirebaseCodesGetPlainSentences() {
        assertEquals(
            "Wrong email or password. Try again, or tap \"Forgot password?\"",
            AuthMessages.forFirebase("ERROR_INVALID_CREDENTIAL", "The supplied auth credential is incorrect, malformed or has expired.")
        )
        assertEquals("That email already has an account. Sign in instead?", AuthMessages.forFirebase("ERROR_EMAIL_ALREADY_IN_USE", "x"))
        assertEquals("Use a password with at least 6 characters.", AuthMessages.forFirebase("ERROR_WEAK_PASSWORD", "x"))
    }

    @Test
    fun networkProblemsSayNoInternet() {
        assertEquals(AuthMessages.NO_INTERNET, AuthMessages.forFirebase(null, "x", isNetwork = true))
        assertEquals(AuthMessages.NO_INTERNET, AuthMessages.forFirebase(null, "A network error (such as timeout, interrupted connection) has occurred."))
    }

    @Test
    fun unknownErrorsFallBackToAFriendlyLine() {
        assertEquals("Couldn't sign you in. Please try again.", AuthMessages.forFirebase("ERROR_SOMETHING_NEW", "raw"))
        assertEquals("Couldn't create your account. Please try again.", AuthMessages.forFirebase(null, null, creatingAccount = true))
    }

    @Test
    fun googleCancelIsNotShownAsACode() {
        assertEquals(AuthMessages.GOOGLE_CANCELLED, AuthMessages.forGoogle(12501))
        assertEquals(AuthMessages.NO_INTERNET, AuthMessages.forGoogle(7))
        assertFalse(AuthMessages.forGoogle(10).contains("10"))
    }
}
