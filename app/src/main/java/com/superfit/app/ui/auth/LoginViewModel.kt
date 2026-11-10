package com.superfit.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.DataRepository

/** Keeps the shared [LoginModel] alive across rotation, on Firebase's Android SDK. */
class LoginViewModel(repository: DataRepository) : ViewModel() {
    private val client = FirebaseAuthClient()

    // A new account uploads what is already on the phone. Restoring after sign-in is done once,
    // by the screen that opens next (Navigation).
    val model = LoginModel(client, viewModelScope) { repository.firebaseSyncManager.syncAllUp() }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) = model.signInWith(onSuccess) { client.signInWithGoogle(idToken) }
}
