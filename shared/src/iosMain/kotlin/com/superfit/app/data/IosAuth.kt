package com.superfit.app.data

import com.superfit.app.ui.auth.AuthMessages
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A sign-in problem as the iPhone's Swift code reports it: Firebase's error under the same names
 * Android uses (ERROR_WRONG_PASSWORD...), or GOOGLE_CANCELLED / APPLE_CANCELLED and the like.
 */
class IosAuthError(val code: String?, val message: String?, val isNetwork: Boolean)

/**
 * Firebase Auth on the iPhone, provided by Swift (Firebase's iPhone SDK, Google's sign-in sheet,
 * Sign in with Apple). Each call reports back through [done]: null when it worked.
 */
interface IosAuth {
    fun currentUid(): String?
    fun currentEmail(): String?

    /** The account's name (from Google or Apple sign-in), for the top of Share my report. */
    fun currentName(): String?
    fun signIn(email: String, password: String, done: (IosAuthError?) -> Unit)
    fun createAccount(email: String, password: String, done: (IosAuthError?) -> Unit)
    fun sendPasswordReset(email: String, done: (IosAuthError?) -> Unit)
    fun signInWithGoogle(done: (IosAuthError?) -> Unit)
    fun signInWithApple(done: (IosAuthError?) -> Unit)
    fun signOut()
}

/** Lets shared code use the Swift [IosAuth] as an ordinary [AuthClient], plus Google and Apple. */
class IosAuthClient(private val ios: IosAuth) : AuthClient {
    override suspend fun signIn(email: String, password: String) = call(false) { ios.signIn(email, password, it) }

    override suspend fun createAccount(email: String, password: String) = call(true) { ios.createAccount(email, password, it) }

    override suspend fun sendPasswordReset(email: String) = call(false) { ios.sendPasswordReset(email, it) }

    suspend fun signInWithGoogle() = call(false) { ios.signInWithGoogle(it) }

    suspend fun signInWithApple() = call(false) { ios.signInWithApple(it) }

    private suspend fun call(creatingAccount: Boolean, start: ((IosAuthError?) -> Unit) -> Unit) =
        suspendCancellableCoroutine<Unit> { c ->
            start { error ->
                if (error == null) {
                    c.resume(Unit)
                } else {
                    val text = AuthMessages.forIphone(error.code, error.message, error.isNetwork, creatingAccount)
                    c.resumeWithException(AuthFailure(text))
                }
            }
        }
}
