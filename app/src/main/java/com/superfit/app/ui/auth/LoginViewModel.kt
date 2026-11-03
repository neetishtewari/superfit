package com.superfit.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.superfit.app.data.DataRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: DataRepository) : ViewModel() {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

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

        isLoading = true

        if (isLoginMode) {
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        viewModelScope.launch {
                            viewModelScope.launch {
                                try {
                                    repository.firebaseSyncManager.syncAllDown()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            isLoading = false
                            onSuccess()
                        }
                    } else {
                        isLoading = false
                        errorMessage = friendly(task.exception, creatingAccount = false)
                    }
                }
        } else {
            auth.createUserWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        viewModelScope.launch {
                            viewModelScope.launch {
                                try {
                                    // Upload default/empty profile or existing local db
                                    repository.firebaseSyncManager.syncAllUp()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            isLoading = false
                            onSuccess()
                        }
                    } else {
                        isLoading = false
                        errorMessage = friendly(task.exception, creatingAccount = true)
                    }
                }
        }
    }

    fun setCustomErrorMessage(message: String?) {
        errorMessage = message
    }

    fun resetPassword(emailAddress: String, onComplete: (Boolean, String?) -> Unit) {
        if (emailAddress.isBlank()) {
            onComplete(false, AuthMessages.NEED_EMAIL)
            return
        }
        auth.sendPasswordResetEmail(emailAddress.trim())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, AuthMessages.resetSent(emailAddress.trim()))
                } else {
                    onComplete(false, friendly(task.exception, creatingAccount = false))
                }
            }
    }

    fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential, onSuccess: () -> Unit) {
        errorMessage = null
        isLoading = true
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    viewModelScope.launch {
                        viewModelScope.launch {
                            try {
                                repository.firebaseSyncManager.syncAllDown()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        isLoading = false
                        onSuccess()
                    }
                } else {
                    isLoading = false
                    errorMessage = friendly(task.exception, creatingAccount = false)
                }
            }
    }

    private fun friendly(e: Exception?, creatingAccount: Boolean): String = AuthMessages.forFirebase(
        errorCode = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode,
        message = e?.message,
        isNetwork = e is com.google.firebase.FirebaseNetworkException,
        creatingAccount = creatingAccount
    )
}
