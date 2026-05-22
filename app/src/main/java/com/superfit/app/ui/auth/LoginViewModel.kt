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
            errorMessage = "Email and Password cannot be empty."
            return
        }

        if (!isLoginMode && password != confirmPassword) {
            errorMessage = "Passwords do not match."
            return
        }

        isLoading = true

        if (isLoginMode) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        viewModelScope.launch {
                            try {
                                repository.firebaseSyncManager.syncAllDown()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            isLoading = false
                            onSuccess()
                        }
                    } else {
                        isLoading = false
                        val rawError = task.exception?.localizedMessage ?: "Login failed. Please check credentials."
                        errorMessage = if (rawError.contains("API key not valid", ignoreCase = true) || rawError.contains("API_KEY_INVALID", ignoreCase = true)) {
                            "Invalid API Key. Please replace the mock google-services.json in the app/ directory with your real Firebase config."
                        } else {
                            rawError
                        }
                    }
                }
        } else {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        viewModelScope.launch {
                            try {
                                // Upload default/empty profile or existing local db
                                repository.firebaseSyncManager.syncAllUp()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            isLoading = false
                            onSuccess()
                        }
                    } else {
                        isLoading = false
                        val rawError = task.exception?.localizedMessage ?: "Registration failed."
                        errorMessage = if (rawError.contains("API key not valid", ignoreCase = true) || rawError.contains("API_KEY_INVALID", ignoreCase = true)) {
                            "Invalid API Key. Please replace the mock google-services.json in the app/ directory with your real Firebase config."
                        } else {
                            rawError
                        }
                    }
                }
        }
    }

    fun setCustomErrorMessage(message: String?) {
        errorMessage = message
    }

    fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential, onSuccess: () -> Unit) {
        errorMessage = null
        isLoading = true
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    viewModelScope.launch {
                        try {
                            repository.firebaseSyncManager.syncAllDown()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        isLoading = false
                        onSuccess()
                    }
                } else {
                    isLoading = false
                    val rawError = task.exception?.localizedMessage ?: "Google sign in failed."
                    errorMessage = if (rawError.contains("API key not valid", ignoreCase = true) || rawError.contains("API_KEY_INVALID", ignoreCase = true)) {
                        "Invalid API Key. Please replace the mock google-services.json in the app/ directory with your real Firebase config."
                    } else {
                        rawError
                    }
                }
            }
    }
}
