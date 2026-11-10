package com.superfit.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.superfit.app.data.AuthClient
import com.superfit.app.data.AuthFailure
import com.superfit.app.domain.AppLog
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the sign-in screen shows and does, the same on Android and iPhone. Restoring the backup
 * after sign-in is the caller's job (onSuccess), so it runs once; a new account uploads what is
 * already on the phone ([afterAccountCreated]).
 */
class LoginModel(
    private val auth: AuthClient,
    private val scope: CoroutineScope,
    private val afterAccountCreated: suspend () -> Unit = {}
) {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set
    var isLoginMode by mutableStateOf(true)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onEmailChange(value: String) {
        email = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun onConfirmPasswordChange(value: String) {
        confirmPassword = value
    }

    fun toggleMode() {
        isLoginMode = !isLoginMode
        errorMessage = null
    }

    fun setCustomErrorMessage(message: String?) {
        errorMessage = message
    }

    fun authenticate(onSuccess: () -> Unit) {
        errorMessage = null
        if (email.isBlank() || password.isBlank()) {
            errorMessage = AuthMessages.EMPTY_FIELDS
            return
        }
        if (!isLoginMode && password != confirmPassword) {
            errorMessage = AuthMessages.PASSWORDS_DIFFER
            return
        }
        val address = email.trim()
        val creating = !isLoginMode
        run(onSuccess, creating) {
            if (creating) {
                auth.createAccount(address, password)
                scope.launch {
                    try {
                        afterAccountCreated()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w(TAG, "First backup after creating the account failed", e)
                    }
                }
            } else {
                auth.signIn(address, password)
            }
        }
    }

    /** Google or Apple sign-in: [signIn] shows the app's own sheet and signs in to Firebase. */
    fun signInWith(onSuccess: () -> Unit, signIn: suspend () -> Unit) {
        errorMessage = null
        run(onSuccess, creating = false, block = signIn)
    }

    fun resetPassword(emailAddress: String, onComplete: (Boolean, String?) -> Unit) {
        val address = emailAddress.trim()
        if (address.isEmpty()) {
            onComplete(false, AuthMessages.NEED_EMAIL)
            return
        }
        scope.launch {
            try {
                auth.sendPasswordReset(address)
                onComplete(true, AuthMessages.resetSent(address))
            } catch (e: AuthFailure) {
                onComplete(false, e.userMessage)
            }
        }
    }

    private fun run(onSuccess: () -> Unit, creating: Boolean, block: suspend () -> Unit) {
        isLoading = true
        scope.launch {
            try {
                block()
                isLoading = false
                onSuccess()
            } catch (e: AuthFailure) {
                isLoading = false
                errorMessage = e.userMessage
            } catch (e: CancellationException) {
                isLoading = false
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Sign-in failed", e)
                isLoading = false
                errorMessage = AuthMessages.forFirebase(null, null, creatingAccount = creating)
            }
        }
    }

    private companion object {
        const val TAG = "Login"
    }
}
