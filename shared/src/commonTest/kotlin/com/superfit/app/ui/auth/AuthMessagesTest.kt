package com.superfit.app.ui.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    @Test
    fun appleCancelAndAccountClashGetPlainSentences() {
        assertEquals(AuthMessages.APPLE_CANCELLED, AuthMessages.forIphone("APPLE_CANCELLED", null, isNetwork = false, creatingAccount = false))
        assertEquals(AuthMessages.GOOGLE_CANCELLED, AuthMessages.forIphone("GOOGLE_CANCELLED", "raw", isNetwork = false, creatingAccount = false))
        assertEquals(AuthMessages.NO_INTERNET, AuthMessages.forIphone(null, "raw", isNetwork = true, creatingAccount = false))
        assertFalse(AuthMessages.forIphone("APPLE_FAILED", "ASAuthorizationError 1000", isNetwork = false, creatingAccount = false).contains("1000"))
        assertEquals(
            AuthMessages.ACCOUNT_EXISTS,
            AuthMessages.forFirebase("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "x")
        )
    }
}
