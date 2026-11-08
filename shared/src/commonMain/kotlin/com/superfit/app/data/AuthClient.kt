package com.superfit.app.data

/** A sign-in that didn't work, already worded for people (see AuthMessages). */
class AuthFailure(val userMessage: String) : Exception(userMessage)

/**
 * Email sign-in on Firebase Auth, the same accounts on both apps. Each app plugs in its own
 * Firebase SDK; Google and Apple sign-in go through each app's own sheet, so they are not here.
 * Every function throws [AuthFailure] when it doesn't work.
 */
interface AuthClient {
    suspend fun signIn(email: String, password: String)
    suspend fun createAccount(email: String, password: String)
    suspend fun sendPasswordReset(email: String)
}
