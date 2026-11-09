package com.superfit.app.ui.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.superfit.app.data.AuthClient
import com.superfit.app.data.AuthFailure
import com.superfit.app.data.await
import kotlin.coroutines.cancellation.CancellationException

/** The shared sign-in on Firebase's Android SDK, plus Google sign-in from Google's own sheet. */
class FirebaseAuthClient : AuthClient {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    override suspend fun signIn(email: String, password: String) = call(creatingAccount = false) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun createAccount(email: String, password: String) = call(creatingAccount = true) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    override suspend fun sendPasswordReset(email: String) = call(creatingAccount = false) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun signInWithGoogle(idToken: String) = call(creatingAccount = false) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    private suspend fun call(creatingAccount: Boolean, block: suspend () -> Any?) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthFailure(
                AuthMessages.forFirebase(
                    errorCode = (e as? FirebaseAuthException)?.errorCode,
                    message = e.message,
                    isNetwork = e is FirebaseNetworkException,
                    creatingAccount = creatingAccount
                )
            )
        }
    }
}
