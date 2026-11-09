package com.superfit.app.ui.auth

/**
 * Plain sentences for sign-in problems. Firebase and Google hand back developer text
 * ("The supplied auth credential is incorrect…", "12501: "), which should never reach people.
 */
object AuthMessages {
    const val EMPTY_FIELDS = "Enter your email and password."
    const val PASSWORDS_DIFFER = "The two passwords don't match. Try typing them again."
    const val NEED_EMAIL = "Enter your email first."
    const val NOT_SET_UP = "Sign-in isn't working right now. Please try again in a few minutes."
    const val GOOGLE_CANCELLED = "Google sign-in didn't finish. Try again, or use your email."
    const val NO_INTERNET = "No internet. Check your connection and try again."
    const val APPLE_CANCELLED = "Apple sign-in didn't finish. Try again, or use your email."
    const val ACCOUNT_EXISTS = "That email already has a Superfit account. Sign in the way you did before (Google, Apple or email)."

    // Google Play services status codes (GoogleSignInStatusCodes / CommonStatusCodes).
    private const val GOOGLE_CANCELLED_CODE = 12501
    private const val GOOGLE_NETWORK_CODE = 7

    fun resetSent(email: String) = "Sent to $email. Look for an email from Superfit; it can take a minute, and sometimes lands in spam."

    /**
     * [errorCode] is FirebaseAuthException.errorCode when there is one, [message] the raw text,
     * [isNetwork] true for FirebaseNetworkException. [creatingAccount] picks the fallback wording.
     */
    fun forFirebase(errorCode: String?, message: String?, isNetwork: Boolean = false, creatingAccount: Boolean = false): String {
        val text = message.orEmpty()
        if (isNetwork || text.contains("network error", ignoreCase = true)) return NO_INTERNET
        if (text.contains("API key not valid", ignoreCase = true) || text.contains("API_KEY_INVALID", ignoreCase = true)) return NOT_SET_UP
        return when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email doesn't look right. Check it and try again."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                "Wrong email or password. Try again, or tap \"Forgot password?\""
            "ERROR_USER_NOT_FOUND" -> "No account with that email. Create one below?"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has an account. Sign in instead?"
            "ERROR_WEAK_PASSWORD" -> "Use a password with at least 6 characters."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many tries. Wait a few minutes, then try again."
            "ERROR_USER_DISABLED" -> "This account has been turned off. Email us if you think that's a mistake."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> ACCOUNT_EXISTS
            else -> if (creatingAccount) "Couldn't create your account. Please try again." else "Couldn't sign you in. Please try again."
        }
    }

    /** [statusCode] is ApiException.statusCode from Google sign-in. */
    fun forGoogle(statusCode: Int): String = when (statusCode) {
        GOOGLE_CANCELLED_CODE -> GOOGLE_CANCELLED
        GOOGLE_NETWORK_CODE -> NO_INTERNET
        else -> "Google sign-in didn't work. Try again, or use your email."
    }

    /**
     * The iPhone's sign-in problems. Swift hands over Firebase's error as the same names Android
     * uses (ERROR_WRONG_PASSWORD...), or its own GOOGLE_* / APPLE_* / NOT_SET_UP for the Google
     * and Apple sheets.
     */
    fun forIphone(code: String?, message: String?, isNetwork: Boolean, creatingAccount: Boolean): String = when (code) {
        "GOOGLE_CANCELLED" -> GOOGLE_CANCELLED
        "GOOGLE_FAILED" -> forGoogle(-1)
        "APPLE_CANCELLED" -> APPLE_CANCELLED
        "APPLE_FAILED" -> "Apple sign-in didn't work. Try again, or use your email."
        "NOT_SET_UP" -> NOT_SET_UP
        else -> forFirebase(code, message, isNetwork, creatingAccount)
    }
}
